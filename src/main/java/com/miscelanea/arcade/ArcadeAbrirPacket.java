package com.miscelanea.arcade;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente de la recreativa. Modos: 0 abrir como jugador 1, 1 créditos (respuesta a ficha), 2 empezar partida
 * (con semilla, juego y número de jugadores), 3 no hay créditos, 4 abrir como jugador 2, 5 cambia el compañero (nombre o vacío),
 * 6 no se puede empezar Paso Doble porque suena otra pista o recreativa de baile cerca (jugadores = a cuántos bloques).
 */
public class ArcadeAbrirPacket {
    public final BlockPos pos;
    public final int creditos;
    public final boolean libre;
    public final int modo;
    public final boolean doble;
    public final long semilla;
    public final String juego, companero;
    public final int jugadores;
    /** Máquina desde la que juega este jugador (la enlazada, en el modo 4 con cable); si no, la misma pos. */
    public final BlockPos propia;

    public ArcadeAbrirPacket(BlockPos pos, int creditos, boolean libre) {
        this(pos, creditos, libre, 0);
    }

    public ArcadeAbrirPacket(BlockPos pos, int creditos, boolean libre, int modo) {
        this(pos, creditos, libre, modo, false, 0, "", "", 1);
    }

    public ArcadeAbrirPacket(BlockPos pos, int creditos, boolean libre, int modo, boolean doble, long semilla, String juego, String companero, int jugadores) {
        this(pos, creditos, libre, modo, doble, semilla, juego, companero, jugadores, pos);
    }

    public ArcadeAbrirPacket(BlockPos pos, int creditos, boolean libre, int modo, boolean doble, long semilla, String juego, String companero, int jugadores,
                             BlockPos propia) {
        this.propia = propia;
        this.pos = pos;
        this.creditos = creditos;
        this.libre = libre;
        this.modo = modo;
        this.doble = doble;
        this.semilla = semilla;
        this.juego = juego;
        this.companero = companero;
        this.jugadores = jugadores;
    }

    public ArcadeAbrirPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readVarInt(), b.readBoolean(), b.readByte(), b.readBoolean(), b.readLong(), b.readUtf(32), b.readUtf(32), b.readByte(), b.readBlockPos());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeVarInt(creditos);
        b.writeBoolean(libre);
        b.writeByte(modo);
        b.writeBoolean(doble);
        b.writeLong(semilla);
        b.writeUtf(juego, 32);
        b.writeUtf(companero, 32);
        b.writeByte(jugadores);
        b.writeBlockPos(propia);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.arcade.ArcadeCliente.abrir(this));
    }
}
