package com.miscelanea.baile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Servidor: sitios donde suena (o va a sonar) una canción de baile, sean pistas de baile o recreativas de Paso Doble.
 * Mientras uno suena, ningún otro a menos de {@link com.miscelanea.ConfigServidor#baileRadioPistas()} bloques puede
 * empezar (para que no se mezclen las músicas). Cada entrada caduca sola en el tic indicado (o nunca, con Long.MAX_VALUE).
 */
public final class FuentesMusica {
    private static final Map<Level, Map<BlockPos, Long>> FUENTES = new WeakHashMap<>();

    private FuentesMusica() {}

    public static synchronized void poner(Level l, BlockPos p, long hastaTic) {
        FUENTES.computeIfAbsent(l, k -> new HashMap<>()).put(p.immutable(), hastaTic);
    }

    public static synchronized void quitar(Level l, BlockPos p) {
        Map<BlockPos, Long> m = FUENTES.get(l);
        if (m != null) m.remove(p);
    }

    public static synchronized boolean suena(Level l, BlockPos p) {
        Map<BlockPos, Long> m = FUENTES.get(l);
        Long h = m == null ? null : m.get(p);
        return h != null && h >= l.getGameTime();
    }

    /** La fuente más cercana (que no sea la propia) a menos del radio, o null. Radio 0 = sin límite (nunca bloquea). */
    @Nullable
    public static synchronized BlockPos otra(Level l, BlockPos p, int radio) {
        if (radio <= 0) return null;
        Map<BlockPos, Long> m = FUENTES.get(l);
        if (m == null) return null;
        long ahora = l.getGameTime();
        m.values().removeIf(h -> h < ahora);
        BlockPos mejor = null;
        double md = (double) radio * radio;
        for (BlockPos o : m.keySet()) {
            if (o.equals(p)) continue;
            double d = o.distSqr(p);
            if (d <= md) {
                md = d;
                mejor = o;
            }
        }
        return mejor;
    }
}
