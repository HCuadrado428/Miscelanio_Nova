package com.miscelanea.arcade;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Cliente → servidor: cerrar, empezar partida (gasta crédito), meter ficha o enviar puntuación. */
public class ArcadeAccionPacket {
    public static final int CERRAR = 0, EMPEZAR = 1, FICHA = 2, RECORD = 3, ESPERAR = 4;
    private final BlockPos pos;
    private final int accion, puntos;
    private final String juego;

    /** Una partida de menos de 3 s no entra en la tabla. */
    static final int MIN_TICS = 60;

    /**
     * Tope de puntos para una partida que ha durado tantos tics: una base más un ritmo por segundo muy generoso (los juegos
     * con multiplicadores o niveles altos, más). Por encima de eso no es una partida normal.
     */
    static long maxPuntos(String juego, long tics) {
        long porSegundo = switch (juego) {
            case "pinball", "tetrominos", "bloques" -> 15_000;
            default -> 5_000;
        };
        return Math.min(9_999_999L, 30_000L + porSegundo * (tics / 20));
    }

    public ArcadeAccionPacket(BlockPos pos, int accion, String juego, int puntos) {
        this.pos = pos;
        this.accion = accion;
        this.juego = juego;
        this.puntos = puntos;
    }

    public ArcadeAccionPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readByte(), b.readUtf(32), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeByte(accion);
        b.writeUtf(juego, 32);
        b.writeVarInt(puntos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) > 100 || !p.level().isLoaded(pos)) return;
        if (!(p.level().getBlockEntity(pos) instanceof RecreativaBlockEntity be)) return;
        boolean soy2 = p.getUUID().equals(be.jugador2);
        if (!soy2 && (be.jugador == null || !be.jugador.equals(p.getUUID()))) return;
        boolean libre = p.getAbilities().instabuild;
        if (soy2 && accion == EMPEZAR) return;
        switch (accion) {
            case ESPERAR -> {
                // el jugador 1 espera rival: cualquiera puede unirse con clic derecho en esta máquina
                if (!soy2) be.esperaRival = true;
            }
            case CERRAR -> {
                be.esperaRival = false;
                be.soltarEnlace(be.jugador2);
                if (soy2) be.jugador2 = null;
                else {
                    be.jugador = null;
                    be.jugador2 = null;
                    be.acabarPartida();
                }
                be.avisarCompaneros();
            }
            case FICHA -> {
                if (RecreativaBlock.gastarFicha(p)) {
                    be.creditos++;
                    be.setChanged();
                    p.level().playSound(null, pos, ModSounds.ARCADE_FICHA.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                }
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ArcadeAbrirPacket(pos, be.creditos, libre, 1, be.doble(), 0, "", "", 1));
            }
            case EMPEZAR -> {
                be.esperaRival = false;
                ServerPlayer p2 = be.jugador2 == null ? null : p.server.getPlayerList().getPlayer(be.jugador2);
                int jug = p2 != null && ArcadeJuegos.dosJugadores(juego) ? 2 : 1;
                boolean ok = libre || be.creditos >= jug;
                if (ok && !libre) {
                    be.creditos -= jug;
                    be.setChanged();
                }
                long semilla = p.getRandom().nextLong();
                // el servidor apunta la partida: solo se aceptará un récord de este jugador, en este juego y en esta máquina
                if (ok && ArcadeJuegos.esArcade(juego)) be.empezarPartida(p.getUUID(), juego, p.level().getGameTime(), semilla);
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ArcadeAbrirPacket(pos, be.creditos, libre, ok ? 2 : 3, be.doble(), semilla, juego, "", jug));
                if (ok && jug == 2)
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p2), new ArcadeAbrirPacket(pos, be.creditos, libre, 2, true, semilla, juego, "", 2));
            }
            case RECORD -> {
                // solo vale si hay una partida empezada aquí por este jugador (con crédito gastado) y una vez por partida;
                // los puntos no pueden pasar de lo que da de sí el tiempo jugado
                if (soy2 || !ArcadeJuegos.esArcade(juego) || !p.getUUID().equals(be.partidaDe) || !juego.equals(be.partidaJuego)) return;
                long tics = p.level().getGameTime() - be.partidaInicio;
                be.acabarPartida();
                if (tics < MIN_TICS || puntos <= 0) return;
                ArcadeRecords.registrar(p, juego, (int) Math.min(puntos, maxPuntos(juego, tics)));
            }
        }
    }
}
