package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: estado del aliento de un jugador.
 * carga = tick en que empezó a cargar (−1 = nada), disparo = tick en que soltó (−1 = aún cargando), potencia 0–1.
 */
public class NucleoEstadoPacket {
    private final int jugador;
    private final long carga, disparo;
    private final float potencia;

    public NucleoEstadoPacket(int jugador, long carga, long disparo, float potencia) {
        this.jugador = jugador;
        this.carga = carga;
        this.disparo = disparo;
        this.potencia = potencia;
    }

    public NucleoEstadoPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(jugador);
        buf.writeLong(carga);
        buf.writeLong(disparo);
        buf.writeFloat(potencia);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.miscelanea.client.nucleo.NucleoCliente.recibir(jugador, carga, disparo, potencia));
    }
}
