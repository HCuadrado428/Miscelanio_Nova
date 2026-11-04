package com.miscelanea.arcade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → clientes: los 10 primeros de cada categoría del ranking (las tablas de las recreativas son las suyas). Con
 * {@link #TODO} trae todas y sustituye lo que había; sin él, solo las categorías que han cambiado.
 */
public class ArcadeRecordsPacket {
    public static final String TODO = "$todo";
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
        if (t == null) return;
        if (t.getBoolean(TODO)) {
            CompoundTag n = t.copy();
            n.remove(TODO);
            cliente = n;
        } else {
            CompoundTag n = cliente.copy();
            for (String k : t.getAllKeys()) n.put(k, t.get(k));
            cliente = n;
        }
    }
}
