package com.miscelanea.espera.eventos;

import com.miscelanea.Miscelanea;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Los logros del Espera (pestaña propia, ids {@code miscelanea:espera/...}). Todos sus criterios son
 * {@code minecraft:impossible}: los da el código cuando pasa lo que piden.
 */
public final class Logros {
    private Logros() {}

    @Nullable
    static Advancement logro(ServerPlayer p, String id) {
        return p.server.getAdvancements().getAdvancement(new ResourceLocation(Miscelanea.MOD_ID, "espera/" + id));
    }

    /** Da ese criterio de ese logro (si ya lo tenía, no pasa nada). */
    public static void dar(ServerPlayer p, String id, String criterio) {
        Advancement a = logro(p, id);
        if (a != null) p.getAdvancements().award(a, criterio);
        else Miscelanea.LOGGER.warn("Espera: no existe el logro espera/{}", id);
    }

    /** ¿Lo tiene ya entero? (para la prueba). */
    public static boolean tiene(ServerPlayer p, String id) {
        Advancement a = logro(p, id);
        return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
    }

    /** ¿Tiene ese criterio? (para la prueba). */
    public static boolean tiene(ServerPlayer p, String id, String criterio) {
        Advancement a = logro(p, id);
        if (a == null) return false;
        var c = p.getAdvancements().getOrStartProgress(a).getCriterion(criterio);
        return c != null && c.isDone();
    }
}
