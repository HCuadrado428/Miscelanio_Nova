package com.miscelanea.bichos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: ¿has ganado el minijuego de pesca? */
public class PescaResultadoPacket {
    private final boolean ganado;

    public PescaResultadoPacket(boolean ganado) {
        this.ganado = ganado;
    }

    public PescaResultadoPacket(FriendlyByteBuf b) {
        this.ganado = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(ganado);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Pesca.resultado(p, ganado);
    }
}
