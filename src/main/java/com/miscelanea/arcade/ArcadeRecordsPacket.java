package com.miscelanea.arcade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes: las tablas de récords de las recreativas. */
public class ArcadeRecordsPacket {
    public static CompoundTag cliente = new CompoundTag();
    private final CompoundTag t;

    public ArcadeRecordsPacket(CompoundTag t) {
        this.t = t;
    }

    public ArcadeRecordsPacket(FriendlyByteBuf b) {
        this.t = b.readNbt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeNbt(t);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        cliente = t == null ? new CompoundTag() : t;
    }
}
