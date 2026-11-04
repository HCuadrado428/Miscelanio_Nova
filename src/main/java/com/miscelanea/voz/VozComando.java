package com.miscelanea.voz;

import com.miscelanea.Miscelanea;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Voces del voice chat puestas a mano, sin poción:
 * <pre>
 * /miscelanea voz &lt;jugadores&gt; poner &lt;voz&gt;   (p. ej. estatica, helio, grave, fantasma...)
 * /miscelanea voz &lt;jugadores&gt; quitar &lt;voz&gt;
 * /miscelanea voz &lt;jugadores&gt; limpiar
 * </pre>
 * Duran hasta quitarlas (también al morir y al reconectar: se guardan con el jugador). Se suman a las de las pociones.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class VozComando {
    private VozComando() {}

    private static final String CLAVE = "MiscelaneaVocesFijas";
    static final String[] VOCES = {VozPorEfecto.ESTATICA, VozPorEfecto.HELIO, VozPorEfecto.TARTAMUDEO, VozPorEfecto.VENTILADOR,
            VozPorEfecto.LENGUA_TRAPO, VozPorEfecto.GRAVE, VozPorEfecto.AGUDO, VozPorEfecto.SUSURRO, VozPorEfecto.FANTASMA,
            VozPorEfecto.GUTURAL, VozPorEfecto.AMORTIGUADO};

    /** Voces fijas del jugador (las guarda el servidor en sus datos persistentes). */
    public static List<String> fijas(Player p) {
        List<String> l = new ArrayList<>();
        CompoundTag d = p.getPersistentData();
        if (!d.contains(CLAVE, Tag.TAG_LIST)) return l;
        ListTag t = d.getList(CLAVE, Tag.TAG_STRING);
        for (int i = 0; i < t.size(); i++) l.add(t.getString(i));
        return l;
    }

    private static void guardar(Player p, List<String> l) {
        ListTag t = new ListTag();
        for (String s : l) t.add(StringTag.valueOf(s));
        p.getPersistentData().put(CLAVE, t);
    }

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("miscelanea")
                .then(Commands.literal("voz").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .then(Commands.literal("poner").then(Commands.argument("voz", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(VOCES, b))
                                        .executes(c -> cambiar(c, true))))
                                .then(Commands.literal("quitar").then(Commands.argument("voz", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(VOCES, b))
                                        .executes(c -> cambiar(c, false))))
                                .then(Commands.literal("limpiar").executes(VozComando::limpiar)))));
    }

    private static int cambiar(CommandContext<CommandSourceStack> c, boolean poner) throws CommandSyntaxException {
        Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "jugadores");
        String voz = StringArgumentType.getString(c, "voz");
        boolean valida = false;
        for (String v : VOCES) if (v.equals(voz)) valida = true;
        if (!valida) {
            c.getSource().sendFailure(Component.translatable("miscelanea.voz.cmd.desconocida", voz, String.join(", ", VOCES)));
            return 0;
        }
        for (ServerPlayer p : ps) {
            List<String> l = fijas(p);
            l.remove(voz);
            if (poner) l.add(voz);
            guardar(p, l);
            VozSync.sincronizar(p);
        }
        int n = ps.size();
        c.getSource().sendSuccess(() -> Component.translatable(poner ? "miscelanea.voz.cmd.puesta" : "miscelanea.voz.cmd.quitada", voz, n), true);
        return n;
    }

    private static int limpiar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "jugadores");
        for (ServerPlayer p : ps) {
            guardar(p, new ArrayList<>());
            VozSync.sincronizar(p);
        }
        int n = ps.size();
        c.getSource().sendSuccess(() -> Component.translatable("miscelanea.voz.cmd.limpia", n), true);
        return n;
    }

    /** Al reaparecer el jugador nuevo no trae los datos persistentes: se copian del viejo. */
    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        CompoundTag viejo = e.getOriginal().getPersistentData();
        if (viejo.contains(CLAVE)) e.getEntity().getPersistentData().put(CLAVE, viejo.get(CLAVE).copy());
    }
}
