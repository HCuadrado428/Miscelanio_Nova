package com.miscelanea.tesoros;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: señal del detector (0..1), distancia al tesoro y ángulo relativo a donde miras. */
public class DetectorPacket {
    final float fuerza, dist, ang;

    public DetectorPacket(float fuerza, float dist, float ang) {
        this.fuerza = fuerza;
        this.dist = dist;
        this.ang = ang;
    }

    public DetectorPacket(FriendlyByteBuf b) {
        this(b.readFloat(), b.readFloat(), b.readFloat());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeFloat(fuerza);
        b.writeFloat(dist);
        b.writeFloat(ang);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tesoros.DetectorCliente.senal(fuerza, dist, ang));
    }
}
