package com.miscelanea.llave;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: tus PM. */
public class PmPacket {
    private final float pm;

    public PmPacket(float pm) {
        this.pm = pm;
    }

    public PmPacket(FriendlyByteBuf b) {
        this.pm = b.readFloat();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeFloat(pm);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Magia.cliente = pm;
    }
}
