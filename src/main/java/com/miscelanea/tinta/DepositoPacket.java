package com.miscelanea.tinta;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: nivel del depósito de tinta de un jugador. */
public class DepositoPacket {
    public final int id;
    public final float nivel;

    public DepositoPacket(int id, float nivel) {
        this.id = id;
        this.nivel = nivel;
    }

    public DepositoPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readFloat());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeFloat(nivel);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Deposito.recibir(this));
    }
}
