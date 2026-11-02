package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → víctima: empieza (o se para) su minijuego dentro del aparato. */
public class AparatoJuegoPacket {
    public static final int PARAR = 0, DONCELLA = 1, PULSO = 2;
    public final int juego;
    public final BlockPos pos;
    public final long semilla;
    public final float dificultad;

    public AparatoJuegoPacket(int juego, BlockPos pos, long semilla, float dificultad) {
        this.juego = juego;
        this.pos = pos;
        this.semilla = semilla;
        this.dificultad = dificultad;
    }

    public AparatoJuegoPacket(FriendlyByteBuf b) {
        this.juego = b.readByte();
        this.pos = b.readBlockPos();
        this.semilla = b.readLong();
        this.dificultad = b.readFloat();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(juego);
        b.writeBlockPos(pos);
        b.writeLong(semilla);
        b.writeFloat(dificultad);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.aparatos.AparatosCliente.juego(this));
    }
}
