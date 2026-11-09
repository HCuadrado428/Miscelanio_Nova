package com.miscelanea.dream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → quien ve a un jugador: ese jugador es Dream (1) o un infectado (2) durante unos ticks (para los ojos que
 * brillan en la oscuridad). Se repite cada segundo mientras dura; el cliente lo olvida si deja de llegar.
 */
public class AuraPacket {
    public final int id, tipo, ticks;

    public AuraPacket(int id, int tipo, int ticks) {
        this.id = id;
        this.tipo = tipo;
        this.ticks = ticks;
    }

    public AuraPacket(FriendlyByteBuf b) {
        this.id = b.readVarInt();
        this.tipo = b.readByte();
        this.ticks = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeByte(tipo);
        b.writeVarInt(ticks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.dream.PersecucionCliente.aura(this));
    }
}
