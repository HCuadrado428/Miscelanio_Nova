package com.miscelanea.armasfuego.comun;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: empieza o deja de apuntar (clic derecho mantenido con un arma en la mano). */
public class ApuntarPacket {
    private final boolean apunta;

    public ApuntarPacket(boolean apunta) {
        this.apunta = apunta;
    }

    public ApuntarPacket(FriendlyByteBuf b) {
        apunta = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(apunta);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Apuntar.poner(p, apunta);
    }
}
