package com.miscelanea.pipboy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes: un jugador lleva el Pip-Boy (bit 0) y/o lo está mirando (bit 1). */
public class PipBoyEstadoPacket {
    private final int jugador;
    private final byte flags;

    public PipBoyEstadoPacket(int jugador, byte flags) {
        this.jugador = jugador;
        this.flags = flags;
    }

    public PipBoyEstadoPacket(FriendlyByteBuf b) {
        this.jugador = b.readInt();
        this.flags = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeInt(jugador);
        b.writeByte(flags);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.pipboy.PipBoyCliente.estado(jugador, flags));
    }
}
