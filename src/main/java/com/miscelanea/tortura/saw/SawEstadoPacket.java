package com.miscelanea.tortura.saw;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: la caja de agua de una entidad. caja = la lleva; activa = se está llenando; nivel (0..1) y sangre
 * (0..1) del agua; tanque = posición del tanque (asLong) o {@link Long#MIN_VALUE}; empujes = veces que ha metido las
 * manos; abierta = ticks que lleva abriéndose (0 = cerrada).
 */
public class SawEstadoPacket {
    public final int entidad;
    public final boolean caja, activa;
    public final float nivel, sangre;
    public final long tanque;
    public final int empujes, abierta;

    public SawEstadoPacket(int entidad, boolean caja, boolean activa, float nivel, float sangre, long tanque, int empujes, int abierta) {
        this.entidad = entidad;
        this.caja = caja;
        this.activa = activa;
        this.nivel = nivel;
        this.sangre = sangre;
        this.tanque = tanque;
        this.empujes = empujes;
        this.abierta = abierta;
    }

    public SawEstadoPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.caja = b.readBoolean();
        this.activa = b.readBoolean();
        this.nivel = b.readFloat();
        this.sangre = b.readFloat();
        this.tanque = b.readLong();
        this.empujes = b.readVarInt();
        this.abierta = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeBoolean(caja);
        b.writeBoolean(activa);
        b.writeFloat(nivel);
        b.writeFloat(sangre);
        b.writeLong(tanque);
        b.writeVarInt(Math.max(0, empujes));
        b.writeVarInt(Math.max(0, abierta));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.saw.SawCliente.recibir(this));
    }
}
