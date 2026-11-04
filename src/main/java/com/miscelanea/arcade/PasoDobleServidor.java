package com.miscelanea.arcade;

import com.miscelanea.ConfigServidor;
import com.miscelanea.baile.BailePacket;
import com.miscelanea.baile.CancionesBaile;
import com.miscelanea.baile.FuentesMusica;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.ranking.Ranking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Servidor de la recreativa Paso Doble con las canciones de la pista de baile ({@link CancionesBaile}).
 * <ul>
 *   <li>Al empezar partida (EMPEZAR de "paso") no se deja si otra pista o recreativa suena a menos del radio de la
 *       configuración, y se reserva la máquina (las de alrededor tampoco pueden empezar) mientras se elige canción.</li>
 *   <li>ARCADE_EMPIEZA: el que juega dice qué canción y dificultad; la reserva dura lo que la canción y los de alrededor
 *       oyen la música (con posición en la recreativa) y ven al jugador bailar la coreografía.</li>
 *   <li>ARCADE_FIN: los puntos (nunca más de lo que da de sí lo que ha sonado) van al ranking paso/&lt;canción&gt;/&lt;dif&gt;.</li>
 * </ul>
 */
public final class PasoDobleServidor {
    /** Tics que se reserva la máquina mientras se elige canción (el menú empieza solo a los 30 s). */
    static final int RESERVA_MENU = 20 * 60;

    private PasoDobleServidor() {}

    /** Otra pista/recreativa sonando cerca (o null). */
    @Nullable
    static BlockPos otraSonando(RecreativaBlockEntity be) {
        if (be.getLevel() == null) return null;
        return FuentesMusica.otra(be.getLevel(), be.getBlockPos(), ConfigServidor.baileRadioPistas());
    }

    /** Al empezar una partida de Paso Doble: reservar la máquina. */
    static void reservar(RecreativaBlockEntity be) {
        if (be.getLevel() == null) return;
        be.pasoCancion = null;
        be.pasoFin1 = be.pasoFin2 = false;
        FuentesMusica.poner(be.getLevel(), be.getBlockPos(), be.getLevel().getGameTime() + RESERVA_MENU);
    }

    /** Se acaba la partida o se va el jugador: la máquina deja de bloquear y los de alrededor dejan de oír/ver el baile. */
    static void soltar(RecreativaBlockEntity be, @Nullable UUID quien) {
        if (be.getLevel() == null || be.getLevel().isClientSide) return;
        boolean sonaba = FuentesMusica.suena(be.getLevel(), be.getBlockPos());
        FuentesMusica.quitar(be.getLevel(), be.getBlockPos());
        if (!sonaba || be.pasoCancion == null || !(be.getLevel() instanceof ServerLevel sl)) return;
        for (UUID u : new UUID[]{be.jugador, be.jugador2, quien}) {
            ServerPlayer p = u == null ? null : sl.getServer().getPlayerList().getPlayer(u);
            if (p != null) alrededor(be, new BailePacket(BailePacket.ARCADE_PARA, be.getBlockPos(), p.getId(), 1, 0));
        }
    }

    /** Un jugador cierra la recreativa: si es el 1, se acaba todo; si es el 2, solo deja de bailar él. */
    static void cerrar(RecreativaBlockEntity be, ServerPlayer p, boolean invitado) {
        if (!invitado) soltar(be, p.getUUID());
        else if (be.pasoCancion != null) alrededor(be, new BailePacket(BailePacket.ARCADE_PARA, be.getBlockPos(), p.getId(), 0, 0));
    }

    /** A los jugadores cerca de la máquina menos los dos que juegan (esos ponen su música y su baile ellos mismos). */
    private static void alrededor(RecreativaBlockEntity be, BailePacket m) {
        if (!(be.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = be.getBlockPos();
        for (ServerPlayer o : sl.players()) {
            if (o.getUUID().equals(be.jugador) || o.getUUID().equals(be.jugador2)) continue;
            if (o.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 72 * 72) continue;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> o), m);
        }
    }

    /** ARCADE_EMPIEZA / ARCADE_FIN de un jugador de la recreativa. */
    public static void recibir(ServerPlayer p, BailePacket m) {
        BlockPos pos = m.pos;
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) > 100 || !p.level().isLoaded(pos)) return;
        if (!(p.level().getBlockEntity(pos) instanceof RecreativaBlockEntity be) || !"paso".equals(be.partidaJuego)) return;
        boolean host = p.getUUID().equals(be.jugador), invitado = p.getUUID().equals(be.jugador2);
        if (!host && !invitado) return;
        long ahora = p.level().getGameTime();
        int dif = Math.max(0, Math.min(2, m.b));
        if (m.tipo == BailePacket.ARCADE_EMPIEZA) {
            CancionesBaile.Cancion c = CancionesBaile.de(m.cancion);
            if (host) {
                be.pasoCancion = c == null ? null : c.id;
                be.pasoInicio = ahora;
                be.pasoDif1 = dif;
                be.pasoFin1 = be.pasoFin2 = false;
                // la reserva dura lo que la canción (y un poco); si el servidor no la tiene, cinco minutos
                FuentesMusica.poner(p.level(), pos, ahora + (c == null ? 6000 : (long) (c.duracion * 20) + 200));
            } else {
                be.pasoDif2 = dif;
                be.pasoFin2 = false;
            }
            if (c != null) alrededor(be, new BailePacket(BailePacket.ARCADE_EMPIEZA, pos, p.getId(), host ? 1 : 0, dif, c.id));
            return;
        }
        if (m.tipo != BailePacket.ARCADE_FIN) return;
        CancionesBaile.Cancion c = CancionesBaile.de(be.pasoCancion);
        if (host) FuentesMusica.quitar(p.level(), pos);
        alrededor(be, new BailePacket(BailePacket.ARCADE_PARA, pos, p.getId(), host ? 1 : 0, 0));
        if (c == null || (host ? be.pasoFin1 : be.pasoFin2)) return;
        if (host) be.pasoFin1 = true;
        else be.pasoFin2 = true;
        int d = host ? be.pasoDif1 : be.pasoDif2;
        // como en la pista: nunca más de lo que da de sí el trozo de canción que ha sonado (todo perfecto), con 2 s de margen
        int max = c.maxPuntos(d, (ahora - be.pasoInicio) * 3 + 120);
        int puntos = Math.min(m.a, max);
        if (puntos > 0) Ranking.mejor(p, Ranking.categoriaPaso(c.id, d), puntos);
    }
}
