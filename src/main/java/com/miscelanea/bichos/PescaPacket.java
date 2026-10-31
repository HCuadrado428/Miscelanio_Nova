package com.miscelanea.bichos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: ¡ha picado! Empieza el minijuego con este pez y esta dificultad. */
public class PescaPacket {
    private final int especie, dificultad;

    public PescaPacket(int especie, int dificultad) {
        this.especie = especie;
        this.dificultad = dificultad;
    }

    public PescaPacket(FriendlyByteBuf b) {
        this.especie = b.readByte();
        this.dificultad = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(especie);
        b.writeByte(dificultad);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.bichos.PescaScreen.abrir(especie, dificultad));
    }
}
