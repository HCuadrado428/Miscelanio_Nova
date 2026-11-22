package com.miscelanea.camaras;

import com.miscelanea.ConfigServidor;
import net.minecraftforge.common.ForgeConfigSpec;

/** Sección {@code [camaras]} de la configuración del servidor (la define {@link ConfigServidor}). */
public final class CamarasConfig {
    private CamarasConfig() {}

    private static ForgeConfigSpec.BooleanValue ACTIVADO;
    private static ForgeConfigSpec.IntValue MAX_ACTIVAS, MAX_POR_JUGADOR, CALIDAD;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Cámaras de vigilancia y televisiones (imagen en directo repartida por el servidor).").push("camaras");
        ACTIVADO = b.comment("Si es false, las cámaras no transmiten y las teles solo enseñan 'sin señal'.")
                .define("activadas", true);
        MAX_ACTIVAS = b.comment("Cámaras que pueden estar transmitiendo a la vez en todo el servidor (cada una es un dibujado extra en el ordenador de alguien).")
                .defineInRange("max_activas", 4, 1, 32);
        MAX_POR_JUGADOR = b.comment("Cámaras que puede dibujar a la vez un mismo jugador (la que está más cerca de él transmite primero).")
                .defineInRange("max_por_jugador", 2, 1, 8);
        CALIDAD = b.comment("Calidad de la imagen (en gris, como una cámara de laboratorio): 0 = baja (256x144, unos 40 KB/s por espectador), 1 = media (384x216, unos 90 KB/s), 2 = alta (512x288, unos 150 KB/s).")
                .defineInRange("calidad", 2, 0, 2);
        b.pop();
    }

    private static <T> T leer(ForgeConfigSpec.ConfigValue<T> v) {
        try {
            return ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    public static boolean activado() { return leer(ACTIVADO); }
    public static int maxActivas() { return leer(MAX_ACTIVAS); }
    public static int maxPorJugador() { return leer(MAX_POR_JUGADOR); }
    public static int calidad() { return leer(CALIDAD); }
}
