package com.miscelanea.juegos;

import com.miscelanea.ConfigServidor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Final de una partida de mesa: cuenta las victorias, avisa a la mesa y hace celebrar (o lamentarse) a los jugadores. */
public final class Partidas {
    private Partidas() {}

    /**
     * Llamar una sola vez cuando una partida termina con ganador.
     *
     * @param juego      identificador del juego (ver {@link Victorias#JUEGOS})
     * @param ganadores  UUID de los ganadores humanos (los null, la CPU, se ignoran)
     * @param perdedores UUID de los perdedores humanos (null = CPU)
     * @param rivalHumano si alguno de los rivales del ganador era una persona
     */
    public static void terminada(Level l, BlockPos pos, String juego, Collection<UUID> ganadores, Collection<UUID> perdedores, boolean rivalHumano) {
        if (l == null || l.isClientSide || !(l instanceof ServerLevel sl)) return;
        Vec3 c = Vec3.atCenterOf(pos);
        List<Component> avisos = new ArrayList<>();
        for (UUID u : ganadores) {
            if (u == null) continue;
            ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(u);
            if (rivalHumano || ConfigServidor.mesasVictoriasContraCpu()) {
                int n = sp != null ? Victorias.sumar(sp, juego, rivalHumano)
                        : Victorias.sumar(sl.getServer(), u, Victorias.nombre(u), juego, rivalHumano);
                String nombre = sp != null ? sp.getGameProfile().getName() : Victorias.nombre(u);
                avisos.add(Component.translatable("msg.miscelanea.victorias.suma", nombre, n, Component.translatable("victorias.miscelanea." + juego))
                        .withStyle(ChatFormatting.GOLD));
            }
            if (sp != null && sp.level() == l) Gestos.ganar(sp, c);
        }
        for (UUID u : perdedores) {
            if (u == null || ganadores.contains(u)) continue;
            if (sl.getPlayerByUUID(u) instanceof ServerPlayer sp) Gestos.perder(sp, c);
        }
        if (avisos.isEmpty()) return;
        for (ServerPlayer o : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(14)))
            for (Component m : avisos) o.displayClientMessage(m, false);
    }

    /** Atajo: un ganador (o ninguno, null) y el resto perdedores. */
    public static void terminada(Level l, BlockPos pos, String juego, @Nullable UUID ganador, UUID... rivales) {
        List<UUID> g = new ArrayList<>();
        if (ganador != null) g.add(ganador);
        List<UUID> p = new ArrayList<>(Arrays.asList(rivales));
        p.removeIf(u -> u == null || u.equals(ganador));
        boolean humano = p.stream().anyMatch(Objects::nonNull);
        terminada(l, pos, juego, g, p, humano);
    }
}
