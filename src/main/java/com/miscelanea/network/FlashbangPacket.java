package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: "te han cegado con un Destello", con intensidad 0–1. */
public class FlashbangPacket {
    private final float intensidad;

    public FlashbangPacket(float intensidad) {
        this.intensidad = intensidad;
    }

    public FlashbangPacket(FriendlyByteBuf buf) {
        this.intensidad = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(intensidad);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.miscelanea.client.FlashOverlay.disparar(intensidad));
    }
}
