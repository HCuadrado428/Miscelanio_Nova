package com.miscelanea.error404;

import com.miscelanea.ConfigServidor;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Sección {@code [error404]} de la configuración del servidor. La define {@link ConfigServidor} llamando a
 * {@link #definir} (así los valores viven aquí y el archivo compartido solo tiene una línea). Mientras el archivo no
 * está cargado se usan los valores por defecto.
 */
public final class Error404Config {
    private Error404Config() {}

    private static ForgeConfigSpec.BooleanValue ACTIVADO, VACIO, CAMBIOS;
    private static ForgeConfigSpec.DoubleValue PARED;
    private static ForgeConfigSpec.IntValue CAMBIOS_MIN, CAMBIOS_MAX;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("La 404: el espacio liminal sin fin (se llega colándose por una pared o cayéndose del mapa).").push("404");
        ACTIVADO = b.comment("Si es false no se puede llegar a la 404, y a quien esté dentro se le saca",
                        "(vuelve a donde estaba). La dimensión sigue existiendo, pero vacía.")
                .define("activado", true);
        PARED = b.comment("Probabilidad (0-1) de colarse por la pared cada vez que hace daño estar dentro de un bloque (cada medio segundo).",
                        "0,12 = de media al cuarto golpe de asfixia. 0 = nunca.")
                .defineInRange("probabilidad_pared", 0.12, 0.0, 1.0);
        VACIO = b.comment("Si caerse al vacío del mundo normal (por debajo del fondo del mapa) lleva a la 404 en vez de matar.")
                .define("entrar_al_caer_al_vacio", true);
        CAMBIOS = b.comment("Si false, nada cambia a tus espaldas (relojes, números, paredes, puertas y luces) mientras no miras.")
                .define("cambios_a_la_espalda", true);
        CAMBIOS_MIN = b.comment("Segundos mínimos entre un cambio a la espalda de un jugador y el siguiente.")
                .defineInRange("cambios_segundos_min", 25, 5, 600);
        CAMBIOS_MAX = b.comment("Segundos máximos entre un cambio a la espalda de un jugador y el siguiente.")
                .defineInRange("cambios_segundos_max", 70, 10, 1200);
        b.pop();
    }

    private static boolean cargado() {
        try {
            return ConfigServidor.SPEC.isLoaded();
        } catch (Exception e) {
            return false;
        }
    }

    private static <T> T leer(ForgeConfigSpec.ConfigValue<T> v) {
        try {
            return cargado() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    /** Para la prueba: apaga la 404 sin tocar el archivo (null = lo que diga la configuración). */
    private static volatile Boolean forzado;

    public static void paraPrueba(boolean activado) {
        forzado = activado ? null : Boolean.FALSE;
    }

    public static boolean activado() {
        Boolean f = forzado;
        return f != null ? f : leer(ACTIVADO);
    }
    public static double probabilidadPared() { return leer(PARED); }
    public static boolean entrarAlCaer() { return leer(VACIO); }
    public static boolean cambios() { return leer(CAMBIOS); }
    public static int cambiosMin() { return leer(CAMBIOS_MIN); }
    public static int cambiosMax() { return Math.max(leer(CAMBIOS_MAX), leer(CAMBIOS_MIN)); }
}
