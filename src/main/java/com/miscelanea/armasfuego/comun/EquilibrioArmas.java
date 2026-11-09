package com.miscelanea.armasfuego.comun;

import com.miscelanea.ConfigServidor;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Equilibrio de las armas de fuego (las tres primeras y las seis de la segunda tanda): daño, cadencia, alcance,
 * capacidad, recarga y dónde se encuentran. Va en la config del servidor, sección {@code armas_de_fuego_equilibrio}
 * (la define {@link ConfigServidor} al final con {@link #definir}).
 * <p>
 * Referencia para el PvP (sin armadura): un jugador tiene 20 de vida; la espada de netherita hace unos 13 por segundo.
 * Ningún disparo suelto llega a 20 (no hay muertes de un tiro a un jugador con la vida llena, salvo la ruleta rusa).
 * Las armas automáticas se saltan la ventana de invulnerabilidad (si no, la mitad de las balas no harían nada), así que
 * su daño por bala es bajo: la Thompson y la Gatling se quedan en 20-25 por segundo, pero gastan munición muy deprisa,
 * dispersan y la Gatling no se mueve y se calienta.
 */
public final class EquilibrioArmas {
    private EquilibrioArmas() {}

    private static ForgeConfigSpec.DoubleValue ENCASQUILLADO_BASE;
    // ---- las tres primeras
    private static ForgeConfigSpec.DoubleValue REVOLVER_DANO, RIFLE_DANO, RIFLE_CABEZA, ESCOPETA_PERDIGON;
    private static ForgeConfigSpec.IntValue REVOLVER_ENFRIAMIENTO;
    // ---- Gatling
    private static ForgeConfigSpec.DoubleValue GATLING_DANO, GATLING_CALOR, GATLING_ENFRIADO, GATLING_ALCANCE;
    private static ForgeConfigSpec.IntValue GATLING_CADENCIA, GATLING_CINTA;
    // ---- Winchester
    private static ForgeConfigSpec.DoubleValue WINCHESTER_DANO, WINCHESTER_ALCANCE;
    private static ForgeConfigSpec.IntValue WINCHESTER_CAPACIDAD, WINCHESTER_PALANCA, WINCHESTER_RECARGA;
    // ---- Derringer
    private static ForgeConfigSpec.DoubleValue DERRINGER_DANO, DERRINGER_ALCANCE, DERRINGER_DESENFUNDE;
    // ---- Thompson
    private static ForgeConfigSpec.DoubleValue THOMPSON_DANO, THOMPSON_ALCANCE;
    private static ForgeConfigSpec.IntValue THOMPSON_CADENCIA, THOMPSON_CAPACIDAD, THOMPSON_RECARGA;
    // ---- Mosquete
    private static ForgeConfigSpec.DoubleValue MOSQUETE_DANO, MOSQUETE_BAYONETA, MOSQUETE_MOJADO;
    private static ForgeConfigSpec.IntValue MOSQUETE_RECARGA;
    // ---- Cañón de mano
    private static ForgeConfigSpec.DoubleValue CANON_DANO, CANON_REVIENTA;
    private static ForgeConfigSpec.IntValue CANON_MECHA, CANON_RECARGA;
    private static ForgeConfigSpec.BooleanValue CANON_ROMPE;
    // ---- botín
    private static ForgeConfigSpec.DoubleValue B_GATLING, B_WINCHESTER, B_DERRINGER, B_THOMPSON, B_MOSQUETE, B_CANON;

    /** Lo llama ConfigServidor al final (su propia sección). */
    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Armas de fuego: equilibrio (daño en puntos de vida: 2 = un corazón; tiempos en ticks: 20 = 1 s).",
                "Ningún disparo suelto llega a 20 (la vida de un jugador). Las automáticas (Thompson y Gatling) se saltan la",
                "ventana de invulnerabilidad: por eso su daño por bala es bajo.").push("armas_de_fuego_equilibrio");
        ENCASQUILLADO_BASE = b.comment("Probabilidad de que cualquier arma de fuego se encasquille en un disparo, aunque esté nueva (el óxido la sube).",
                "Encasquillada: agachado + clic derecho para desatascarla (o el cerrojo/la palanca).").defineInRange("encasquillado_base", 0.01, 0.0, 1.0);
        REVOLVER_DANO = b.comment("Revólver: daño de la bala (antes 7). Desenfunde lo multiplica por 1,75.").defineInRange("revolver_dano", 6.5, 0.0, 100.0);
        REVOLVER_ENFRIAMIENTO = b.comment("Revólver: ticks entre disparos (antes 7).").defineInRange("revolver_enfriamiento", 8, 1, 100);
        ESCOPETA_PERDIGON = b.comment("Escopeta de presión: daño de cada perdigón de grava a 100 de presión (10 perdigones).").defineInRange("escopeta_perdigon", 1.6, 0.0, 100.0);
        RIFLE_DANO = b.comment("Rifle de catalejo: daño de la bala (antes 12; con la respiración contenida, hasta x1,25).").defineInRange("rifle_dano", 9.0, 0.0, 100.0);
        RIFLE_CABEZA = b.comment("Rifle de catalejo: multiplicador en la cabeza (antes 1,75).").defineInRange("rifle_cabeza", 1.6, 1.0, 10.0);

        GATLING_DANO = b.comment("Gatling: daño de cada bala.").defineInRange("gatling_dano", 2.5, 0.0, 100.0);
        GATLING_CADENCIA = b.comment("Gatling: ticks entre balas con la manivela a toda velocidad.").defineInRange("gatling_cadencia", 2, 1, 40);
        GATLING_CINTA = b.comment("Gatling: balas de cada cinta.").defineInRange("gatling_cinta", 40, 1, 1000);
        GATLING_CALOR = b.comment("Gatling: calor por bala (a 100 se atasca y quema).").defineInRange("gatling_calor", 3.5, 0.0, 100.0);
        GATLING_ENFRIADO = b.comment("Gatling: calor que pierde por tick (la mitad mientras dispara).").defineInRange("gatling_enfriado", 0.8, 0.0, 100.0);
        GATLING_ALCANCE = b.comment("Gatling: alcance en bloques.").defineInRange("gatling_alcance", 48.0, 4.0, 256.0);

        WINCHESTER_DANO = b.comment("Winchester: daño de la bala (usa las balas del revólver).").defineInRange("winchester_dano", 7.0, 0.0, 100.0);
        WINCHESTER_ALCANCE = b.comment("Winchester: alcance en bloques.").defineInRange("winchester_alcance", 80.0, 4.0, 256.0);
        WINCHESTER_CAPACIDAD = b.comment("Winchester: balas del depósito.").defineInRange("winchester_capacidad", 12, 1, 100);
        WINCHESTER_PALANCA = b.comment("Winchester: ticks de la palanca entre disparos (a caballo, el giro tarda un 30 % menos).").defineInRange("winchester_palanca", 12, 2, 100);
        WINCHESTER_RECARGA = b.comment("Winchester: ticks por bala al recargar.").defineInRange("winchester_recarga", 8, 1, 100);

        DERRINGER_DANO = b.comment("Derringer: daño de cada tiro a quemarropa (baja a la mitad al final del alcance).").defineInRange("derringer_dano", 7.0, 0.0, 100.0);
        DERRINGER_ALCANCE = b.comment("Derringer: alcance en bloques.").defineInRange("derringer_alcance", 20.0, 2.0, 128.0);
        DERRINGER_DESENFUNDE = b.comment("Derringer: multiplicador del primer tiro justo al sacarla (truco de tahúr).").defineInRange("derringer_desenfunde", 1.4, 1.0, 5.0);

        THOMPSON_DANO = b.comment("Thompson: daño de cada bala.").defineInRange("thompson_dano", 2.0, 0.0, 100.0);
        THOMPSON_ALCANCE = b.comment("Thompson: alcance en bloques.").defineInRange("thompson_alcance", 40.0, 4.0, 256.0);
        THOMPSON_CADENCIA = b.comment("Thompson: ticks entre balas.").defineInRange("thompson_cadencia", 2, 1, 40);
        THOMPSON_CAPACIDAD = b.comment("Thompson: balas del cargador de tambor.").defineInRange("thompson_capacidad", 50, 1, 500);
        THOMPSON_RECARGA = b.comment("Thompson: ticks para cambiar el tambor.").defineInRange("thompson_recarga", 40, 1, 400);

        MOSQUETE_DANO = b.comment("Mosquete: daño de la bola.").defineInRange("mosquete_dano", 15.0, 0.0, 100.0);
        MOSQUETE_BAYONETA = b.comment("Mosquete: daño de la bayoneta (cuerpo a cuerpo, con más alcance).").defineInRange("mosquete_bayoneta", 7.0, 1.0, 100.0);
        MOSQUETE_RECARGA = b.comment("Mosquete: ticks de recarga (pólvora, bola y baqueta).").defineInRange("mosquete_recarga", 70, 1, 600);
        MOSQUETE_MOJADO = b.comment("Mosquete y cañón: probabilidad de fallar con lluvia o en el agua (pólvora mojada).").defineInRange("polvora_mojada", 0.3, 0.0, 1.0);

        CANON_DANO = b.comment("Cañón de mano: daño del impacto directo del bolaño (alrededor, hasta un tercio).").defineInRange("canon_dano", 16.0, 0.0, 100.0);
        CANON_MECHA = b.comment("Cañón de mano: ticks que tarda la mecha.").defineInRange("canon_mecha", 30, 1, 200);
        CANON_RECARGA = b.comment("Cañón de mano: ticks de recarga.").defineInRange("canon_recarga", 50, 1, 600);
        CANON_REVIENTA = b.comment("Cañón de mano: probabilidad de que reviente en las manos.").defineInRange("canon_revienta", 0.05, 0.0, 1.0);
        CANON_ROMPE = b.comment("Cañón de mano: el bolaño rompe bloques al estallar (no por defecto).").define("canon_rompe_bloques", false);

        b.comment("Dónde se encuentran las armas nuevas (probabilidad 0-1 en cada cofre; no tienen receta).").push("botin");
        B_GATLING = b.comment("Gatling: cofres de las pirámides del desierto.").defineInRange("gatling_piramide", 0.2, 0.0, 1.0);
        B_WINCHESTER = b.comment("Winchester: cofres de las minas abandonadas (vagonetas con cofre).").defineInRange("winchester_mina", 0.1, 0.0, 1.0);
        B_DERRINGER = b.comment("Derringer: cofres de los tesoros enterrados.").defineInRange("derringer_tesoro", 0.35, 0.0, 1.0);
        B_THOMPSON = b.comment("Thompson (en su estuche de violín): cofres de las mansiones del bosque.").defineInRange("thompson_mansion", 0.4, 0.0, 1.0);
        B_MOSQUETE = b.comment("Mosquete: cofres de los pasillos y cruces de las fortalezas.").defineInRange("mosquete_fortaleza", 0.15, 0.0, 1.0);
        B_CANON = b.comment("Cañón de mano: cofres de los bastiones (otros y puente).").defineInRange("canon_bastion", 0.15, 0.0, 1.0);
        b.pop();
        b.pop();
    }

    private static double d(ForgeConfigSpec.DoubleValue v) {
        try {
            return ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    private static int i(ForgeConfigSpec.IntValue v) {
        try {
            return ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    private static boolean b(ForgeConfigSpec.BooleanValue v) {
        try {
            return ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    /** La prueba del CI apaga los encasquillados al azar (si no, alguna comprobación fallaría una vez de cada cien). */
    public static volatile boolean sinAtascosPrueba;

    public static float encasquilladoBase() { return sinAtascosPrueba ? 0F : (float) d(ENCASQUILLADO_BASE); }
    public static float revolverDano() { return (float) d(REVOLVER_DANO); }
    public static int revolverEnfriamiento() { return i(REVOLVER_ENFRIAMIENTO); }
    public static float escopetaPerdigon() { return (float) d(ESCOPETA_PERDIGON); }
    public static float rifleDano() { return (float) d(RIFLE_DANO); }
    public static float rifleCabeza() { return (float) d(RIFLE_CABEZA); }

    public static float gatlingDano() { return (float) d(GATLING_DANO); }
    public static int gatlingCadencia() { return i(GATLING_CADENCIA); }
    public static int gatlingCinta() { return i(GATLING_CINTA); }
    public static float gatlingCalor() { return (float) d(GATLING_CALOR); }
    public static float gatlingEnfriado() { return (float) d(GATLING_ENFRIADO); }
    public static double gatlingAlcance() { return d(GATLING_ALCANCE); }

    public static float winchesterDano() { return (float) d(WINCHESTER_DANO); }
    public static double winchesterAlcance() { return d(WINCHESTER_ALCANCE); }
    public static int winchesterCapacidad() { return i(WINCHESTER_CAPACIDAD); }
    public static int winchesterPalanca() { return i(WINCHESTER_PALANCA); }
    public static int winchesterRecarga() { return i(WINCHESTER_RECARGA); }

    public static float derringerDano() { return (float) d(DERRINGER_DANO); }
    public static double derringerAlcance() { return d(DERRINGER_ALCANCE); }
    public static float derringerDesenfunde() { return (float) d(DERRINGER_DESENFUNDE); }

    public static float thompsonDano() { return (float) d(THOMPSON_DANO); }
    public static double thompsonAlcance() { return d(THOMPSON_ALCANCE); }
    public static int thompsonCadencia() { return i(THOMPSON_CADENCIA); }
    public static int thompsonCapacidad() { return i(THOMPSON_CAPACIDAD); }
    public static int thompsonRecarga() { return i(THOMPSON_RECARGA); }

    public static float mosqueteDano() { return (float) d(MOSQUETE_DANO); }
    public static float mosqueteBayoneta() { return (float) d(MOSQUETE_BAYONETA); }
    public static int mosqueteRecarga() { return i(MOSQUETE_RECARGA); }
    public static float polvoraMojada() { return (float) d(MOSQUETE_MOJADO); }

    public static float canonDano() { return (float) d(CANON_DANO); }
    public static int canonMecha() { return i(CANON_MECHA); }
    public static int canonRecarga() { return i(CANON_RECARGA); }
    public static float canonRevienta() { return (float) d(CANON_REVIENTA); }
    public static boolean canonRompe() { return b(CANON_ROMPE); }

    public static double botinGatling() { return d(B_GATLING); }
    public static double botinWinchester() { return d(B_WINCHESTER); }
    public static double botinDerringer() { return d(B_DERRINGER); }
    public static double botinThompson() { return d(B_THOMPSON); }
    public static double botinMosquete() { return d(B_MOSQUETE); }
    public static double botinCanon() { return d(B_CANON); }
}
