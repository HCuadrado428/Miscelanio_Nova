package com.miscelanea.fiesta;

import com.miscelanea.ConfigServidor;
import net.minecraftforge.common.ForgeConfigSpec;

/** Sección {@code [fiesta]} de la configuración del servidor (la define {@link ConfigServidor}). */
public final class FiestaConfig {
    private FiestaConfig() {}

    private static ForgeConfigSpec.BooleanValue ACTIVADA;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("La Fiesta (el juego de tablero con minijuegos, en su propia dimensión).").push("fiesta");
        ACTIVADA = b.comment("Si es false no se puede entrar a la dimensión de la fiesta (y a quien esté dentro se le saca).")
                .define("activada", true);
        b.pop();
    }

    public static boolean activada() {
        try {
            return ConfigServidor.SPEC.isLoaded() ? ACTIVADA.get() : ACTIVADA.getDefault();
        } catch (Exception e) {
            return ACTIVADA.getDefault();
        }
    }
}
