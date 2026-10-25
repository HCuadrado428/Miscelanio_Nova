package com.miscelanea.programa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: usar el rig (tecla G). */
public class UsarRigPacket {
    public UsarRigPacket() {}

    public UsarRigPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Rigs.usar(p);
    }
}
