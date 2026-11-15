package com.miscelanea.sangre;

import net.minecraftforge.common.ForgeConfigSpec;

/** Sección {@code [hacha_sangre]} de la configuración del servidor. La define {@link com.miscelanea.ConfigServidor} llamando a {@link #definir}. */
public final class HachaSangreConfig {
    private HachaSangreConfig() {}

    private static ForgeConfigSpec.IntValue UMBRAL1, UMBRAL2, UMBRAL3, UMBRAL4, DEPOSITO_OLVIDO_S, SIEGA_ENF_S, CHARCO_S, LIBRE_S, SINERGIA;
    private static ForgeConfigSpec.DoubleValue DIEZMO_EXTRA, DIEZMO_COSTE, DIEZMO_CURA, SIEGA_DANO, SIEGA_RADIO, CHARCO_RADIO, CHARCO_CURA;
    private static ForgeConfigSpec.BooleanValue TOPE_JUGADORES;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Hacha de sangre: bebe de lo que golpea y despierta por fases.").push("hacha_sangre");
        UMBRAL1 = b.comment("Daño que tiene que beber para despertar la fase 1 (sedienta: guarda sangre y suelta la Siega).")
                .defineInRange("fase1_dano_bebido", 120, 1, 100000);
        UMBRAL2 = b.comment("Daño bebido para la fase 2 (cobradora: el Diezmo).").defineInRange("fase2_dano_bebido", 420, 1, 100000);
        UMBRAL3 = b.comment("Daño bebido para la fase 3 (viva: charcos de sangre).").defineInRange("fase3_dano_bebido", 900, 1, 100000);
        UMBRAL4 = b.comment("Daño bebido para la fase 4 (insaciable: Diezmo gratis cada cierto tiempo y charcos que dan velocidad).")
                .defineInRange("fase4_dano_bebido", 2000, 1, 100000);
        DEPOSITO_OLVIDO_S = b.comment("Segundos sin beber hasta que el depósito empieza a vaciarse.").defineInRange("segundos_sin_beber", 30, 5, 3600);
        DIEZMO_EXTRA = b.comment("Diezmo: daño extra del golpe cobrado, en múltiplos del golpe base (2 = el golpe vale por tres).")
                .defineInRange("diezmo_extra", 2.0, 0.0, 20.0);
        DIEZMO_COSTE = b.comment("Diezmo: vida que cuesta pagarlo (4 = dos corazones).").defineInRange("diezmo_coste", 4.0, 0.0, 20.0);
        DIEZMO_CURA = b.comment("Diezmo: vida que recupera quien mata con él.").defineInRange("diezmo_cura", 8.0, 0.0, 40.0);
        SIEGA_DANO = b.comment("Siega: daño del giro a cada criatura de alrededor.").defineInRange("siega_dano", 9.0, 0.0, 200.0);
        SIEGA_RADIO = b.comment("Siega: radio del giro en bloques.").defineInRange("siega_radio", 4.0, 1.0, 16.0);
        SIEGA_ENF_S = b.comment("Siega: segundos de espera.").defineInRange("siega_enfriamiento_segundos", 3, 0, 600);
        CHARCO_RADIO = b.comment("Charco de sangre: radio.").defineInRange("charco_radio", 2.0, 0.5, 8.0);
        CHARCO_S = b.comment("Charco de sangre: segundos que dura.").defineInRange("charco_segundos", 8, 1, 120);
        CHARCO_CURA = b.comment("Charco de sangre: vida que cura cada medio segundo a su dueño mientras lo pisa.")
                .defineInRange("charco_cura", 0.5, 0.0, 10.0);
        LIBRE_S = b.comment("Fase 4: segundos entre dos Diezmos gratis.").defineInRange("diezmo_gratis_segundos", 60, 5, 3600);
        SINERGIA = b.comment("Multiplicador de lo que bebe contra alguien con el efecto Desangrado (1 = sin sinergia).")
                .defineInRange("sinergia_desangrado", 2, 1, 10);
        TOPE_JUGADORES = b.comment("Ningún golpe extra (Diezmo, Siega) mata de un tiro a un jugador con la vida llena.")
                .define("tope_jugadores", true);
        b.pop();
    }

    private static int i(ForgeConfigSpec.IntValue v) {
        try { return v.get(); } catch (IllegalStateException e) { return v.getDefault(); }
    }

    private static double d(ForgeConfigSpec.DoubleValue v) {
        try { return v.get(); } catch (IllegalStateException e) { return v.getDefault(); }
    }

    /** Daño bebido para despertar la fase n (1 a 4). */
    public static int umbral(int n) { return switch (n) { case 1 -> i(UMBRAL1); case 2 -> i(UMBRAL2); case 3 -> i(UMBRAL3); default -> i(UMBRAL4); }; }
    public static int olvidoTicks() { return i(DEPOSITO_OLVIDO_S) * 20; }
    public static float diezmoExtra() { return (float) d(DIEZMO_EXTRA); }
    public static float diezmoCoste() { return (float) d(DIEZMO_COSTE); }
    public static float diezmoCura() { return (float) d(DIEZMO_CURA); }
    public static float siegaDano() { return (float) d(SIEGA_DANO); }
    public static double siegaRadio() { return d(SIEGA_RADIO); }
    public static int siegaEnfriamientoTicks() { return i(SIEGA_ENF_S) * 20; }
    public static float charcoRadio() { return (float) d(CHARCO_RADIO); }
    public static int charcoTicks() { return i(CHARCO_S) * 20; }
    public static float charcoCura() { return (float) d(CHARCO_CURA); }
    public static long libreTicks() { return i(LIBRE_S) * 20L; }
    public static int sinergia() { return i(SINERGIA); }
    public static boolean topeJugadores() {
        try { return TOPE_JUGADORES.get(); } catch (IllegalStateException e) { return true; }
    }
}
