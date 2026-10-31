package com.miscelanea.bichos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: tu Bichopedia (atrapados con su récord en cm, y donados). */
public class ColeccionPacket {
    public static CompoundTag cliente = new CompoundTag();
    private final CompoundTag datos;

    public ColeccionPacket(CompoundTag datos) {
        this.datos = datos;
    }

    public ColeccionPacket(FriendlyByteBuf b) {
        this.datos = b.readNbt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeNbt(datos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        cliente = datos == null ? new CompoundTag() : datos;
    }
}
