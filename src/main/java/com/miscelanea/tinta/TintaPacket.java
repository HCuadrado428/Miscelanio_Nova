package com.miscelanea.tinta;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: caras pintadas (o secadas, color 0) de un chunk. 'completo' = sustituye todo el chunk. */
public class TintaPacket {
    public record Cambio(long pos, byte cara, int color) {}

    public final boolean completo;
    public final long chunk;
    public final List<Cambio> cambios;

    public TintaPacket(boolean completo, long chunk, List<Cambio> cambios) {
        this.completo = completo;
        this.chunk = chunk;
        this.cambios = cambios;
    }

    public TintaPacket(FriendlyByteBuf b) {
        completo = b.readBoolean();
        chunk = b.readLong();
        int n = b.readVarInt();
        cambios = new ArrayList<>(n);
        for (int i = 0; i < n; i++) cambios.add(new Cambio(b.readLong(), b.readByte(), b.readInt()));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(completo);
        b.writeLong(chunk);
        b.writeVarInt(cambios.size());
        for (Cambio c : cambios) {
            b.writeLong(c.pos());
            b.writeByte(c.cara());
            b.writeInt(c.color());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Tinta.recibir(this));
    }
}
