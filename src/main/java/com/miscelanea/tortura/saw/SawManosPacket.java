package com.miscelanea.tortura.saw;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: la víctima de la caja de agua mete las manos por los agujeros (tecla o clic derecho al aire). */
public class SawManosPacket {
    public SawManosPacket() {}

    public SawManosPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) CajaAgua.meterManos(p);
    }
}
