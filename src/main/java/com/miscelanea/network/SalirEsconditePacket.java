package com.miscelanea.network;

import com.miscelanea.carcel.Escondites;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el jugador escondido pulsa saltar/agacharse para salir. */
public class SalirEsconditePacket {
    public SalirEsconditePacket() {}

    public SalirEsconditePacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null && Escondites.escondido(p)) Escondites.salir(p);
    }
}
