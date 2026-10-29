package com.miscelanea.hacha;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: postura mantenida con el hacha (0 nada, 1 apuntar, 2 escudo, 3 cargar, 4 llamar). */
public class PosturaPacket {
    public static final byte NADA = 0, APUNTAR = 1, ESCUDO = 2, CARGAR = 3, LLAMAR = 4;
    private final byte postura;

    public PosturaPacket(byte postura) {
        this.postura = postura;
    }

    public PosturaPacket(FriendlyByteBuf b) {
        this.postura = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(postura);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Leviatan.postura(p, postura);
    }
}
