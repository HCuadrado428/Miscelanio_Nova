package com.miscelanea.armas;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: clic izquierdo con la superescopeta pulsado (lanzar gancho) o suelto (soltarlo). */
public class GanchoPacket {
    private final boolean pulsado;

    public GanchoPacket(boolean pulsado) {
        this.pulsado = pulsado;
    }

    public GanchoPacket(FriendlyByteBuf b) {
        this.pulsado = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(pulsado);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null) return;
        if (pulsado) Gancho.lanzar(p);
        else Gancho.soltar(p);
    }
}
