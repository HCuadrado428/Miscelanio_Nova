package com.miscelanea.gravedad;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: clic izquierdo con la pistola de gravedad (lanzar). */
public class GravedadPacket {
    public GravedadPacket() {}

    public GravedadPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Gravedad.punt(p);
    }
}
