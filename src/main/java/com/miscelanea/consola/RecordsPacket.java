package com.miscelanea.consola;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: tus récords en cada juego de la consola. */
public class RecordsPacket {
    public static CompoundTag cliente = new CompoundTag();
    private final CompoundTag datos;

    public RecordsPacket(CompoundTag datos) {
        this.datos = datos;
    }

    public RecordsPacket(FriendlyByteBuf b) {
        this.datos = b.readNbt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeNbt(datos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        cliente = datos == null ? new CompoundTag() : datos;
    }
}
