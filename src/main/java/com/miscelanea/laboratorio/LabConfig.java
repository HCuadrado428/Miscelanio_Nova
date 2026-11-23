package com.miscelanea.laboratorio;

import com.miscelanea.ConfigServidor;
import net.minecraftforge.common.ForgeConfigSpec;

/** Sección {@code [laboratorio]} de la configuración del servidor (la define {@link ConfigServidor}). */
public final class LabConfig {
    private LabConfig() {}

    private static ForgeConfigSpec.BooleanValue ACTIVADO;
    private static ForgeConfigSpec.IntValue NIVEL_MAX, DANO_EXTREMO;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("El laboratorio de experimentos (zona de torturas).").push("laboratorio");
        ACTIVADO = b.comment("Si es false las estaciones del laboratorio no funcionan (los objetos de medir sí).")
                .define("estaciones_activadas", true);
        NIVEL_MAX = b.comment("Nivel máximo que permiten las estaciones: 0 = solo suave (cómico), 1 = hasta intenso, 2 = hasta extremo (con daño de verdad).")
                .defineInRange("nivel_maximo", 2, 0, 2);
        DANO_EXTREMO = b.comment("Daño (en corazones x2, o sea, puntos de vida) del nivel extremo de la centrífuga al salir disparado. 0 = sin daño.")
                .defineInRange("dano_extremo", 14, 0, 40);
        b.pop();
    }

    private static <T> T leer(ForgeConfigSpec.ConfigValue<T> v) {
        try {
            return ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    public static boolean estaciones() { return leer(ACTIVADO); }
    public static int nivelMax() { return leer(NIVEL_MAX); }
    public static int danoExtremo() { return leer(DANO_EXTREMO); }
}
