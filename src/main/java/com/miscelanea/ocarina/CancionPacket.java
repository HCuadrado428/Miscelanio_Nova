package com.miscelanea.ocarina;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: he tocado entera esta canción. */
public class CancionPacket {
    private final int cancion;

    public CancionPacket(int cancion) {
        this.cancion = cancion;
    }

    public CancionPacket(FriendlyByteBuf b) {
        this.cancion = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(cancion);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        Cancion c = Cancion.de(cancion);
        if (p != null && c != null) Ocarina.tocada(p, c);
    }
}
