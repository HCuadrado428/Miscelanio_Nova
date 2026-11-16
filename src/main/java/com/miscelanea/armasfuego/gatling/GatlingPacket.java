package com.miscelanea.armasfuego.gatling;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor: el que va a los mandos de la Gatling (o el que lleva el barco donde va montada) empieza o deja
 * de girar la manivela (clic izquierdo mantenido) y de apuntar (clic derecho mantenido).
 */
public class GatlingPacket {
    private final boolean girando, apunta;

    public GatlingPacket(boolean girando, boolean apunta) {
        this.girando = girando;
        this.apunta = apunta;
    }

    public GatlingPacket(FriendlyByteBuf b) {
        girando = b.readBoolean();
        apunta = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(girando);
        b.writeBoolean(apunta);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null) return;
        GatlingEntity g = GatlingEntity.deJugador(p);
        if (g != null) {
            g.ponerGirando(girando);
            g.ponerApunta(apunta);
        }
    }
}
