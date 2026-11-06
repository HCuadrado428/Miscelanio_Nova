package com.miscelanea.movil;

import com.miscelanea.Miscelanea;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Banco y Bizum (lado servidor). Cada jugador tiene un saldo de esmeraldas ({@link BancoDatos}): las ingresa desde su
 * inventario, las retira como objetos y se las manda a otros (Bizum, con concepto). También puede mandar un objeto de su
 * inventario a otro jugador conectado. El saldo nunca baja de 0, todo pasa por el servidor y queda en el registro
 * ({@code [Banco] ...}). Las confirmaciones ("¿Seguro?") las pide el cliente.
 * <p>
 * Operadores: {@code /miscelanea banco <jugador> ver|dar <n>|quitar <n>}.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Banco {
    private Banco() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<UUID, Long> ULT = new HashMap<>();

    static void recibir(ServerPlayer p, int tipo, FriendlyByteBuf b) {
        if (!MovilConfig.banco()) return;
        switch (tipo) {
            case MovilRed.C_BANCO -> estado(p);
            case MovilRed.C_BANCO_INGRESAR -> ingresar(p, b.readVarInt());
            case MovilRed.C_BANCO_RETIRAR -> retirar(p, b.readVarInt());
            case MovilRed.C_BIZUM -> bizum(p, b.readUUID(), b.readVarLong(), b.readUtf(256));
            case MovilRed.C_ENVIAR_OBJETO -> objeto(p, b.readUUID(), b.readVarInt(), b.readVarInt());
            default -> {
            }
        }
    }

    private static boolean rapido(ServerPlayer p) {
        long ahora = p.server.getTickCount();
        Long u = ULT.get(p.getUUID());
        if (u != null && ahora - u < 10) return true;
        ULT.put(p.getUUID(), ahora);
        return false;
    }

    static int esmeraldas(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    public static void estado(ServerPlayer p) {
        BancoDatos d = BancoDatos.de(p.server);
        List<BancoDatos.Movimiento> h = d.historial.getOrDefault(p.getUUID(), List.of());
        MovilRed.aJugador(p, MovilRed.S_BANCO, b -> {
            b.writeVarLong(d.saldo(p.getUUID()));
            b.writeVarInt(esmeraldas(p));
            b.writeVarInt(MovilConfig.bancoMaxTransferencia());
            b.writeVarInt(MovilConfig.bancoMaxConcepto());
            b.writeBoolean(MovilConfig.bancoObjetos());
            b.writeVarInt(h.size());
            for (BancoDatos.Movimiento m : h) {
                b.writeLong(m.t());
                b.writeByte(m.tipo());
                b.writeBoolean(m.otro() != null);
                if (m.otro() != null) b.writeUUID(m.otro());
                b.writeUtf(m.nombreOtro());
                b.writeLong(m.cantidad());
                b.writeUtf(m.texto());
            }
        });
    }

    private static void ingresar(ServerPlayer p, int n) {
        if (rapido(p)) return;
        n = Math.min(n, esmeraldas(p));
        if (n <= 0) return;
        int falta = n;
        for (ItemStack s : p.getInventory().items) {
            if (falta <= 0) break;
            if (!s.is(Items.EMERALD)) continue;
            int q = Math.min(falta, s.getCount());
            s.shrink(q);
            falta -= q;
        }
        int hecho = n - falta;
        BancoDatos d = BancoDatos.de(p.server);
        d.mover(p.getUUID(), hecho);
        d.anotar(p.getUUID(), new BancoDatos.Movimiento(System.currentTimeMillis(), BancoDatos.M_INGRESO, null, "", hecho, ""));
        p.getInventory().setChanged();
        LOG.info("[Banco] {} ingresa {} esmeraldas (saldo {})", MovilServidor.nombre(p), hecho, d.saldo(p.getUUID()));
        estado(p);
    }

    private static void retirar(ServerPlayer p, int n) {
        if (rapido(p)) return;
        BancoDatos d = BancoDatos.de(p.server);
        if (n <= 0 || n > 64 * 36 || !d.mover(p.getUUID(), -n)) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.banco_saldo", "");
            estado(p);
            return;
        }
        int falta = n;
        while (falta > 0) {
            int q = Math.min(64, falta);
            ItemStack s = new ItemStack(Items.EMERALD, q);
            if (!p.getInventory().add(s) || !s.isEmpty()) p.drop(s, false);
            falta -= q;
        }
        d.anotar(p.getUUID(), new BancoDatos.Movimiento(System.currentTimeMillis(), BancoDatos.M_RETIRADA, null, "", -n, ""));
        LOG.info("[Banco] {} retira {} esmeraldas (saldo {})", MovilServidor.nombre(p), n, d.saldo(p.getUUID()));
        estado(p);
    }

    private static void bizum(ServerPlayer p, UUID a, long n, String concepto) {
        if (rapido(p)) return;
        concepto = MovilServidor.limpiar(concepto, MovilConfig.bancoMaxConcepto());
        BancoDatos d = BancoDatos.de(p.server);
        MovilDatos.Perfil otro = MovilServidor.datos(p.server).perfiles.get(a);
        if (otro == null || a.equals(p.getUUID()) || n <= 0 || n > MovilConfig.bancoMaxTransferencia()) return;
        if (!d.mover(p.getUUID(), -n)) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.banco_saldo", "");
            estado(p);
            return;
        }
        d.mover(a, n);
        long t = System.currentTimeMillis();
        String yo = MovilServidor.nombre(p);
        d.anotar(p.getUUID(), new BancoDatos.Movimiento(t, BancoDatos.M_ENVIADO, a, otro.nombre, -n, concepto));
        d.anotar(a, new BancoDatos.Movimiento(t, BancoDatos.M_RECIBIDO, p.getUUID(), yo, n, concepto));
        LOG.info("[Banco] Bizum {} -> {}: {} esmeraldas ({})", yo, otro.nombre, n, concepto);
        MovilServidor.aviso(p, "movil.miscelanea.aviso.bizum_ok", n + " → " + otro.nombre);
        estado(p);
        ServerPlayer o = p.server.getPlayerList().getPlayer(a);
        if (o != null) {
            MovilServidor.noti(o, MovilRed.APP_BANCO, yo, MovilServidor.tr(concepto.isEmpty() ? "bizum_noti" : "bizum_noti_concepto", n, concepto), true);
            estado(o);
        }
    }

    private static void objeto(ServerPlayer p, UUID a, int slot, int cantidad) {
        if (rapido(p) || !MovilConfig.bancoObjetos()) return;
        ServerPlayer o = p.server.getPlayerList().getPlayer(a);
        if (o == null || o == p) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.objeto_desconectado", "");
            return;
        }
        if (slot < 0 || slot >= p.getInventory().items.size()) return;
        ItemStack s = p.getInventory().items.get(slot);
        if (s.isEmpty() || MovilRegistro.esMovil(s)) return;
        int q = Math.max(1, Math.min(cantidad, s.getCount()));
        ItemStack env = s.split(q);
        p.getInventory().setChanged();
        String desc = q + "x " + env.getHoverName().getString();
        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(env.getItem()));
        if (!o.getInventory().add(env) || !env.isEmpty()) o.drop(env, false);
        BancoDatos d = BancoDatos.de(p.server);
        long t = System.currentTimeMillis();
        d.anotar(p.getUUID(), new BancoDatos.Movimiento(t, BancoDatos.M_OBJETO_ENVIADO, a, MovilServidor.nombre(o), 0, desc));
        d.anotar(a, new BancoDatos.Movimiento(t, BancoDatos.M_OBJETO_RECIBIDO, p.getUUID(), MovilServidor.nombre(p), 0, desc));
        LOG.info("[Banco] Objeto {} -> {}: {} x{}", MovilServidor.nombre(p), MovilServidor.nombre(o), id, q);
        MovilServidor.aviso(p, "movil.miscelanea.aviso.objeto_ok", desc);
        MovilServidor.noti(o, MovilRed.APP_BANCO, MovilServidor.nombre(p), MovilServidor.tr("objeto_noti", desc), true);
        estado(p);
        estado(o);
    }

    // ------------------------------------------------------------------ comando de operadores
    @SubscribeEvent
    public static void comando(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("miscelanea")
                .then(Commands.literal("banco").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("jugador", GameProfileArgument.gameProfile())
                                .then(Commands.literal("ver").executes(c -> ver(c)))
                                .then(Commands.literal("dar").then(Commands.argument("cantidad", IntegerArgumentType.integer(1))
                                        .executes(c -> cambiar(c, IntegerArgumentType.getInteger(c, "cantidad")))))
                                .then(Commands.literal("quitar").then(Commands.argument("cantidad", IntegerArgumentType.integer(1))
                                        .executes(c -> cambiar(c, -IntegerArgumentType.getInteger(c, "cantidad"))))))));
    }

    private static int ver(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<GameProfile> l = GameProfileArgument.getGameProfiles(c, "jugador");
        BancoDatos d = BancoDatos.de(c.getSource().getServer());
        for (GameProfile g : l) {
            long s = d.saldo(g.getId());
            c.getSource().sendSuccess(() -> Component.translatable("movil.miscelanea.banco.cmd_ver", g.getName(), s).withStyle(ChatFormatting.GREEN), false);
        }
        return l.size();
    }

    private static int cambiar(CommandContext<CommandSourceStack> c, int delta) throws CommandSyntaxException {
        Collection<GameProfile> l = GameProfileArgument.getGameProfiles(c, "jugador");
        BancoDatos d = BancoDatos.de(c.getSource().getServer());
        String quien = c.getSource().getTextName();
        for (GameProfile g : l) {
            long antes = d.saldo(g.getId());
            long real = delta < 0 ? -Math.min(antes, -delta) : delta;
            d.mover(g.getId(), real);
            d.anotar(g.getId(), new BancoDatos.Movimiento(System.currentTimeMillis(), BancoDatos.M_ADMIN, null, quien, real, ""));
            long s = d.saldo(g.getId());
            LOG.info("[Banco] {} cambia el saldo de {}: {} -> {}", quien, g.getName(), antes, s);
            c.getSource().sendSuccess(() -> Component.translatable("movil.miscelanea.banco.cmd_cambio", g.getName(), real, s).withStyle(ChatFormatting.GREEN), true);
            ServerPlayer o = c.getSource().getServer().getPlayerList().getPlayer(g.getId());
            if (o != null) estado(o);
        }
        return l.size();
    }
}
