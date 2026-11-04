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
    private static final ForgeConfigSpec.BooleanValue MUERTE_INSTANTANEA, APARATOS_SUPERVIVENCIA, MUERTES_ANIMADAS;
    // ---- bichos
    private static final ForgeConfigSpec.BooleanValue BICHOS_APARICION;
    private static final ForgeConfigSpec.IntValue BICHOS_INTERVALO, BICHOS_MAX;
    // ---- trampas
    private static final ForgeConfigSpec.IntValue TRAMPA_OSO_S, TRAMPA_OSO_SIN_CAJAS_S, CAJA_AGUA_LLENADO_S,
            GANCHO_DANO_DESDE_S, CEPO_S;
    // ---- Dream (mordisco venenoso y anillo de medianoche)
    private static final ForgeConfigSpec.BooleanValue MORDISCO_ACTIVADO, MORDISCO_JUGADORES;
    private static final ForgeConfigSpec.IntValue MORDISCO_LENTITUD_MAX, MORDISCO_LENTITUD_S, MORDISCO_NAUSEA_S,
            MORDISCO_DESANGRADO_NIVEL, MORDISCO_DESANGRADO_S, MORDISCO_DEBILIDAD_S, VENENO_ENFRIAMIENTO_S, ANILLO_ENFRIAMIENTO_S;
    private static final ForgeConfigSpec.DoubleValue MORDISCO_DANO, MORDISCO_UMBRAL, DESANGRADO_DANO;
    private static final ForgeConfigSpec.ConfigValue<String> ANILLO_DIMENSION;
    // ---- glitch (muestra de glitch)
    private static final ForgeConfigSpec.IntValue GLITCH_RADIO, GLITCH_BLOQUES_TICK;
    private static final ForgeConfigSpec.BooleanValue GLITCH_DESTRUIR, GLITCH_CONTENEDORES;
    private static final ForgeConfigSpec.DoubleValue GLITCH_DANO;
    // ---- bromas (y la rueda de Catalina)
    private static final ForgeConfigSpec.BooleanValue BROMA_UWU, BROMA_CONTROLES, BROMA_CARTEL, BROMA_DUENDE, BROMA_REVES, RUEDA_MUERTE;
    private static final ForgeConfigSpec.IntValue BROMA_UWU_S, BROMA_CONTROLES_S, BROMA_CARTEL_S, BROMA_DUENDE_S, BROMA_DUENDE_CADA_S,
            BROMA_REVES_S, BROMA_ENFRIAMIENTO_S, RUEDA_ACIERTOS, RUEDA_MUERTE_S;
    private static final ForgeConfigSpec.DoubleValue RUEDA_DANO;
    // ---- pista de baile
    private static final ForgeConfigSpec.IntValue BAILE_RADIO;
    // ---- estática (poción del hombre de estática)
    private static final ForgeConfigSpec.IntValue EST_DURACION_S, EST_TOQUE_LENTITUD_S, EST_TOQUE_CORROMPER_S, EST_PARPADEO_ENF_S, EST_PANTALLAS_RADIO,
            EST_PANTALLAS_ENF_S, EST_PANTALLAS_MAX_S, EST_LUCES_RADIO, EST_LUCES_VUELTA_S, EST_IMITAR_ENF_S, EST_CHARCOS_MAX, EST_CHARCOS_VIDA_S,
            EST_OSCURIDAD_LUZ, EST_ARRASTRAR_ENF_S, EST_FRAGMENTARSE_ENF_S, EST_ATRAER_ENF_S, EST_LANZAR_ENF_S, EST_ESTAMPAR_ENF_S;
    private static final ForgeConfigSpec.BooleanValue EST_LECHE, EST_CINEMATICA, EST_TOQUE, EST_PARPADEO, EST_PANTALLAS, EST_LUCES, EST_IMITAR,
            EST_CHARCOS, EST_OSCURIDAD, EST_ARRASTRAR, EST_FRAGMENTARSE, EST_ATRAER, EST_LANZAR, EST_ESTAMPAR;
    // ---- juegos de mesa
    private static final ForgeConfigSpec.IntValue MESAS_TURNO_S, MESAS_AUSENCIA_S;
    private static final ForgeConfigSpec.BooleanValue MESAS_VICTORIAS_CPU;
    private static final ForgeConfigSpec.DoubleValue EST_VELOCIDAD, EST_GRAVEDAD, EST_CAIDA, EST_TOQUE_DANO, EST_PARPADEO_DIST, EST_OSCURIDAD_QUIETO_S,
            EST_ARRASTRAR_ALCANCE, EST_ARRASTRAR_DIST, EST_TENTACULOS_ALCANCE, EST_LANZAR_FUERZA, EST_ESTAMPAR_DANO, EST_ESTAMPAR_ALTURA;

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
                        "silla eléctrica, péndulo, caja de agua...) matan del tirón en vez de dejarte derribado.",
                        "(En las versiones de Incapacitated que usan la etiqueta incapacitated:no_incap, la etiqueta del mod sigue aplicándose.)")
                .define("muerte_instantanea_incapacitated", true);
        APARATOS_SUPERVIVENCIA = b.comment("Si es false, los jugadores en supervivencia/aventura no pueden poner ni usar los aparatos de tortura",
                        "(ni ponerle a nadie grilletes, trampas, collares...). En creativo se sigue pudiendo, para montar escenas;",
                        "quien ya está atrapado puede seguir intentando escapar (ganzúas, llaves, sierra...).")
                .define("aparatos_en_supervivencia", true);
        MUERTES_ANIMADAS = b.comment("Si es true, las muertes de los aparatos (doncella, potro, silla eléctrica, péndulo, silla de inmersión,",
                        "collar explosivo, caja de agua, paro cardíaco, colgado) tienen su cinemática: la víctima ve su propia muerte",
                        "desde una cámara de cine y los demás ven la animación. Si es false, se muere en el acto como antes.")
                .define("muertes_animadas", true);
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
        MORDISCO_LENTITUD_MAX = b.comment("Veneno paralizante: cada mordisco deja Lentitud, que sube un nivel cada dos mordiscos seguidos",
                        "hasta este tope (0 = Lentitud I, 1 = II, 2 = III...).")
                .defineInRange("mordisco_lentitud_max", 2, 0, 6);
        MORDISCO_LENTITUD_S = b.comment("Segundos de Lentitud que deja cada mordisco (muerde cada 1,5 s).")
                .defineInRange("mordisco_lentitud_segundos", 8, 1, 120);
        MORDISCO_NAUSEA_S = b.comment("Segundos de Náuseas (mareo) que deja cada mordisco. 0 = sin náuseas.")
                .defineInRange("mordisco_nausea_segundos", 10, 0, 120);
        MORDISCO_DESANGRADO_NIVEL = b.comment("Nivel del Desangrado de cada mordisco (0 = I: pierde vida cada 2 s; II cada 1 s...).")
                .defineInRange("mordisco_desangrado_nivel", 0, 0, 3);
        MORDISCO_DESANGRADO_S = b.comment("Segundos de Desangrado que deja cada mordisco. 0 = no sangra.")
                .defineInRange("mordisco_desangrado_segundos", 10, 0, 300);
        MORDISCO_DEBILIDAD_S = b.comment("Segundos de Debilidad I que deja cada mordisco. 0 = sin debilidad.")
                .defineInRange("mordisco_debilidad_segundos", 6, 0, 120);
        MORDISCO_DANO = b.comment("Daño directo de cada mordisco (los colmillos), aparte del desangrado.")
                .defineInRange("mordisco_dano", 1.0, 0.0, 100.0);
        DESANGRADO_DANO = b.comment("Daño de cada pérdida de sangre del efecto Desangrado (ignora la armadura).")
                .defineInRange("desangrado_dano", 1.0, 0.0, 100.0);
        VENENO_ENFRIAMIENTO_S = b.comment("Segundos sin veneno después de extraerlo en una botella (Veneno de Dream): mientras tanto",
                        "no se puede volver a extraer ni morder. 0 = sin espera.")
                .defineInRange("veneno_enfriamiento_segundos", 300, 0, 86400);
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

        b.comment("Bromas (objetos de creativo): se activan / desactivan una a una y se ajusta cuánto duran.").push("bromas");
        BROMA_UWU = b.comment("Varita uwu: el chat de la víctima sale en \"uwu\" (r/l -> w, nya~, >w<...).").define("uwu_activada", true);
        BROMA_UWU_S = b.comment("Segundos que dura el hechizo uwu.").defineInRange("uwu_segundos", 180, 1, 86400);
        BROMA_CONTROLES = b.comment("Poción de controles cambiados: W<->S y A<->D.").define("controles_activada", true);
        BROMA_CONTROLES_S = b.comment("Segundos que duran los controles cambiados.").defineInRange("controles_segundos", 30, 1, 3600);
        BROMA_CARTEL = b.comment("Cartel de \"PATÉAME\" en la espalda (cada golpe que recibe: confeti y \"boing\").").define("cartel_activada", true);
        BROMA_CARTEL_S = b.comment("Segundos que dura el cartel pegado (también se cae al morir, o la víctima se lo quita: agachada + clic derecho con la mano vacía).")
                .defineInRange("cartel_segundos", 600, 1, 86400);
        BROMA_DUENDE = b.comment("Duende travieso: revuelve el inventario de la víctima (nunca borra nada).").define("duende_activada", true);
        BROMA_DUENDE_S = b.comment("Segundos que dura el duende.").defineInRange("duende_segundos", 300, 1, 86400);
        BROMA_DUENDE_CADA_S = b.comment("Cada cuántos segundos revuelve el inventario.").defineInRange("duende_cada_segundos", 60, 5, 3600);
        BROMA_REVES = b.comment("Espejo del revés: la pantalla de la víctima se pone boca abajo.").define("reves_activada", true);
        BROMA_REVES_S = b.comment("Segundos que dura la pantalla del revés.").defineInRange("reves_segundos", 20, 1, 600);
        BROMA_ENFRIAMIENTO_S = b.comment("Segundos de espera de quien gasta la broma antes de poder repetirla con el mismo objeto.")
                .defineInRange("enfriamiento_segundos", 10, 0, 3600);
        b.pop();

        b.comment("Rueda de Catalina (sala de juegos).").push("rueda_catalina");
        RUEDA_ACIERTOS = b.comment("Rondas del minijuego (mantener la mirilla en el blanco) que hay que superar para soltar cada una de las 4 correas.")
                .defineInRange("aciertos_por_correa", 2, 1, 20);
        RUEDA_DANO = b.comment("Daño de cada ronda fallada (sube un poco con la velocidad de la rueda).").defineInRange("dano_fallo", 2.0, 0.0, 100.0);
        RUEDA_MUERTE = b.comment("Si la rueda puede llegar a matar (solo después de rueda_muerte_segundos atado).").define("puede_matar", true);
        RUEDA_MUERTE_S = b.comment("Segundos atado a la rueda antes de que los fallos puedan matar (hasta entonces te deja con 1 corazón).")
                .defineInRange("muerte_tras_segundos", 600, 0, 86400);
        b.pop();

        b.comment("Pista de baile.").push("pista_baile");
        BAILE_RADIO = b.comment("Mientras una pista suena, ninguna otra a menos de estos bloques puede empezar una canción (para que no se mezclen las músicas). 0 = sin límite.")
                .defineInRange("radio_otras_pistas", 32, 0, 512);
        b.pop();

        b.comment("Estática: la poción del hombre de estática (solo de creativo). Cada poder se puede apagar por separado.").push("estatica");
        EST_DURACION_S = b.comment("Segundos que dura el efecto Estática al beber la poción (20 min por defecto; se acaba solo).")
                .defineInRange("duracion_segundos", 1200, 10, 86400);
        EST_LECHE = b.comment("Si la leche quita la Estática (por defecto no: solo se acaba con el tiempo o con /effect clear).").define("leche_cura", false);
        EST_CINEMATICA = b.comment("Cinemática de la transformación al beberla (cámara de frente; el jugador no se puede mover mientras).")
                .define("cinematica", true);
        EST_VELOCIDAD = b.comment("Cambio de velocidad al andar (multiplicador: -0.12 = un 12 % más lento).").defineInRange("velocidad", -0.12, -0.9, 1.0);
        EST_GRAVEDAD = b.comment("Cambio de gravedad (multiplicador: -0.35 = saltos más largos y caídas más lentas).").defineInRange("gravedad", -0.35, -0.9, 1.0);
        EST_CAIDA = b.comment("Multiplicador del daño de caída.").defineInRange("dano_caida", 0.4, 0.0, 1.0);
        EST_TOQUE = b.comment("Toque estático: golpear con la mano vacía da una descarga (más daño, lentitud, nieve en la pantalla de la víctima y su barra",
                "de objetos se ve corrupta un rato).").define("toque_activado", true);
        EST_TOQUE_DANO = b.comment("Daño extra de cada toque estático.").defineInRange("toque_dano", 2.0, 0.0, 100.0);
        EST_TOQUE_LENTITUD_S = b.comment("Segundos de lentitud del toque.").defineInRange("toque_lentitud_segundos", 2, 0, 60);
        EST_TOQUE_CORROMPER_S = b.comment("Segundos que la víctima ve sus objetos corruptos (solo se ve así; los objetos no cambian). 0 = nunca.")
                .defineInRange("toque_corromper_segundos", 10, 0, 120);
        EST_PARPADEO = b.comment("Poder Parpadeo: salta unos bloques hacia delante por el aire (nunca a través de paredes).").define("parpadeo_activado", true);
        EST_PARPADEO_DIST = b.comment("Distancia máxima del parpadeo (bloques).").defineInRange("parpadeo_distancia", 5.0, 1.0, 16.0);
        EST_PARPADEO_ENF_S = b.comment("Segundos de espera entre parpadeos.").defineInRange("parpadeo_enfriamiento_segundos", 4, 0, 600);
        EST_PANTALLAS = b.comment("Poder Meterse en pantallas: entra por una recreativa / pantalla y sale por otra cercana.").define("pantallas_activado", true);
        EST_PANTALLAS_RADIO = b.comment("Radio (bloques) en el que se buscan pantallas de salida.").defineInRange("pantallas_radio", 32, 4, 96);
        EST_PANTALLAS_ENF_S = b.comment("Segundos de espera al salir de una pantalla.").defineInRange("pantallas_enfriamiento_segundos", 10, 0, 600);
        EST_PANTALLAS_MAX_S = b.comment("Segundos máximos dentro de las pantallas (luego sale solo por donde entró).").defineInRange("pantallas_max_segundos", 20, 2, 600);
        EST_LUCES = b.comment("Apagar luces al pasar: lámparas, velas y hogueras cercanas se apagan y vuelven a encenderse cuando se va (las antorchas",
                "y faroles solo parpadean). Nunca rompe bloques.").define("luces_activado", true);
        EST_LUCES_RADIO = b.comment("Radio (bloques) de las luces que se apagan.").defineInRange("luces_radio", 4, 1, 12);
        EST_LUCES_VUELTA_S = b.comment("Segundos que tardan en volver a encenderse cuando ya no hay nadie de estática cerca.").defineInRange("luces_vuelta_segundos", 3, 1, 600);
        EST_IMITAR = b.comment("Poder Imitar voces (necesita Simple Voice Chat): graba trozos de lo que dicen los de alrededor y los reproduce,",
                "con estática, desde un bloque.").define("imitar_activado", true);
        EST_IMITAR_ENF_S = b.comment("Segundos de espera entre imitaciones.").defineInRange("imitar_enfriamiento_segundos", 8, 0, 600);
        EST_CHARCOS = b.comment("Charcos de estática al andar (no son bloques): quien los pisa va lento y ve nieve.").define("charcos_activado", true);
        EST_CHARCOS_MAX = b.comment("Charcos a la vez por jugador.").defineInRange("charcos_max", 6, 1, 32);
        EST_CHARCOS_VIDA_S = b.comment("Segundos que dura cada charco.").defineInRange("charcos_vida_segundos", 30, 2, 600);
        EST_OSCURIDAD = b.comment("Fundirse en la oscuridad: quieto a oscuras se vuelve invisible (solo se le ven los ojos).").define("oscuridad_activado", true);
        EST_OSCURIDAD_LUZ = b.comment("Luz máxima (0-15) para fundirse.").defineInRange("oscuridad_luz_maxima", 4, 0, 15);
        EST_OSCURIDAD_QUIETO_S = b.comment("Segundos quieto a oscuras antes de fundirse.").defineInRange("oscuridad_segundos", 2.0, 0.0, 60.0);
        EST_ARRASTRAR = b.comment("Poder Arrastrar a la estática: se lleva consigo a quien mira y aparecen juntos unos bloques más allá.")
                .define("arrastrar_activado", true);
        EST_ARRASTRAR_ALCANCE = b.comment("Distancia máxima al objetivo (bloques).").defineInRange("arrastrar_alcance", 4.0, 1.0, 16.0);
        EST_ARRASTRAR_DIST = b.comment("Distancia máxima del viaje (bloques).").defineInRange("arrastrar_distancia", 8.0, 2.0, 32.0);
        EST_ARRASTRAR_ENF_S = b.comment("Segundos de espera.").defineInRange("arrastrar_enfriamiento_segundos", 12, 0, 600);
        EST_FRAGMENTARSE = b.comment("Fragmentarse (pasivo): al recibir un golpe cuerpo a cuerpo se deshace en estática (sin daño) y aparece detrás",
                "del atacante.").define("fragmentarse_activado", true);
        EST_FRAGMENTARSE_ENF_S = b.comment("Segundos de espera entre fragmentaciones.").defineInRange("fragmentarse_enfriamiento_segundos", 15, 0, 600);
        EST_TENTACULOS_ALCANCE = b.comment("Alcance de los tentáculos de estática (bloques).").defineInRange("tentaculos_alcance", 10.0, 2.0, 32.0);
        EST_ATRAER = b.comment("Tentáculos: Atraer (agarra al objetivo y lo trae).").define("atraer_activado", true);
        EST_ATRAER_ENF_S = b.comment("Segundos de espera de Atraer.").defineInRange("atraer_enfriamiento_segundos", 6, 0, 600);
        EST_LANZAR = b.comment("Tentáculos: Lanzar (agarra al objetivo y lo lanza hacia donde mira).").define("lanzar_activado", true);
        EST_LANZAR_FUERZA = b.comment("Fuerza del lanzamiento.").defineInRange("lanzar_fuerza", 2.2, 0.2, 8.0);
        EST_LANZAR_ENF_S = b.comment("Segundos de espera de Lanzar.").defineInRange("lanzar_enfriamiento_segundos", 8, 0, 600);
        EST_ESTAMPAR = b.comment("Tentáculos: Estampar (lo sube por el aire y lo estampa contra el suelo).").define("estampar_activado", true);
        EST_ESTAMPAR_DANO = b.comment("Daño del golpe contra el suelo.").defineInRange("estampar_dano", 8.0, 0.0, 200.0);
        EST_ESTAMPAR_ALTURA = b.comment("Altura (bloques) a la que lo sube.").defineInRange("estampar_altura", 4.5, 1.0, 16.0);
        EST_ESTAMPAR_ENF_S = b.comment("Segundos de espera de Estampar.").defineInRange("estampar_enfriamiento_segundos", 12, 0, 600);
        b.pop();

        b.comment("Juegos de mesa (parchís, oca, imperio, cuatro en raya, hundir la flota, dominó, cartas, tablero, billar...).").push("juegos_de_mesa");
        MESAS_TURNO_S = b.comment("Segundos que tiene cada jugador para hacer su jugada; al acabarse juega la CPU por él hasta que vuelva a tocar la mesa (0 = sin límite).")
                .defineInRange("turno_segundos", 60, 0, 900);
        MESAS_AUSENCIA_S = b.comment("Segundos que puede estar desconectado o lejos de la mesa el jugador de turno antes de que la CPU ocupe su sitio (hasta que vuelva).")
                .defineInRange("ausencia_segundos", 20, 3, 600);
        MESAS_VICTORIAS_CPU = b.comment("Contar en el marcador de victorias las partidas ganadas solo contra la CPU.").define("victorias_contra_cpu", true);
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
    public static boolean muertesAnimadas() { return b(MUERTES_ANIMADAS); }

    // ------------------------------------------------------------------ bichos
    public static boolean bichosAparicion() { return b(BICHOS_APARICION); }
    public static int bichosIntervalo() { return Math.max(1, i(BICHOS_INTERVALO)); }
    public static int bichosMax() { return i(BICHOS_MAX); }

    // ------------------------------------------------------------------ trampas (en ticks)
    public static int trampaOsoTicks() { return i(TRAMPA_OSO_S) * 20; }
    public static int trampaOsoSinCajasTicks() { return i(TRAMPA_OSO_SIN_CAJAS_S) * 20; }
    public static int cajaAguaLlenadoTicks() { return Math.max(1, i(CAJA_AGUA_LLENADO_S) * 20); }
    public static int ganchoDanoDesdeTicks() { return Math.max(20, i(GANCHO_DANO_DESDE_S) * 20); }
    public static int cepoTicks() { return i(CEPO_S) * 20; }

    // ------------------------------------------------------------------ Dream
    public static boolean mordiscoActivado() { return b(MORDISCO_ACTIVADO); }
    public static boolean mordiscoJugadores() { return b(MORDISCO_JUGADORES); }
    public static int mordiscoLentitudMax() { return i(MORDISCO_LENTITUD_MAX); }
    public static int mordiscoLentitudTicks() { return Math.max(20, i(MORDISCO_LENTITUD_S) * 20); }
    public static int mordiscoNauseaTicks() { return i(MORDISCO_NAUSEA_S) * 20; }
    public static int mordiscoDesangradoNivel() { return i(MORDISCO_DESANGRADO_NIVEL); }
    public static int mordiscoDesangradoTicks() { return i(MORDISCO_DESANGRADO_S) * 20; }
    public static int mordiscoDebilidadTicks() { return i(MORDISCO_DEBILIDAD_S) * 20; }
    public static float desangradoDano() { return (float) d(DESANGRADO_DANO); }
    public static int venenoEnfriamientoTicks() { return i(VENENO_ENFRIAMIENTO_S) * 20; }
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

    // ------------------------------------------------------------------ bromas (duraciones en ticks)
    public static boolean bromaUwu() { return b(BROMA_UWU); }
    public static int bromaUwuTicks() { return i(BROMA_UWU_S) * 20; }
    public static boolean bromaControles() { return b(BROMA_CONTROLES); }
    public static int bromaControlesTicks() { return i(BROMA_CONTROLES_S) * 20; }
    public static boolean bromaCartel() { return b(BROMA_CARTEL); }
    public static int bromaCartelTicks() { return i(BROMA_CARTEL_S) * 20; }
    public static boolean bromaDuende() { return b(BROMA_DUENDE); }
    public static int bromaDuendeTicks() { return i(BROMA_DUENDE_S) * 20; }
    public static int bromaDuendeCadaTicks() { return Math.max(100, i(BROMA_DUENDE_CADA_S) * 20); }
    public static boolean bromaReves() { return b(BROMA_REVES); }
    public static int bromaRevesTicks() { return i(BROMA_REVES_S) * 20; }
    public static int bromaEnfriamientoTicks() { return i(BROMA_ENFRIAMIENTO_S) * 20; }

    // ------------------------------------------------------------------ rueda de Catalina
    public static int ruedaAciertosPorCorrea() { return Math.max(1, i(RUEDA_ACIERTOS)); }
    public static float ruedaDanoFallo() { return (float) d(RUEDA_DANO); }
    public static boolean ruedaPuedeMatar() { return b(RUEDA_MUERTE); }
    public static int ruedaMuerteTicks() { return i(RUEDA_MUERTE_S) * 20; }

    // ------------------------------------------------------------------ pista de baile
    public static int baileRadioPistas() { return i(BAILE_RADIO); }
    // ------------------------------------------------------------------ estática (tiempos en ticks)
    public static int estaticaDuracionTicks() { return i(EST_DURACION_S) * 20; }
    public static boolean estaticaLecheCura() { return b(EST_LECHE); }
    public static boolean estaticaCinematica() { return b(EST_CINEMATICA); }
    public static double estaticaVelocidad() { return d(EST_VELOCIDAD); }
    public static double estaticaGravedad() { return d(EST_GRAVEDAD); }
    public static float estaticaCaida() { return (float) d(EST_CAIDA); }
    public static boolean estaticaToque() { return b(EST_TOQUE); }
    public static float estaticaToqueDano() { return (float) d(EST_TOQUE_DANO); }
    public static int estaticaToqueLentitudTicks() { return i(EST_TOQUE_LENTITUD_S) * 20; }
    public static int estaticaToqueCorromperTicks() { return i(EST_TOQUE_CORROMPER_S) * 20; }
    public static boolean estaticaParpadeo() { return b(EST_PARPADEO); }
    public static double estaticaParpadeoDistancia() { return d(EST_PARPADEO_DIST); }
    public static int estaticaParpadeoEnfriamiento() { return i(EST_PARPADEO_ENF_S) * 20; }
    public static boolean estaticaPantallas() { return b(EST_PANTALLAS); }
    public static int estaticaPantallasRadio() { return i(EST_PANTALLAS_RADIO); }
    public static int estaticaPantallasEnfriamiento() { return i(EST_PANTALLAS_ENF_S) * 20; }
    public static int estaticaPantallasMaxTicks() { return i(EST_PANTALLAS_MAX_S) * 20; }
    public static boolean estaticaLuces() { return b(EST_LUCES); }
    public static int estaticaLucesRadio() { return i(EST_LUCES_RADIO); }
    public static int estaticaLucesVueltaTicks() { return i(EST_LUCES_VUELTA_S) * 20; }
    public static boolean estaticaImitar() { return b(EST_IMITAR); }
    public static int estaticaImitarEnfriamiento() { return i(EST_IMITAR_ENF_S) * 20; }
    public static boolean estaticaCharcos() { return b(EST_CHARCOS); }
    public static int estaticaCharcosMax() { return i(EST_CHARCOS_MAX); }
    public static int estaticaCharcosVidaTicks() { return i(EST_CHARCOS_VIDA_S) * 20; }
    public static boolean estaticaOscuridad() { return b(EST_OSCURIDAD); }
    public static int estaticaOscuridadLuz() { return i(EST_OSCURIDAD_LUZ); }
    public static int estaticaOscuridadTicks() { return (int) Math.round(d(EST_OSCURIDAD_QUIETO_S) * 20); }
    public static boolean estaticaArrastrar() { return b(EST_ARRASTRAR); }
    public static double estaticaArrastrarAlcance() { return d(EST_ARRASTRAR_ALCANCE); }
    public static double estaticaArrastrarDistancia() { return d(EST_ARRASTRAR_DIST); }
    public static int estaticaArrastrarEnfriamiento() { return i(EST_ARRASTRAR_ENF_S) * 20; }
    public static boolean estaticaFragmentarse() { return b(EST_FRAGMENTARSE); }
    public static int estaticaFragmentarseEnfriamiento() { return i(EST_FRAGMENTARSE_ENF_S) * 20; }
    public static double estaticaTentaculosAlcance() { return d(EST_TENTACULOS_ALCANCE); }
    public static boolean estaticaAtraer() { return b(EST_ATRAER); }
    public static int estaticaAtraerEnfriamiento() { return i(EST_ATRAER_ENF_S) * 20; }
    public static boolean estaticaLanzar() { return b(EST_LANZAR); }
    public static double estaticaLanzarFuerza() { return d(EST_LANZAR_FUERZA); }
    public static int estaticaLanzarEnfriamiento() { return i(EST_LANZAR_ENF_S) * 20; }
    public static boolean estaticaEstampar() { return b(EST_ESTAMPAR); }
    public static float estaticaEstamparDano() { return (float) d(EST_ESTAMPAR_DANO); }
    public static double estaticaEstamparAltura() { return d(EST_ESTAMPAR_ALTURA); }
    public static int estaticaEstamparEnfriamiento() { return i(EST_ESTAMPAR_ENF_S) * 20; }

    // ------------------------------------------------------------------ juegos de mesa (tiempos en ticks)
    public static int mesasTurnoTicks() { return i(MESAS_TURNO_S) * 20; }
    public static int mesasAusenciaTicks() { return Math.max(60, i(MESAS_AUSENCIA_S) * 20); }
    public static boolean mesasVictoriasContraCpu() { return b(MESAS_VICTORIAS_CPU); }
}
