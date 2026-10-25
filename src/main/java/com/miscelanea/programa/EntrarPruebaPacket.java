package com.miscelanea.programa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: entrar en la prueba con el rig elegido en el Terminal del Programa. */
public class EntrarPruebaPacket {
    private final int rig;

    public EntrarPruebaPacket(int rig) {
        this.rig = rig;
    }

    public EntrarPruebaPacket(FriendlyByteBuf b) {
        this(b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(rig);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Prueba.entrar(p, Rigs.Rig.de(rig));
    }
}
