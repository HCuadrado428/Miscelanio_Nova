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
    // ---- Cerbatana
    private static ForgeConfigSpec.DoubleValue CERBATANA_DANO, CERBATANA_ALCANCE;
    // ---- Lanzallamas
    private static ForgeConfigSpec.DoubleValue LANZALLAMAS_DANO, LANZALLAMAS_ALCANCE;
    private static ForgeConfigSpec.IntValue LANZALLAMAS_CAPACIDAD;
    // ---- Pistola de resonancia
    private static ForgeConfigSpec.DoubleValue RESONANCIA_DANO, RESONANCIA_ALCANCE;
    // ---- Arponera
    private static ForgeConfigSpec.DoubleValue ARPONERA_DANO, ARPONERA_ALCANCE, ARPONERA_TIRON;
    // ---- Thompson
    private static ForgeConfigSpec.DoubleValue THOMPSON_DANO, THOMPSON_ALCANCE;
    private static ForgeConfigSpec.IntValue THOMPSON_CADENCIA, THOMPSON_CAPACIDAD, THOMPSON_RECARGA;
    // ---- Mosquete
    private static ForgeConfigSpec.DoubleValue MOSQUETE_DANO, MOSQUETE_BAYONETA, MOSQUETE_MOJADO;
    private static ForgeConfigSpec.IntValue MOSQUETE_RECARGA;
    // ---- Cañón de mano
    private static ForgeConfigSpec.DoubleValue CANON_DANO, CANON_REVIENTA;
    private static ForgeConfigSpec.IntValue CANON_MECHA, CANON_RECARGA;
    private static ForgeConfigSpec.BooleanValue CANON_ROMPE, CANON_COLOCA, GATLING_REDSTONE;
    // ---- lo nuevo de 1.1.0 (utilidad, manga, violín, bayonetas, bloques)
    private static ForgeConfigSpec.DoubleValue GATLING_TORRETA_ALCANCE, MOSQUETE_ARPON, CANON_YUNQUE, CANON_BLOQUE;
    private static ForgeConfigSpec.IntValue WINCHESTER_HIELO, DERRINGER_MANGA, VIOLIN_ENFRIAMIENTO;
    // ---- el cobre de las nueve: lo que hace cada fase (nueva, expuesta, erosionada, oxidada) y lo que dura la cera
    private static ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> OX_ATASCO, OX_DISPERSION, OX_DANO, OX_PERFORACION;
    private static ForgeConfigSpec.IntValue CERA, CERA_AUTOMATICAS;
    // ---- apuntar (clic derecho mantenido; todas menos la escopeta de presión)
    private static ForgeConfigSpec.DoubleValue APUNTAR_DISPERSION, APUNTAR_FOV, APUNTAR_LENTITUD;
    // ---- botín
    private static ForgeConfigSpec.DoubleValue B_GATLING, B_WINCHESTER, B_DERRINGER, B_THOMPSON, B_MOSQUETE, B_CANON, B_CERBATANA, B_LANZALLAMAS, B_RESONANCIA, B_ARPONERA;

    /** Lo llama ConfigServidor al final (su propia sección). */
    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Armas de fuego: equilibrio (daño en puntos de vida: 2 = un corazón; tiempos en ticks: 20 = 1 s).",
                "Ningún disparo suelto llega a 20 (la vida de un jugador). Las automáticas (Thompson y Gatling) se saltan la",
                "ventana de invulnerabilidad: por eso su daño por bala es bajo.").push("armas_de_fuego_equilibrio");
        b.comment("El cobre de las nueve armas: cada fase es un intercambio. Valores para [nueva, expuesta, erosionada, oxidada].",
                "La nueva es precisa y fiable con el daño normal; la oxidada (cañón gastado y basto) se encasquilla y dispersa más, pero pega más y",
                "perfora más armadura.").push("cobre");
        java.util.function.Predicate<Object> num = o -> o instanceof Number;
        OX_ATASCO = b.comment("Encasquillado que añade cada fase (0-1, sobre el de base).").defineList("encasquillado", java.util.List.of(0.0, 0.01, 0.025, 0.05), num);
        OX_DISPERSION = b.comment("Dispersión que añade cada fase (radianes).").defineList("dispersion", java.util.List.of(0.0, 0.005, 0.011, 0.02), num);
        OX_DANO = b.comment("Multiplicador del daño de cada fase.").defineList("dano", java.util.List.of(1.0, 1.05, 1.1, 1.15), num);
        OX_PERFORACION = b.comment("Parte del daño que se salta la armadura que añade cada fase (0-1; 0,1 = 10 puntos).").defineList("perforacion", java.util.List.of(0.0, 0.03, 0.06, 0.1), num);
        CERA = b.comment("Disparos que dura la cera de un panal (luego vuelve a oxidarse; volver a encerar la rellena).").defineInRange("cera_disparos", 200, 1, 100000);
        CERA_AUTOMATICAS = b.comment("Lo mismo para la Gatling y la Thompson (disparan muchísimo más).").defineInRange("cera_disparos_automaticas", 1500, 1, 1000000);
        b.pop();
        b.comment("Apuntar: mantener el clic derecho con un arma en la mano (todas menos la escopeta de presión, que dispara solo desde la cadera).",
                "El rifle apunta con su catalejo (su zoom es el del catalejo) y solo usa la dispersión de aquí.").push("apuntado");
        APUNTAR_DISPERSION = b.comment("Por cuánto se multiplica la dispersión al apuntar (1 = igual que desde la cadera; 0,4 = un 60 % menos).").defineInRange("dispersion", 0.4, 0.0, 1.0);
        APUNTAR_FOV = b.comment("Por cuánto se multiplica el campo de visión al apuntar (1 = sin zoom; 0,8 = se cierra un 20 %).").defineInRange("fov", 0.8, 0.3, 1.0);
        APUNTAR_LENTITUD = b.comment("Cuánto más lento se anda apuntando (0 = igual; 0,35 = un 35 % más lento).").defineInRange("lentitud", 0.35, 0.0, 0.9);
        b.pop();
        ENCASQUILLADO_BASE = b.comment("Probabilidad de que cualquier arma de fuego se encasquille en un disparo, aunque esté nueva (el óxido la sube).",
                "Encasquillada: agachado + clic derecho para desatascarla (o el cerrojo/la palanca).").defineInRange("encasquillado_base", 0.01, 0.0, 1.0);
        REVOLVER_DANO = b.comment("Revólver: daño de la bala (antes 7). Desenfunde lo multiplica por 1,75.").defineInRange("revolver_dano", 6.5, 0.0, 100.0);
        REVOLVER_ENFRIAMIENTO = b.comment("Revólver: ticks entre disparos (10 = la ventana de invulnerabilidad: cuentan todos los tiros).").defineInRange("revolver_enfriamiento", 10, 1, 100);
        ESCOPETA_PERDIGON = b.comment("Escopeta de presión: daño de cada perdigón de grava a 100 de presión (10 perdigones).").defineInRange("escopeta_perdigon", 1.5, 0.0, 100.0);
        RIFLE_DANO = b.comment("Rifle de catalejo: daño de la bala (antes 12; con la respiración contenida, hasta x1,25).").defineInRange("rifle_dano", 9.0, 0.0, 100.0);
        RIFLE_CABEZA = b.comment("Rifle de catalejo: multiplicador en la cabeza (con 1,5 ni el cañón oxidado con Respiración III mata de un tiro).").defineInRange("rifle_cabeza", 1.5, 1.0, 10.0);

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

        THOMPSON_DANO = b.comment("Thompson: daño de cada bala.").defineInRange("thompson_dano", 1.6, 0.0, 100.0);
        THOMPSON_ALCANCE = b.comment("Thompson: alcance en bloques.").defineInRange("thompson_alcance", 40.0, 4.0, 256.0);
        THOMPSON_CADENCIA = b.comment("Thompson: ticks entre balas.").defineInRange("thompson_cadencia", 2, 1, 40);
        THOMPSON_CAPACIDAD = b.comment("Thompson: balas del cargador de tambor.").defineInRange("thompson_capacidad", 50, 1, 500);
        THOMPSON_RECARGA = b.comment("Thompson: ticks para cambiar el tambor.").defineInRange("thompson_recarga", 40, 1, 400);

        MOSQUETE_DANO = b.comment("Mosquete: daño de la bola.").defineInRange("mosquete_dano", 15.0, 0.0, 100.0);
        MOSQUETE_BAYONETA = b.comment("Mosquete: daño que la bayoneta añade al de la espada calada (con la de hierro, 6 + 1 = 7; cuerpo a cuerpo y con más alcance).",
                "La durabilidad es la de la espada: se gasta la espada calada y, si se rompe, el mosquete se queda sin bayoneta.").defineInRange("mosquete_bayoneta_extra", 1.0, 0.0, 100.0);
        MOSQUETE_RECARGA = b.comment("Mosquete: ticks de recarga (pólvora, bola y baqueta).").defineInRange("mosquete_recarga", 70, 1, 600);
        MOSQUETE_MOJADO = b.comment("Mosquete y cañón: probabilidad de fallar con lluvia o en el agua (pólvora mojada).").defineInRange("polvora_mojada", 0.3, 0.0, 1.0);

        CANON_DANO = b.comment("Cañón de mano: daño del impacto directo del bolaño (alrededor, hasta un tercio).").defineInRange("canon_dano", 16.0, 0.0, 100.0);
        CANON_MECHA = b.comment("Cañón de mano: ticks que tarda la mecha.").defineInRange("canon_mecha", 30, 1, 200);
        CANON_RECARGA = b.comment("Cañón de mano: ticks de recarga.").defineInRange("canon_recarga", 50, 1, 600);
        CANON_REVIENTA = b.comment("Cañón de mano: probabilidad de que reviente en las manos.").defineInRange("canon_revienta", 0.05, 0.0, 1.0);
        CANON_ROMPE = b.comment("Cañón de mano: el bolaño rompe bloques al estallar (no por defecto).").define("canon_rompe_bloques", false);
        CANON_COLOCA = b.comment("Cañón de mano: los bloques que caen disparados (arena, grava, hormigón en polvo, yunque...) se quedan donde dan, como un",
                "bloque que cae. Con false (para servidores) caen como objeto y no se coloca nada. También respeta la protección de cada sitio.").define("canon_coloca_bloques", true);
        CANON_BLOQUE = b.comment("Cañón de mano: daño de un bloque que cae disparado (arena, grava...) respecto al del bolaño.").defineInRange("canon_bloque", 0.6, 0.0, 10.0);
        CANON_YUNQUE = b.comment("Cañón de mano: daño de un yunque disparado (daño de yunque) respecto al del bolaño.").defineInRange("canon_yunque", 1.25, 0.0, 10.0);
        GATLING_REDSTONE = b.comment("Gatling: con señal de redstone y nadie a los mandos, la manivela gira sola y dispara hacia donde apunta (torreta).").define("gatling_redstone", true);
        GATLING_TORRETA_ALCANCE = b.comment("Gatling con Torreta: distancia a la que busca sola lo hostil.").defineInRange("gatling_torreta_alcance", 24.0, 2.0, 128.0);
        WINCHESTER_HIELO = b.comment("Winchester: ticks que congela la bala de hielo.").defineInRange("winchester_hielo", 140, 0, 1200);
        DERRINGER_MANGA = b.comment("Derringer: ticks de espera para volver a sacarla de la manga.").defineInRange("derringer_manga", 30, 0, 1200);
        MOSQUETE_ARPON = b.comment("Mosquete: fuerza del tirón de la bola arpón.").defineInRange("mosquete_arpon", 1.4, 0.0, 5.0);
        VIOLIN_ENFRIAMIENTO = b.comment("Estuche de violín: ticks entre nota y nota.").defineInRange("violin_enfriamiento", 3, 0, 40);

        b.comment("Dónde se encuentran las armas nuevas (probabilidad 0-1 en cada cofre; no tienen receta).").push("botin");
        CERBATANA_DANO = b.comment("Cerbatana: daño del dardo normal (el de veneno hace el 70 % y el somnífero el 35 %).").defineInRange("cerbatana_dano", 3.0, 0.0, 100.0);
        CERBATANA_ALCANCE = b.comment("Cerbatana: alcance en bloques.").defineInRange("cerbatana_alcance", 40.0, 2.0, 128.0);

        LANZALLAMAS_DANO = b.comment("Lanzallamas: daño de cada pulsación de llama (sale cada 2 ticks) a todo lo que cae en el cono, además de prenderlo.").defineInRange("lanzallamas_dano", 1.5, 0.0, 100.0);
        LANZALLAMAS_ALCANCE = b.comment("Lanzallamas: alcance del chorro en bloques.").defineInRange("lanzallamas_alcance", 7.0, 2.0, 32.0);
        LANZALLAMAS_CAPACIDAD = b.comment("Lanzallamas: pulsaciones de llama que da un bidón (cada una gasta una; 2 ticks).").defineInRange("lanzallamas_capacidad", 100, 10, 1000);

        RESONANCIA_DANO = b.comment("Pistola de resonancia: daño de la onda a todo lo que cruza (la armadura casi no la frena; atraviesa bloques).").defineInRange("resonancia_dano", 9.0, 0.0, 100.0);
        RESONANCIA_ALCANCE = b.comment("Pistola de resonancia: alcance de la onda en bloques.").defineInRange("resonancia_alcance", 24.0, 2.0, 128.0);

        ARPONERA_DANO = b.comment("Arponera: daño del arpón a una criatura (tira de ella hacia ti).").defineInRange("arponera_dano", 8.0, 0.0, 100.0);
        ARPONERA_ALCANCE = b.comment("Arponera: alcance de la cuerda en bloques.").defineInRange("arponera_alcance", 36.0, 2.0, 128.0);
        ARPONERA_TIRON = b.comment("Arponera: fuerza del tirón (bajo el agua, x1,6).").defineInRange("arponera_tiron", 1.8, 0.2, 5.0);

        B_GATLING = b.comment("Gatling: cofres de las pirámides del desierto.").defineInRange("gatling_piramide", 0.2, 0.0, 1.0);
        B_WINCHESTER = b.comment("Winchester: cofres de las minas abandonadas (vagonetas con cofre).").defineInRange("winchester_mina", 0.1, 0.0, 1.0);
        B_DERRINGER = b.comment("Derringer: cofres de los tesoros enterrados.").defineInRange("derringer_tesoro", 0.35, 0.0, 1.0);
        B_THOMPSON = b.comment("Thompson (en su estuche de violín): cofres de las mansiones del bosque.").defineInRange("thompson_mansion", 0.4, 0.0, 1.0);
        B_CERBATANA = b.comment("Cerbatana: cofres de los templos de la jungla.").defineInRange("cerbatana_templo", 0.3, 0.0, 1.0);
        B_LANZALLAMAS = b.comment("Lanzallamas: cofres de las fortalezas del Nether.").defineInRange("lanzallamas_fortaleza", 0.25, 0.0, 1.0);
        B_RESONANCIA = b.comment("Pistola de resonancia: cofres de las ciudades antiguas.").defineInRange("resonancia_ciudad", 0.3, 0.0, 1.0);
        B_ARPONERA = b.comment("Arponera: cofres de las ruinas submarinas.").defineInRange("arponera_ruinas", 0.3, 0.0, 1.0);
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
    public static double apuntarDispersion() { return d(APUNTAR_DISPERSION); }
    public static float apuntarFov() { return (float) d(APUNTAR_FOV); }
    public static double apuntarLentitud() { return d(APUNTAR_LENTITUD); }
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
    public static float cerbatanaDano() { return (float) d(CERBATANA_DANO); }
    public static double cerbatanaAlcance() { return d(CERBATANA_ALCANCE); }
    public static float lanzallamasDano() { return (float) d(LANZALLAMAS_DANO); }
    public static double lanzallamasAlcance() { return d(LANZALLAMAS_ALCANCE); }
    public static int lanzallamasCapacidad() { return i(LANZALLAMAS_CAPACIDAD); }
    public static float resonanciaDano() { return (float) d(RESONANCIA_DANO); }
    public static double resonanciaAlcance() { return d(RESONANCIA_ALCANCE); }
    public static float arponeraDano() { return (float) d(ARPONERA_DANO); }
    public static double arponeraAlcance() { return d(ARPONERA_ALCANCE); }
    public static float arponeraTiron() { return (float) d(ARPONERA_TIRON); }
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
    public static boolean canonColoca() { return b(CANON_COLOCA); }

    private static float fase(ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> v, int ox) {
        java.util.List<? extends Number> l;
        try {
            l = ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            l = v.getDefault();
        }
        if (l == null || l.isEmpty()) return 0F;
        return l.get(Math.max(0, Math.min(l.size() - 1, ox))).floatValue();
    }

    public static float oxidoAtasco(int ox) { return fase(OX_ATASCO, ox); }
    public static double oxidoDispersion(int ox) { return fase(OX_DISPERSION, ox); }
    public static float oxidoDano(int ox) { return fase(OX_DANO, ox); }
    public static float oxidoPerforacion(int ox) { return fase(OX_PERFORACION, ox); }
    public static int cera() { return i(CERA); }
    public static int ceraAutomaticas() { return i(CERA_AUTOMATICAS); }
    public static float canonBloque() { return (float) d(CANON_BLOQUE); }
    public static float canonYunque() { return (float) d(CANON_YUNQUE); }
    public static boolean gatlingRedstone() { return b(GATLING_REDSTONE); }
    public static double gatlingTorretaAlcance() { return d(GATLING_TORRETA_ALCANCE); }
    public static int winchesterHielo() { return i(WINCHESTER_HIELO); }
    public static int derringerManga() { return i(DERRINGER_MANGA); }
    public static float mosqueteArpon() { return (float) d(MOSQUETE_ARPON); }
    public static int violinEnfriamiento() { return i(VIOLIN_ENFRIAMIENTO); }

    public static double botinGatling() { return d(B_GATLING); }
    public static double botinWinchester() { return d(B_WINCHESTER); }
    public static double botinDerringer() { return d(B_DERRINGER); }
    public static double botinThompson() { return d(B_THOMPSON); }
    public static double botinMosquete() { return d(B_MOSQUETE); }
    public static double botinCerbatana() { return d(B_CERBATANA); }
    public static double botinLanzallamas() { return d(B_LANZALLAMAS); }
    public static double botinResonancia() { return d(B_RESONANCIA); }
    public static double botinArponera() { return d(B_ARPONERA); }
    public static double botinCanon() { return d(B_CANON); }
}
