package com.miscelanea.tortura.qte;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: ese minijuego se ha cancelado o se ha acabado su plazo; ciérralo. */
public class QteFinPacket {
    private final int id;

    public QteFinPacket(int id) {
        this.id = id;
    }

    public QteFinPacket(FriendlyByteBuf b) {
        this.id = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.qte.QteCliente.fin(id));
    }
}
