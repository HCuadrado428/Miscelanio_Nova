package com.miscelanea.espera.eventos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** Qué objetos de los rincones raros ya se ha llevado alguien (cada rincón da el suyo una sola vez por mundo). */
public final class RinconesDatos extends SavedData {
    private static final String NOMBRE = "miscelanea_espera_rincones";
    final Set<Long> cogidos = new HashSet<>();

    static RinconesDatos de(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(RinconesDatos::cargar, RinconesDatos::new, NOMBRE);
    }

    private static RinconesDatos cargar(CompoundTag t) {
        RinconesDatos d = new RinconesDatos();
        for (long k : t.getLongArray("Cogidos")) d.cogidos.add(k);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("Cogidos", new LongArrayTag(cogidos.stream().mapToLong(Long::longValue).toArray()));
        return t;
    }

    boolean cogido(long k) {
        return cogidos.contains(k);
    }

    void coger(long k) {
        if (cogidos.add(k)) setDirty();
    }
}
