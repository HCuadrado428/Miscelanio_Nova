package com.miscelanea.espera;

import com.miscelanea.ConfigServidor;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Sección {@code [espera]} de la configuración del servidor. La define {@link ConfigServidor} llamando a
 * {@link #definir} (así los valores viven aquí y el archivo compartido solo tiene una línea). Mientras el archivo no
 * está cargado se usan los valores por defecto.
 */
public final class EsperaConfig {
    private EsperaConfig() {}

    private static ForgeConfigSpec.BooleanValue ACTIVADO, VACIO;
    private static ForgeConfigSpec.DoubleValue PARED;
    private static ForgeConfigSpec.IntValue POR_JUGADOR, MAX_TOTAL;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("El Espera: la dimensión adonde van los errores pacientes (se llega colándose por una pared o cayéndose del mapa).").push("espera");
        ACTIVADO = b.comment("Si es false no se puede llegar al Espera, no salen habitantes y a quien esté dentro se le saca",
                        "(vuelve a donde estaba). La dimensión sigue existiendo, pero vacía.")
                .define("activado", true);
        PARED = b.comment("Probabilidad (0-1) de colarse por la pared cada vez que hace daño estar dentro de un bloque (cada medio segundo).",
                        "0,12 = de media al cuarto golpe de asfixia. 0 = nunca.")
                .defineInRange("probabilidad_pared", 0.12, 0.0, 1.0);
        VACIO = b.comment("Si caerse al vacío del mundo normal (por debajo del fondo del mapa) lleva al Espera en vez de matar.")
                .define("entrar_al_caer_al_vacio", true);
        POR_JUGADOR = b.comment("Habitantes como mucho cerca de cada jugador (a 64 bloques). 0 = no salen.")
                .defineInRange("habitantes_por_jugador", 2, 0, 8);
        MAX_TOTAL = b.comment("Habitantes como mucho en todo el Espera a la vez (sumando los de todos los jugadores).")
                .defineInRange("max_habitantes", 12, 0, 64);
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

    /** Para la prueba: apaga el Espera sin tocar el archivo (null = lo que diga la configuración). */
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
    public static int habitantesPorJugador() { return leer(POR_JUGADOR); }
    public static int maxHabitantes() { return leer(MAX_TOTAL); }
}
