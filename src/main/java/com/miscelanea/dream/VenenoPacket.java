package com.miscelanea.dream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugador: cuánto le queda sin veneno (ticks) y cuánto era en total (para el indicador del HUD). */
public class VenenoPacket {
    public final int restante, total;

    public VenenoPacket(int restante, int total) {
        this.restante = restante;
        this.total = total;
    }

    public VenenoPacket(FriendlyByteBuf b) {
        this.restante = b.readVarInt();
        this.total = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(restante);
        b.writeVarInt(total);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.dream.VenenoCliente.recibir(this));
    }
}
