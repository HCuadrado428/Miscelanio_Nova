package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: dos entidades unidas por Cupido (tipo 0) o enfrentadas por Enemigo (tipo 1). */
public class LazoPacket {
    private final int a, b, tipo;

    public LazoPacket(int a, int b, int tipo) {
        this.a = a;
        this.b = b;
        this.tipo = tipo;
    }

    public LazoPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeByte(tipo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.Lazos.recibir(a, b, tipo));
    }
}
