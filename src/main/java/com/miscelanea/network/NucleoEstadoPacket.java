package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: un jugador empieza el aliento del núcleo (tick de inicio) o lo corta (−1). */
public class NucleoEstadoPacket {
    private final int jugador;
    private final long inicio;

    public NucleoEstadoPacket(int jugador, long inicio) {
        this.jugador = jugador;
        this.inicio = inicio;
    }

    public NucleoEstadoPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(jugador);
        buf.writeLong(inicio);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.miscelanea.client.nucleo.NucleoCliente.recibir(jugador, inicio));
    }
}
