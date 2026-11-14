package com.miscelanea.armasfuego.pistola;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: agachado + clic izquierdo con el revólver = girar el tambor (ruleta). */
public class GirarPacket {
    public GirarPacket() {}

    public GirarPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null || p.isSpectator()) return;
        var s = p.getMainHandItem();
        if (s.getItem() instanceof RevolverItem) Revolver.girar(p, s);
    }
}
