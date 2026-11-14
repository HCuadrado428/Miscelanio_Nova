package com.miscelanea.armasfuego.escopeta;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: clic izquierdo con la escopeta de presión cargada de aire (disparar). */
public class DispararPresionPacket {
    public DispararPresionPacket() {}

    public DispararPresionPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null && p.getMainHandItem().getItem() instanceof EscopetaPresionItem) DisparoPresion.disparar(p, p.getMainHandItem());
    }
}
