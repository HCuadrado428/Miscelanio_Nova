package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);

    /** Zumbido de la aspiradora. */
    public static final RegistryObject<SoundEvent> ASPIRAR = register("aspirar");
    /** Pitido de oídos del flashbang. */
    public static final RegistryObject<SoundEvent> PITIDO = register("pitido");
    /** Chispazo corto de la porra. */
    public static final RegistryObject<SoundEvent> CHISPAZO = register("chispazo");
    /** Descarga larga del agarre. */
    public static final RegistryObject<SoundEvent> DESCARGA = register("descarga");

    /** Zumbido del rayo de la medigun (en bucle). */
    public static final RegistryObject<SoundEvent> MEDIGUN_RAYO = register("medigun_rayo");
    public static final RegistryObject<SoundEvent> MEDIGUN_CONECTAR = register("medigun_conectar");
    public static final RegistryObject<SoundEvent> UBER_ACTIVAR = register("uber_activar");
    public static final RegistryObject<SoundEvent> UBER_LISTA = register("uber_lista");
    public static final RegistryObject<SoundEvent> UBER_REBOTE = register("uber_rebote");

    public static final RegistryObject<SoundEvent> NUCLEO_CARGA = register("nucleo_carga");
    /** Rugido del rayo del núcleo (en bucle; lo para el cliente al acabar). */
    public static final RegistryObject<SoundEvent> NUCLEO_RAYO = register("nucleo_rayo");
    public static final RegistryObject<SoundEvent> NUCLEO_FIN = register("nucleo_fin");

    public static final RegistryObject<SoundEvent> ESCOPETA_DISPARO = register("escopeta_disparo");
    public static final RegistryObject<SoundEvent> ESCOPETA_RECARGA = register("escopeta_recarga");
    public static final RegistryObject<SoundEvent> GANCHO_LANZAR = register("gancho_lanzar");
    public static final RegistryObject<SoundEvent> GANCHO_ENGANCHAR = register("gancho_enganchar");
    public static final RegistryObject<SoundEvent> LANZACOHETES_DISPARO = register("lanzacohetes_disparo");
    public static final RegistryObject<SoundEvent> LANZACOHETES_RECARGA = register("lanzacohetes_recarga");
    public static final RegistryObject<SoundEvent> COHETE_EXPLOSION = register("cohete_explosion");
    public static final RegistryObject<SoundEvent> BALLESTA_DISPARO = register("ballesta_disparo");
    public static final RegistryObject<SoundEvent> BALLESTA_PALANCA = register("ballesta_palanca");
    public static final RegistryObject<SoundEvent> BALLESTA_CRUJIDO = register("ballesta_crujido");
    public static final RegistryObject<SoundEvent> BALLESTA_VIROTE = register("ballesta_virote");
    public static final RegistryObject<SoundEvent> BFG_CARGA = register("bfg_carga");
    public static final RegistryObject<SoundEvent> BFG_DISPARO = register("bfg_disparo");
    public static final RegistryObject<SoundEvent> BFG_ZUMBIDO = register("bfg_zumbido");
    public static final RegistryObject<SoundEvent> BFG_RAYO = register("bfg_rayo");
    public static final RegistryObject<SoundEvent> BFG_ESTALLIDO = register("bfg_estallido");
    public static final RegistryObject<SoundEvent> MAZO_GOLPE = register("mazo_golpe");
    public static final RegistryObject<SoundEvent> MAZO_GOLPE_FUERTE = register("mazo_golpe_fuerte");
    public static final RegistryObject<SoundEvent> VIENTO_LANZA = register("viento_lanza");
    public static final RegistryObject<SoundEvent> VIENTO_RAFAGA = register("viento_rafaga");
    public static final RegistryObject<SoundEvent> BFG_LISTO = register("bfg_listo");
    public static final RegistryObject<SoundEvent> BFG_VORTICE = register("bfg_vortice");
    public static final RegistryObject<SoundEvent> MAZO_LANZAR = register("mazo_lanzar");
    public static final RegistryObject<SoundEvent> MAZO_VUELVE = register("mazo_vuelve");
    public static final RegistryObject<SoundEvent> MAZO_SISMO = register("mazo_sismo");
    public static final RegistryObject<SoundEvent> MAZO_CARGADO = register("mazo_cargado");

    public static final RegistryObject<SoundEvent> LLAVE_GOLPE = register("llave_golpe");
    public static final RegistryObject<SoundEvent> LLAVE_IMPACTO = register("llave_impacto");
    public static final RegistryObject<SoundEvent> LLAVE_REMATE = register("llave_remate");
    public static final RegistryObject<SoundEvent> LLAVE_LANZAR = register("llave_lanzar");
    public static final RegistryObject<SoundEvent> HECHIZO_PIRO = register("hechizo_piro");
    public static final RegistryObject<SoundEvent> HECHIZO_HIELO = register("hechizo_hielo");
    public static final RegistryObject<SoundEvent> HECHIZO_CURA = register("hechizo_cura");
    public static final RegistryObject<SoundEvent> CERRADURA = register("cerradura");

    public static final RegistryObject<SoundEvent> HACHA_GOLPE = register("hacha_golpe");
    public static final RegistryObject<SoundEvent> HACHA_IMPACTO = register("hacha_impacto");
    public static final RegistryObject<SoundEvent> HACHA_LANZAR = register("hacha_lanzar");
    public static final RegistryObject<SoundEvent> HACHA_VOLVER = register("hacha_volver");
    public static final RegistryObject<SoundEvent> HACHA_ATRAPAR = register("hacha_atrapar");
    public static final RegistryObject<SoundEvent> ESCUDO_BLOQUEO = register("escudo_bloqueo");
    public static final RegistryObject<SoundEvent> ESCUDO_PARRY = register("escudo_parry");
    public static final RegistryObject<SoundEvent> ESCUDO_ABRIR = register("escudo_abrir");
    public static final RegistryObject<SoundEvent> RUNICO = register("runico");
    public static final RegistryObject<SoundEvent> ESCARCHA = register("escarcha");
    public static final RegistryObject<SoundEvent> PUNETAZO = register("punetazo");

    @SuppressWarnings("unchecked")
    public static final RegistryObject<SoundEvent>[] RADIO = new RegistryObject[]{register("radio_0"), register("radio_1"), register("radio_2")};
    public static final RegistryObject<SoundEvent> RADIO_ESTATICA = register("radio_estatica");
    public static final RegistryObject<SoundEvent> PIPBOY_ABRIR = register("pipboy_abrir");
    public static final RegistryObject<SoundEvent> PIPBOY_CLIC = register("pipboy_clic");
    public static final RegistryObject<SoundEvent> PIPBOY_ENCENDER = register("pipboy_encender");
    public static final RegistryObject<SoundEvent> PIPBOY_PONER = register("pipboy_poner");


    public static final RegistryObject<SoundEvent> ARCADE_FICHA = register("arcade_ficha");
    public static final RegistryObject<SoundEvent> ARCADE_MUSICA = register("arcade_musica");
    public static final RegistryObject<SoundEvent> CH_DISPARO = register("ch_disparo");
    public static final RegistryObject<SoundEvent> CH_METRALLA = register("ch_metralla");
    public static final RegistryObject<SoundEvent> CH_COHETE = register("ch_cohete");
    public static final RegistryObject<SoundEvent> CH_EXPLOSION = register("ch_explosion");
    public static final RegistryObject<SoundEvent> CH_GRANADA = register("ch_granada");
    public static final RegistryObject<SoundEvent> CH_SALTO = register("ch_salto");
    public static final RegistryObject<SoundEvent> CH_RESCATE = register("ch_rescate");
    public static final RegistryObject<SoundEvent> CH_ARMA = register("ch_arma");
    public static final RegistryObject<SoundEvent> CH_MUERTE = register("ch_muerte");
    public static final RegistryObject<SoundEvent> CH_GOLPE = register("ch_golpe");
    public static final RegistryObject<SoundEvent> CH_JEFE = register("ch_jefe");
    public static final RegistryObject<SoundEvent> CH_MUSICA = register("ch_musica");
    public static final RegistryObject<SoundEvent> MZ_ESCOPETA = register("mz_escopeta");
    public static final RegistryObject<SoundEvent> MZ_PUERTA = register("mz_puerta");
    public static final RegistryObject<SoundEvent> MZ_COGER = register("mz_coger");
    public static final RegistryObject<SoundEvent> MZ_DOLOR = register("mz_dolor");
    public static final RegistryObject<SoundEvent> MZ_GRUNIDO = register("mz_grunido");
    public static final RegistryObject<SoundEvent> MZ_MUERE = register("mz_muere");
    public static final RegistryObject<SoundEvent> MZ_BOLA = register("mz_bola");
    public static final RegistryObject<SoundEvent> MZ_SALIDA = register("mz_salida");
    public static final RegistryObject<SoundEvent> MZ_MUSICA = register("mz_musica");
    public static final RegistryObject<SoundEvent> TC_CHOQUE = register("tc_choque");
    public static final RegistryObject<SoundEvent> TC_DERRAPE = register("tc_derrape");
    public static final RegistryObject<SoundEvent> TC_PUNTO = register("tc_punto");
    public static final RegistryObject<SoundEvent> TC_TURBO = register("tc_turbo");
    public static final RegistryObject<SoundEvent> TC_META = register("tc_meta");
    public static final RegistryObject<SoundEvent> TC_CUENTA = register("tc_cuenta");
    public static final RegistryObject<SoundEvent> TC_MUSICA = register("tc_musica");
    public static final RegistryObject<SoundEvent> LU_AIRE = register("lu_aire");
    public static final RegistryObject<SoundEvent> LU_GOLPE = register("lu_golpe");
    public static final RegistryObject<SoundEvent> LU_GOLPE2 = register("lu_golpe2");
    public static final RegistryObject<SoundEvent> LU_BLOQUEO = register("lu_bloqueo");
    public static final RegistryObject<SoundEvent> LU_CAIDA = register("lu_caida");
    public static final RegistryObject<SoundEvent> LU_KO = register("lu_ko");
    public static final RegistryObject<SoundEvent> LU_RONDA = register("lu_ronda");
    public static final RegistryObject<SoundEvent> LU_ESPECIAL = register("lu_especial");
    public static final RegistryObject<SoundEvent> LU_SELEC = register("lu_selec");
    public static final RegistryObject<SoundEvent> LU_COMER = register("lu_comer");
    public static final RegistryObject<SoundEvent> LU_ROMPER = register("lu_romper");
    public static final RegistryObject<SoundEvent> LU_DUELO = register("lu_duelo");
    public static final RegistryObject<SoundEvent> LU_BARRIO = register("lu_barrio");
    public static final RegistryObject<SoundEvent> CC_PUNTO = register("cc_punto");
    public static final RegistryObject<SoundEvent> CC_PILA = register("cc_pila");
    public static final RegistryObject<SoundEvent> CC_ROBOT = register("cc_robot");
    public static final RegistryObject<SoundEvent> CC_MUERTE = register("cc_muerte");
    public static final RegistryObject<SoundEvent> CC_FRUTA = register("cc_fruta");
    public static final RegistryObject<SoundEvent> CC_EXTRA = register("cc_extra");
    public static final RegistryObject<SoundEvent> CC_INICIO = register("cc_inicio");
    public static final RegistryObject<SoundEvent> CC_MUSICA = register("cc_musica");
    public static final RegistryObject<SoundEvent> BI_CHOQUE = register("bi_choque");
    public static final RegistryObject<SoundEvent> BI_BANDA = register("bi_banda");
    public static final RegistryObject<SoundEvent> BI_TRONERA = register("bi_tronera");
    public static final RegistryObject<SoundEvent> BI_TACO = register("bi_taco");
    public static final RegistryObject<SoundEvent> FU_GOLPE = register("fu_golpe");
    public static final RegistryObject<SoundEvent> FU_GOL = register("fu_gol");
    public static final RegistryObject<SoundEvent> FU_SILBATO = register("fu_silbato");
    public static final RegistryObject<SoundEvent> PB_MUSICA = register("pb_musica");
    public static final RegistryObject<SoundEvent> PB_FLIPPER = register("pb_flipper");
    public static final RegistryObject<SoundEvent> PB_LANZAR = register("pb_lanzar");
    public static final RegistryObject<SoundEvent> PB_SETA = register("pb_seta");
    public static final RegistryObject<SoundEvent> PB_DIANA = register("pb_diana");
    public static final RegistryObject<SoundEvent> PB_TIRACHINAS = register("pb_tirachinas");
    public static final RegistryObject<SoundEvent> PB_AGUJERO = register("pb_agujero");
    public static final RegistryObject<SoundEvent> PB_EXTRA = register("pb_extra");
    public static final RegistryObject<SoundEvent> PB_PERDER = register("pb_perder");
    public static final RegistryObject<SoundEvent> DB_MUSICA = register("db_musica");
    public static final RegistryObject<SoundEvent> DB_PIERDE = register("db_pierde");
    public static final RegistryObject<SoundEvent> DB_GIRO = register("db_giro");
    public static final RegistryObject<SoundEvent> DB_FIJA = register("db_fija");
    public static final RegistryObject<SoundEvent> DB_LINEA = register("db_linea");
    public static final RegistryObject<SoundEvent> DB_CUATRO = register("db_cuatro");
    public static final RegistryObject<SoundEvent> DB_BASURA = register("db_basura");
    public static final RegistryObject<SoundEvent> DB_MOVER = register("db_mover");
    public static final RegistryObject<SoundEvent> DB_GANA = register("db_gana");
    public static final RegistryObject<SoundEvent> RI_VERBENA = register("ri_verbena");
    public static final RegistryObject<SoundEvent> RI_NEON = register("ri_neon");
    public static final RegistryObject<SoundEvent> RI_TORMENTA = register("ri_tormenta");
    public static final RegistryObject<SoundEvent> FE_ARRANCA = register("fe_arranca");
    public static final RegistryObject<SoundEvent> FE_PARA = register("fe_para");
    public static final RegistryObject<SoundEvent> FE_TIOVIVO = register("fe_tiovivo");
    public static final RegistryObject<SoundEvent> FE_NORIA = register("fe_noria");
    public static final RegistryObject<SoundEvent> FE_CHOQUE = register("fe_choque");
    public static final RegistryObject<SoundEvent> FE_BOMBO = register("fe_bombo");
    public static final RegistryObject<SoundEvent> FE_CUAC = register("fe_cuac");
    public static final RegistryObject<SoundEvent> PE_CAE = register("pe_cae");
    public static final RegistryObject<SoundEvent> PE_CHOQUE = register("pe_choque");
    public static final RegistryObject<SoundEvent> FE_CANASTA = register("fe_canasta");
    public static final RegistryObject<SoundEvent> FE_BOTE = register("fe_bote");
    public static final RegistryObject<SoundEvent> FE_PUNETAZO = register("fe_punetazo");
    public static final RegistryObject<SoundEvent> FE_MAZAZO = register("fe_mazazo");
    public static final RegistryObject<SoundEvent> FE_CAMPANA = register("fe_campana");
    public static final RegistryObject<SoundEvent> AH_GOLPE = register("ah_golpe");
    public static final RegistryObject<SoundEvent> AH_BANDA = register("ah_banda");
    public static final RegistryObject<SoundEvent> AH_GOL = register("ah_gol");
    public static final RegistryObject<SoundEvent> CA_BARAJAR = register("ca_barajar");
    public static final RegistryObject<SoundEvent> CA_CARTA = register("ca_carta");
    public static final RegistryObject<SoundEvent> CA_BAZA = register("ca_baza");
    public static final RegistryObject<SoundEvent> CO_CHISPORROTEO = register("co_chisporroteo");
    public static final RegistryObject<SoundEvent> TO_CLAC = register("to_clac");
    public static final RegistryObject<SoundEvent> TO_DERRUMBE = register("to_derrumbe");
    public static final RegistryObject<SoundEvent> BO_BOLO = register("bo_bolo");
    public static final RegistryObject<SoundEvent> BO_RUEDA = register("bo_rueda");
    public static final RegistryObject<SoundEvent> BO_PLENO = register("bo_pleno");
    public static final RegistryObject<SoundEvent> DA_CLAVA = register("da_clava");
    public static final RegistryObject<SoundEvent> DA_LANZA = register("da_lanza");
    public static final RegistryObject<SoundEvent> FE_GANCHO = register("fe_gancho");
    public static final RegistryObject<SoundEvent> FE_GANCHO_MUSICA = register("fe_gancho_musica");
    public static final RegistryObject<SoundEvent> FE_PREMIO = register("fe_premio");
    public static final RegistryObject<SoundEvent> FE_PREMIO_GORDO = register("fe_premio_gordo");
    public static final RegistryObject<SoundEvent> FE_RODILLOS = register("fe_rodillos");
    public static final RegistryObject<SoundEvent> FE_RODILLO_PARA = register("fe_rodillo_para");
    public static final RegistryObject<SoundEvent> FE_MANIVELA = register("fe_manivela");
    public static final RegistryObject<SoundEvent> FE_CAPSULA = register("fe_capsula");
    public static final RegistryObject<SoundEvent> FE_CAPSULA_ABRE = register("fe_capsula_abre");
    public static final RegistryObject<SoundEvent> PI_DISPARO = register("pi_disparo");
    public static final RegistryObject<SoundEvent> PI_RECARGA = register("pi_recarga");
    public static final RegistryObject<SoundEvent> PI_VACIO = register("pi_vacio");
    public static final RegistryObject<SoundEvent> PI_IMPACTO = register("pi_impacto");
    public static final RegistryObject<SoundEvent> PI_ROBOT = register("pi_robot");
    public static final RegistryObject<SoundEvent> PI_DANO = register("pi_dano");
    public static final RegistryObject<SoundEvent> PI_EXPLOSION = register("pi_explosion");
    public static final RegistryObject<SoundEvent> PI_MUSICA = register("pi_musica");
    public static final RegistryObject<SoundEvent> FE_ALGODON = register("fe_algodon");
    public static final RegistryObject<SoundEvent> FE_POP = register("fe_pop");
    public static final RegistryObject<SoundEvent> FE_MASCLET = register("fe_masclet");
    public static final RegistryObject<SoundEvent> FE_MASCLET_FINAL = register("fe_masclet_final");
    public static final RegistryObject<SoundEvent> FE_TREN = register("fe_tren");
    public static final RegistryObject<SoundEvent> FE_BRUJA = register("fe_bruja");
    public static final RegistryObject<SoundEvent> FE_HUESOS = register("fe_huesos");
    public static final RegistryObject<SoundEvent> FE_TRUENO = register("fe_trueno");
    public static final RegistryObject<SoundEvent> FE_FANTASMA = register("fe_fantasma");
    public static final RegistryObject<SoundEvent> FE_CORCHO = register("fe_corcho");
    public static final RegistryObject<SoundEvent> FE_DIANA = register("fe_diana");
    public static final RegistryObject<SoundEvent> DETECTOR_PITIDO = register("detector_pitido");
    public static final RegistryObject<SoundEvent> TESORO = register("tesoro");
    public static final RegistryObject<SoundEvent> CAPSULA_ABRIR = register("capsula_abrir");
    public static final RegistryObject<SoundEvent> TIROLINA_ZUMBIDO = register("tirolina_zumbido");
    public static final RegistryObject<SoundEvent> TIROLINA_ENGANCHAR = register("tirolina_enganchar");
    public static final RegistryObject<SoundEvent> TRAMPOLIN_BOING = register("trampolin_boing");
    // ---- Ocarina
    public static final RegistryObject<SoundEvent>[] OCARINA = new RegistryObject[]{register("ocarina_0"), register("ocarina_1"), register("ocarina_2"), register("ocarina_3"), register("ocarina_4")};
    public static final RegistryObject<SoundEvent>[] CANCION = new RegistryObject[]{register("cancion_sol"), register("cancion_luna"), register("cancion_tormenta"), register("cancion_despejar"), register("cancion_regreso")};
    public static final RegistryObject<SoundEvent> OCARINA_EXITO = register("ocarina_exito");
    // ---- Consola de bolsillo
    public static final RegistryObject<SoundEvent> GB_MOVER = register("gb_mover");
    public static final RegistryObject<SoundEvent> GB_GIRAR = register("gb_girar");
    public static final RegistryObject<SoundEvent> GB_LINEA = register("gb_linea");
    public static final RegistryObject<SoundEvent> GB_COMER = register("gb_comer");
    public static final RegistryObject<SoundEvent> GB_PUNTO = register("gb_punto");
    public static final RegistryObject<SoundEvent> GB_REBOTE = register("gb_rebote");
    public static final RegistryObject<SoundEvent> GB_DISPARO = register("gb_disparo");
    public static final RegistryObject<SoundEvent> GB_EXPLOSION = register("gb_explosion");
    public static final RegistryObject<SoundEvent> GB_ALETEO = register("gb_aleteo");
    public static final RegistryObject<SoundEvent> GB_FIN = register("gb_fin");
    public static final RegistryObject<SoundEvent> GB_INICIO = register("gb_inicio");
    public static final RegistryObject<SoundEvent> GB_MUSICA = register("gb_musica");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, name)));
    }

    private ModSounds() {}
}
