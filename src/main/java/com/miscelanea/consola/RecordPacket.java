package com.miscelanea.consola;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: has terminado una partida con estos puntos. */
public class RecordPacket {
    private final int juego, puntos;

    public RecordPacket(int juego, int puntos) {
        this.juego = juego;
        this.puntos = puntos;
    }

    public RecordPacket(FriendlyByteBuf b) {
        this.juego = b.readVarInt();
        this.puntos = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(juego);
        b.writeVarInt(puntos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        Juego j = Juego.de(juego);
        if (p != null && j != null) Records.registrar(p, j, puntos);
    }
}
