package com.miscelanea.tortura.saw;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Llaves escondidas en los pozos de agujas (una por dimensión, en data/miscelanea_pozo_llaves.dat): la llave que ha
 * escondido el captor en cada bloque, o la marca de "llave al azar" (se fabrica al encontrarla).
 */
public class PozoLlaves extends SavedData {
    private static final String NOMBRE = "miscelanea_pozo_llaves";
    final Map<Long, ItemStack> llaves = new HashMap<>();
    final Set<Long> aleatorias = new HashSet<>();

    public static PozoLlaves de(ServerLevel sl) {
        return sl.getDataStorage().computeIfAbsent(PozoLlaves::cargar, PozoLlaves::new, NOMBRE);
    }

    private static PozoLlaves cargar(CompoundTag t) {
        PozoLlaves p = new PozoLlaves();
        ListTag l = t.getList("Llaves", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            if (c.getBoolean("Azar")) p.aleatorias.add(c.getLong("Pos"));
            else {
                ItemStack st = ItemStack.of(c.getCompound("Llave"));
                if (!st.isEmpty()) p.llaves.put(c.getLong("Pos"), st);
            }
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Map.Entry<Long, ItemStack> e : llaves.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getKey());
            c.put("Llave", e.getValue().save(new CompoundTag()));
            l.add(c);
        }
        for (long p : aleatorias) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", p);
            c.putBoolean("Azar", true);
            l.add(c);
        }
        t.put("Llaves", l);
        return t;
    }

    public boolean hay(BlockPos p) {
        return llaves.containsKey(p.asLong()) || aleatorias.contains(p.asLong());
    }

    public void esconder(BlockPos p, ItemStack llave) {
        aleatorias.remove(p.asLong());
        llaves.put(p.asLong(), llave.copy());
        setDirty();
    }

    public void azar(BlockPos p) {
        if (llaves.containsKey(p.asLong())) return;
        aleatorias.add(p.asLong());
        setDirty();
    }

    /** Saca lo que haya en ese bloque: la llave escondida, EMPTY si era "al azar" (hay que fabricarla) o null si nada. */
    @Nullable
    public ItemStack sacar(BlockPos p) {
        ItemStack st = llaves.remove(p.asLong());
        boolean az = aleatorias.remove(p.asLong());
        if (st != null || az) setDirty();
        if (st != null) return st;
        return az ? ItemStack.EMPTY : null;
    }
}
