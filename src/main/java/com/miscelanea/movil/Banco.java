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
 * Banco y Bizum (lado servidor). Cada jugador tiene un saldo ({@link BancoDatos}, en chips de OmegaChips o esmeraldas: ver
 * {@link Dinero}): lo ingresa desde su inventario, lo retira en monedas (sin límite en un cajero, poco desde el móvil) y se las manda a otros (Bizum, con concepto). También puede mandar un objeto de su
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
            case MovilRed.C_FICHAS -> fichas(p, b.readVarInt());
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

    // ------------------------------------------------------------------ cajero y límite del móvil
    private record Cajero(net.minecraft.core.BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {}

    private static final Map<UUID, Cajero> CAJERO = new HashMap<>();
    /** Lo movido hoy desde el móvil: día (desde 1970) y cantidad. */
    private static final Map<UUID, long[]> HOY = new HashMap<>();

    /** Al usar un cajero: se apunta y se le manda el estado (el cliente abre la pantalla del cajero). */
    public static void abrirCajero(ServerPlayer p, net.minecraft.core.BlockPos pos) {
        CAJERO.put(p.getUUID(), new Cajero(pos.immutable(), p.level().dimension()));
        estado(p);
    }

    public static boolean enCajero(ServerPlayer p) {
        Cajero c = CAJERO.get(p.getUUID());
        if (c == null || c.dim != p.level().dimension()) return false;
        double d = MovilConfig.bancoCajeroDistancia();
        if (p.distanceToSqr(c.pos.getX() + 0.5, c.pos.getY() + 0.5, c.pos.getZ() + 0.5) > d * d) return false;
        return p.level().getBlockState(c.pos).getBlock() instanceof CajeroBlock;
    }

    private static long dia() {
        return java.time.LocalDate.now().toEpochDay();
    }

    /** Lo que aún se puede mover hoy desde el móvil en una operación. */
    static long limiteMovil(ServerPlayer p) {
        long[] h = HOY.get(p.getUUID());
        long usado = h != null && h[0] == dia() ? h[1] : 0;
        return Math.max(0, Math.min(MovilConfig.bancoMovilOperacion(), MovilConfig.bancoMovilDia() - usado));
    }

    private static void usarMovil(ServerPlayer p, long n) {
        long[] h = HOY.computeIfAbsent(p.getUUID(), k -> new long[]{dia(), 0});
        if (h[0] != dia()) {
            h[0] = dia();
            h[1] = 0;
        }
        h[1] += n;
    }

    public static void estado(ServerPlayer p) {
        BancoDatos d = BancoDatos.de(p.server);
        List<BancoDatos.Movimiento> h = d.historial.getOrDefault(p.getUUID(), List.of());
        MovilRed.aJugador(p, MovilRed.S_BANCO, b -> {
            b.writeVarLong(d.saldo(p.getUUID()));
            b.writeVarLong(Dinero.enInventario(p));
            boolean caj = enCajero(p);
            b.writeBoolean(caj);
            b.writeBoolean(Dinero.omega());
            b.writeVarLong(caj ? Long.MAX_VALUE / 4 : limiteMovil(p));
            b.writeVarInt(MovilConfig.precioFicha());
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

    private static void ingresar(ServerPlayer p, int pedido) {
        if (rapido(p)) return;
        long n = Math.min(pedido, Dinero.enInventario(p));
        boolean caj = enCajero(p);
        if (!caj) {
            long lim = limiteMovil(p);
            if (lim <= 0) {
                MovilServidor.aviso(p, "movil.miscelanea.aviso.banco_cajero", "");
                estado(p);
                return;
            }
            n = Math.min(n, lim);
        }
        if (n <= 0) return;
        long hecho = Dinero.cobrar(p, n);
        if (hecho <= 0) return;
        if (!caj) usarMovil(p, hecho);
        BancoDatos d = BancoDatos.de(p.server);
        d.mover(p.getUUID(), hecho);
        d.anotar(p.getUUID(), new BancoDatos.Movimiento(System.currentTimeMillis(), BancoDatos.M_INGRESO, null, "", hecho, ""));
        p.getInventory().setChanged();
        LOG.info("[Banco] {} ingresa {} ({}; saldo {})", MovilServidor.nombre(p), hecho, caj ? "cajero" : "móvil", d.saldo(p.getUUID()));
        estado(p);
    }

    private static void retirar(ServerPlayer p, int n) {
        if (rapido(p)) return;
        BancoDatos d = BancoDatos.de(p.server);
        boolean caj = enCajero(p);
        if (!caj && n > limiteMovil(p)) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.banco_cajero", "");
            estado(p);
            return;
        }
        // como mucho lo que quepa en 36 montones de la moneda más grande
        long tope = 36L * 64 * Dinero.monedas().get(0).valor();
        if (n <= 0 || n > tope || !d.mover(p.getUUID(), -n)) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.banco_saldo", "");
            estado(p);
            return;
        }
        if (!caj) usarMovil(p, n);
        Dinero.dar(p, n);
        d.anotar(p.getUUID(), new BancoDatos.Movimiento(System.currentTimeMillis(), BancoDatos.M_RETIRADA, null, "", -n, ""));
        LOG.info("[Banco] {} retira {} ({}; saldo {})", MovilServidor.nombre(p), n, caj ? "cajero" : "móvil", d.saldo(p.getUUID()));
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
        LOG.info("[Banco] Bizum {} -> {}: {} ({})", yo, otro.nombre, n, concepto);
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

    // ------------------------------------------------------------------ fichas de recreativa en el cajero
    private static void fichas(ServerPlayer p, int n) {
        if (rapido(p) || !enCajero(p) || n <= 0 || n > 64) return;
        long precio = (long) n * MovilConfig.precioFicha();
        BancoDatos d = BancoDatos.de(p.server);
        if (precio > 0 && !d.mover(p.getUUID(), -precio)) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.banco_saldo", "");
            estado(p);
            return;
        }
        ItemStack s = new ItemStack(com.miscelanea.registry.ModItems.FICHA.get(), n);
        if (!p.getInventory().add(s) || !s.isEmpty()) p.drop(s, false);
        if (precio > 0) d.anotar(p.getUUID(), new BancoDatos.Movimiento(System.currentTimeMillis(), BancoDatos.M_RETIRADA, null, "", -precio,
                MovilServidor.tr("fichas_concepto", n)));
        LOG.info("[Banco] {} compra {} fichas por {}", MovilServidor.nombre(p), n, precio);
        estado(p);
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
