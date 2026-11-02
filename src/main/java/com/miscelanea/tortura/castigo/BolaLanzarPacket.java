package com.miscelanea.tortura.castigo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el preso que lleva su bola en brazos la lanza (clic derecho). El servidor comprueba que la lleva. */
public class BolaLanzarPacket {
    public BolaLanzarPacket() {}

    public BolaLanzarPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) Bolas.lanzar(p);
    }
}
