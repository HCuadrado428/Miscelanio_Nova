package com.miscelanea.network;

import com.miscelanea.nucleo.Aliento;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: empieza a cargar (true) o suelta (false) el aliento del núcleo. */
public class NucleoActivarPacket {
    private final boolean pulsado;

    public NucleoActivarPacket(boolean pulsado) {
        this.pulsado = pulsado;
    }

    public NucleoActivarPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(pulsado);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        if (pulsado) {
            if (p.getMainHandItem().isEmpty()) Aliento.pulsar(p);
        } else {
            Aliento.soltar(p);
        }
    }
}
