package com.miscelanea.network;

import com.miscelanea.nucleo.Aliento;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: Shift + clic derecho con la mano vacía llevando el núcleo. */
public class NucleoActivarPacket {
    public NucleoActivarPacket() {}

    public NucleoActivarPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null && p.getMainHandItem().isEmpty()) Aliento.activar(p);
    }
}
