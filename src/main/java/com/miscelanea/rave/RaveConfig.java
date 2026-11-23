package com.miscelanea.rave;

import com.miscelanea.ConfigServidor;
import net.minecraftforge.common.ForgeConfigSpec;

/** Sección {@code [rave]} de la configuración del servidor (la define {@link ConfigServidor}). */
public final class RaveConfig {
    private RaveConfig() {}

    private static ForgeConfigSpec.IntValue ALCANCE_BASE, POR_ALTAVOZ, ALCANCE_MAX;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Rave: mesa de DJ y altavoces.").push("rave");
        ALCANCE_BASE = b.comment("Bloques a los que se oye la mesa de DJ sin altavoces.").defineInRange("alcance_base", 32, 8, 256);
        POR_ALTAVOZ = b.comment("Bloques de alcance que suma cada altavoz a menos de 24 bloques de la mesa.").defineInRange("alcance_por_altavoz", 24, 0, 128);
        ALCANCE_MAX = b.comment("Alcance máximo de la música (bloques). Hasta donde los jugadores tengan cargado el chunk de la mesa.")
                .defineInRange("alcance_maximo", 128, 16, 512);
        b.pop();
    }

    private static <T> T leer(ForgeConfigSpec.ConfigValue<T> v) {
        try {
            return ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    public static int alcanceBase() { return leer(ALCANCE_BASE); }
    public static int porAltavoz() { return leer(POR_ALTAVOZ); }
    public static int alcanceMax() { return leer(ALCANCE_MAX); }
}
