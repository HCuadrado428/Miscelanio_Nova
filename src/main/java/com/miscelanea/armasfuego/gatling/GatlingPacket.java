package com.miscelanea.armasfuego.gatling;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el que va a los mandos de la Gatling empieza o deja de girar la manivela (clic derecho mantenido). */
public class GatlingPacket {
    private final boolean girando;

    public GatlingPacket(boolean girando) {
        this.girando = girando;
    }

    public GatlingPacket(FriendlyByteBuf b) {
        girando = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(girando);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null && p.getVehicle() instanceof GatlingEntity g) g.ponerGirando(girando);
    }
}
