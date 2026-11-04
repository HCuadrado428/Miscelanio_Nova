package com.miscelanea;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuración del servidor (serverconfig/miscelanea-server.toml, una por mundo). Forge la manda sola a los clientes al
 * entrar, así que el cliente también puede leerla (p. ej. para predecir el llenado de la caja de agua).
 * <p>
 * Los valores por defecto son exactamente los que tenía el mod antes de existir este archivo. Mientras el archivo no
 * está cargado (menú principal, arranque) los métodos de lectura devuelven el valor por defecto en vez de fallar.
 */
public final class ConfigServidor {
    private ConfigServidor() {}

    public static final ForgeConfigSpec SPEC;

    // ---- armas
    private static final ForgeConfigSpec.DoubleValue ESCOPETA_PERDIGON, COHETE, BFG_ESFERA, BFG_RAYO, BALLESTA_MULT,
            PORRA_DESCARGA, LANZATINTAS, RODILLO_GOLPE, MAZO_MULT, HACHA_MULT, LLAVE_MULT;
    // ---- tortura
    private static final ForgeConfigSpec.BooleanValue MUERTE_INSTANTANEA, APARATOS_SUPERVIVENCIA;
    // ---- bichos
    private static final ForgeConfigSpec.BooleanValue BICHOS_APARICION;
    private static final ForgeConfigSpec.IntValue BICHOS_INTERVALO, BICHOS_MAX;
    // ---- trampas
    private static final ForgeConfigSpec.IntValue TRAMPA_OSO_S, TRAMPA_OSO_SIN_CAJAS_S, CAJA_AGUA_LLENADO_S,
            SALIDA_S, GANCHO_DANO_DESDE_S, CEPO_S;
    // ---- Dream (mordisco venenoso y anillo de medianoche)
    private static final ForgeConfigSpec.BooleanValue MORDISCO_ACTIVADO, MORDISCO_JUGADORES;
    private static final ForgeConfigSpec.IntValue MORDISCO_WITHER_NIVEL, MORDISCO_WITHER_S, ANILLO_ENFRIAMIENTO_S;
    private static final ForgeConfigSpec.DoubleValue MORDISCO_DANO, MORDISCO_UMBRAL;
    private static final ForgeConfigSpec.ConfigValue<String> ANILLO_DIMENSION;
    // ---- glitch (muestra de glitch)
    private static final ForgeConfigSpec.IntValue GLITCH_RADIO, GLITCH_BLOQUES_TICK;
    private static final ForgeConfigSpec.BooleanValue GLITCH_DESTRUIR, GLITCH_CONTENEDORES;
    private static final ForgeConfigSpec.DoubleValue GLITCH_DANO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Daño de las armas del mod (en puntos de vida: 2 = un corazón).").push("armas");
        ESCOPETA_PERDIGON = b.comment("Superescopeta: daño de cada perdigón a quemarropa (dispara muchos y caen con la distancia).")
                .defineInRange("escopeta_perdigon", 1.75, 0.0, 1000.0);
        COHETE = b.comment("Lanzacohetes: daño del impacto directo del cohete (la explosión hace menos según la distancia).")
                .defineInRange("lanzacohetes_cohete", 14.0, 0.0, 1000.0);
        BFG_ESFERA = b.comment("BFG: daño de la esfera de plasma al estallar (la sobrecargada hace x1,7).")
                .defineInRange("bfg_esfera", 60.0, 0.0, 10000.0);
        BFG_RAYO = b.comment("BFG: daño de cada rayo que suelta la esfera a los que pasan cerca.")
                .defineInRange("bfg_rayo", 7.0, 0.0, 1000.0);
        BALLESTA_MULT = b.comment("Ballesta de repetición: multiplicador del daño de los virotes (1 = como una flecha normal de ballesta).")
                .defineInRange("ballesta_multiplicador", 1.0, 0.0, 100.0);
        PORRA_DESCARGA = b.comment("Porra eléctrica: daño de cada descarga durante el agarre.")
                .defineInRange("porra_descarga", 3.0, 0.0, 1000.0);
        LANZATINTAS = b.comment("Lanzatintas: daño de cada disparo de tinta.")
                .defineInRange("lanzatintas", 3.5, 0.0, 1000.0);
        RODILLO_GOLPE = b.comment("Rodillo de tinta: daño del golpe al pasarlo por encima de alguien.")
                .defineInRange("rodillo_golpe", 8.0, 0.0, 1000.0);
        MAZO_MULT = b.comment("Mazo de Wemmbu: multiplicador de todo su daño (golpe, caída, sismo y lanzamiento).")
                .defineInRange("mazo_multiplicador", 1.0, 0.0, 100.0);
        HACHA_MULT = b.comment("Hacha Leviatán: multiplicador de todo su daño (combos, lanzamientos y rúnicos).")
                .defineInRange("hacha_multiplicador", 1.0, 0.0, 100.0);
        LLAVE_MULT = b.comment("Llave Espada: multiplicador del daño de sus combos.")
                .defineInRange("llave_espada_multiplicador", 1.0, 0.0, 100.0);
        b.pop();

        b.comment("Sala de juegos (aparatos de tortura).").push("tortura");
        MUERTE_INSTANTANEA = b.comment("Con el mod Incapacitated: las muertes de los aparatos y trampas (trampa de oso, doncella, potro,",
                        "silla eléctrica, péndulo, caja de agua, pozo de agujas, alambre de espino...) matan del tirón en vez de dejarte derribado.",
                        "(En las versiones de Incapacitated que usan la etiqueta incapacitated:no_incap, la etiqueta del mod sigue aplicándose.)")
                .define("muerte_instantanea_incapacitated", true);
        APARATOS_SUPERVIVENCIA = b.comment("Si es false, los jugadores en supervivencia/aventura no pueden poner ni usar los aparatos de tortura",
                        "(ni ponerle a nadie grilletes, trampas, collares...). En creativo se sigue pudiendo, para montar escenas;",
                        "quien ya está atrapado puede seguir intentando escapar (ganzúas, llaves, pozo, tenazas...).")
                .define("aparatos_en_supervivencia", true);
        b.pop();

        b.comment("Bichos (insectos que aparecen alrededor de los jugadores, como en Animal Crossing).").push("bichos");
        BICHOS_APARICION = b.comment("Si aparecen bichos solos alrededor de los jugadores.")
                .define("aparicion_natural", true);
        BICHOS_INTERVALO = b.comment("Cada cuántos ticks se intenta hacer aparecer un bicho junto a cada jugador (20 ticks = 1 s; menos = más bichos).")
                .defineInRange("intervalo_ticks", 30, 1, 72000);
        BICHOS_MAX = b.comment("Máximo de bichos alrededor de un jugador (36 bloques) para que aparezcan más.")
                .defineInRange("max_cerca", 7, 0, 100);
        b.pop();

        b.comment("Tiempos de las trampas y juegos (en segundos).").push("trampas");
        TRAMPA_OSO_S = b.comment("Trampa de oso: segundos para encontrar la llave antes de que se dispare (con cajas de Jigsaw cerca).")
                .defineInRange("trampa_oso_segundos", 60, 5, 3600);
        TRAMPA_OSO_SIN_CAJAS_S = b.comment("Trampa de oso: segundos si no hay cajas de Jigsaw cerca (la llave hay que buscarla de otra forma).")
                .defineInRange("trampa_oso_sin_cajas_segundos", 90, 5, 3600);
        CAJA_AGUA_LLENADO_S = b.comment("Caja de agua: segundos que tarda en llenarse del todo.")
                .defineInRange("caja_agua_llenado_segundos", 40, 2, 3600);
        SALIDA_S = b.comment("Puerta de salida: tiempo por defecto de la cuenta atrás de una puerta recién puesta (se cambia luego con clic derecho).")
                .defineInRange("salida_segundos", 120, 30, 900);
        GANCHO_DANO_DESDE_S = b.comment("Gancho: segundos colgado boca abajo antes de que la sangre en la cabeza empiece a hacer daño.")
                .defineInRange("gancho_dano_desde_segundos", 60, 1, 3600);
        CEPO_S = b.comment("Cepo (picota): segundos que dura el castigo.")
                .defineInRange("cepo_segundos", 60, 5, 3600);
        b.pop();

        b.comment("Dream: el mordisco venenoso (efecto de la esencia de Dream) y el anillo de medianoche.").push("dream");
        MORDISCO_ACTIVADO = b.comment("Si quien tiene el efecto «Mordisco venenoso» puede agarrar y morder con la mano vacía.")
                .define("mordisco_activado", true);
        MORDISCO_JUGADORES = b.comment("Si se puede morder a otros jugadores (además de respetar el PvP del servidor y los equipos).")
                .define("mordisco_a_jugadores", true);
        MORDISCO_WITHER_NIVEL = b.comment("Nivel del Wither de cada mordisco (0 = Wither I, 1 = Wither II...).")
                .defineInRange("mordisco_wither_nivel", 1, 0, 4);
        MORDISCO_WITHER_S = b.comment("Segundos de Wither que deja cada mordisco (muerde cada 1,5 s).")
                .defineInRange("mordisco_wither_segundos", 4, 1, 60);
        MORDISCO_DANO = b.comment("Daño directo de cada mordisco, aparte del Wither (sirve también contra no muertos, que no sufren Wither).")
                .defineInRange("mordisco_dano", 1.0, 0.0, 100.0);
        MORDISCO_UMBRAL = b.comment("Daño de un solo golpe que, recibido por quien muerde, le obliga a soltar a su presa.")
                .defineInRange("mordisco_umbral_soltar", 3.0, 0.0, 1000.0);
        ANILLO_DIMENSION = b.comment("Dimensión a la que lleva el anillo de medianoche (la de «The Midnight» de Cryptic Mushroom).")
                .define("anillo_dimension", "midnight:midnight");
        ANILLO_ENFRIAMIENTO_S = b.comment("Segundos de espera del anillo de medianoche después de viajar.")
                .defineInRange("anillo_enfriamiento_segundos", 10, 0, 3600);
        b.pop();

        b.comment("El glitch: la muestra de glitch (solo de creativo) que, al soltarse o romperse, corrompe una esfera de bloques.").push("glitch");
        GLITCH_RADIO = b.comment("Radio en bloques de la esfera de corrupción.")
                .defineInRange("radio", 4, 1, 12);
        GLITCH_DESTRUIR = b.comment("Si es false el efecto es solo visual: los bloques se glitchean un momento y luego vuelven a ser lo que eran",
                        "(y los objetos tirados parpadean pero no se borran). Si es true, los bloques glitcheados desaparecen.")
                .define("destruir_bloques", true);
        GLITCH_CONTENEDORES = b.comment("Si la onda puede borrar bloques con datos (cofres, hornos, tolvas, carteles...). Solo con destruir_bloques = true;",
                        "lo que lleven dentro cae al suelo (y si la onda lo pilla, se borra).")
                .define("afectar_contenedores", false);
        GLITCH_DANO = b.comment("Daño que hace la onda a cada ser vivo que pilla (además de náuseas, oscuridad y el efecto Glitch). No borra a nadie.")
                .defineInRange("dano", 2.0, 0.0, 100.0);
        GLITCH_BLOQUES_TICK = b.comment("Máximo de bloques que se glitchean por tick en cada mundo (entre todas las ondas), para no dar lag.")
                .defineInRange("bloques_por_tick", 160, 8, 4096);
        b.pop();

        SPEC = b.build();
    }

    // ------------------------------------------------------------------ lectura segura
    private static double d(ForgeConfigSpec.DoubleValue v) {
        try {
            return SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    private static int i(ForgeConfigSpec.IntValue v) {
        try {
            return SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    private static boolean b(ForgeConfigSpec.BooleanValue v) {
        try {
            return SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    private static String s(ForgeConfigSpec.ConfigValue<String> v) {
        try {
            return SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    // ------------------------------------------------------------------ armas
    public static float escopetaPerdigon() { return (float) d(ESCOPETA_PERDIGON); }
    public static float cohete() { return (float) d(COHETE); }
    public static float bfgEsfera() { return (float) d(BFG_ESFERA); }
    public static float bfgRayo() { return (float) d(BFG_RAYO); }
    public static double ballestaMult() { return d(BALLESTA_MULT); }
    public static float porraDescarga() { return (float) d(PORRA_DESCARGA); }
    public static float lanzatintas() { return (float) d(LANZATINTAS); }
    public static float rodilloGolpe() { return (float) d(RODILLO_GOLPE); }
    public static float mazoMult() { return (float) d(MAZO_MULT); }
    public static float hachaMult() { return (float) d(HACHA_MULT); }
    public static float llaveMult() { return (float) d(LLAVE_MULT); }

    // ------------------------------------------------------------------ tortura
    public static boolean muerteInstantanea() { return b(MUERTE_INSTANTANEA); }
    public static boolean aparatosEnSupervivencia() { return b(APARATOS_SUPERVIVENCIA); }

    // ------------------------------------------------------------------ bichos
    public static boolean bichosAparicion() { return b(BICHOS_APARICION); }
    public static int bichosIntervalo() { return Math.max(1, i(BICHOS_INTERVALO)); }
    public static int bichosMax() { return i(BICHOS_MAX); }

    // ------------------------------------------------------------------ trampas (en ticks)
    public static int trampaOsoTicks() { return i(TRAMPA_OSO_S) * 20; }
    public static int trampaOsoSinCajasTicks() { return i(TRAMPA_OSO_SIN_CAJAS_S) * 20; }
    public static int cajaAguaLlenadoTicks() { return Math.max(1, i(CAJA_AGUA_LLENADO_S) * 20); }
    public static int salidaSegundos() { return i(SALIDA_S); }
    public static int ganchoDanoDesdeTicks() { return Math.max(20, i(GANCHO_DANO_DESDE_S) * 20); }
    public static int cepoTicks() { return i(CEPO_S) * 20; }

    // ------------------------------------------------------------------ Dream
    public static boolean mordiscoActivado() { return b(MORDISCO_ACTIVADO); }
    public static boolean mordiscoJugadores() { return b(MORDISCO_JUGADORES); }
    public static int mordiscoWitherNivel() { return i(MORDISCO_WITHER_NIVEL); }
    public static int mordiscoWitherTicks() { return Math.max(20, i(MORDISCO_WITHER_S) * 20); }
    public static float mordiscoDano() { return (float) d(MORDISCO_DANO); }
    public static float mordiscoUmbral() { return (float) d(MORDISCO_UMBRAL); }
    public static String anilloDimension() { return s(ANILLO_DIMENSION); }
    public static int anilloEnfriamientoTicks() { return i(ANILLO_ENFRIAMIENTO_S) * 20; }

    // ------------------------------------------------------------------ glitch
    public static int glitchRadio() { return i(GLITCH_RADIO); }
    public static boolean glitchDestruirBloques() { return b(GLITCH_DESTRUIR); }
    public static boolean glitchAfectarContenedores() { return b(GLITCH_CONTENEDORES); }
    public static float glitchDano() { return (float) d(GLITCH_DANO); }
    public static int glitchBloquesPorTick() { return Math.max(1, i(GLITCH_BLOQUES_TICK)); }
}
