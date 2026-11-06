package com.miscelanea.movil;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Sección {@code [movil]} de la configuración del servidor. La define {@link com.miscelanea.ConfigServidor} llamando a
 * {@link #definir} (así los valores viven aquí y el archivo compartido solo tiene una línea).
 */
public final class MovilConfig {
    private MovilConfig() {}

    private static ForgeConfigSpec.IntValue MAX_FOTOS, MAX_BYTES_FOTO, VIDEO_FPS, VIDEO_ANCHO, VIDEO_MAX_BYTES, HISTORIAL, MAX_CHATS,
            DISTANCIA, TIMBRE_S;
    private static ForgeConfigSpec.BooleanValue SIN_LIMITE, ENTRE_DIMENSIONES, VIDEO;
    private static ForgeConfigSpec.BooleanValue NOTAS_VOZ, MINEGRAM, UBICACION, BANCO, BANCO_OBJETOS, LINTERNA_REAL;
    private static ForgeConfigSpec.IntValue VOZ_SEGUNDOS, VOZ_MAX_BYTES, MG_MAX_POSTS, MG_MAX_COMENT, MG_MAX_PIE, MG_MAX_COMENTARIOS, UBI_MAX_MIN,
            BANCO_MAX_TRANSFER, BANCO_MAX_CONCEPTO, LINTERNA_NIVEL, LINTERNA_ALCANCE, BANCO_MOVIL_OP, BANCO_MOVIL_DIA, BANCO_CAJERO_DIST, PRECIO_FICHA;

    /** Fotos de 180x320 en JPEG: unos 12-25 KB cada una. El paquete del cliente al servidor no puede pasar de 32 KB. */
    public static final int TOPE_BYTES_FOTO = 30000;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Móvil: mensajes, cámara, llamadas y videollamadas entre jugadores.",
                "Las fotos y los mensajes se guardan EN EL SERVIDOR (carpeta del mundo: miscelanea_movil/ y data/miscelanea_movil.dat).").push("movil");
        MAX_FOTOS = b.comment("Fotos guardadas por jugador en su galería (al llenarse, hay que borrar alguna para hacer más).")
                .defineInRange("max_fotos", 40, 0, 500);
        MAX_BYTES_FOTO = b.comment("Tamaño máximo de cada foto en bytes (JPEG de 180x320; tope " + TOPE_BYTES_FOTO + ").")
                .defineInRange("max_bytes_foto", 30000, 4000, TOPE_BYTES_FOTO);
        HISTORIAL = b.comment("Mensajes que se guardan por chat (los más viejos se borran, con sus fotos).")
                .defineInRange("historial_mensajes", 300, 20, 5000);
        MAX_CHATS = b.comment("Chats (individuales + grupos) en los que puede estar cada jugador.").defineInRange("max_chats", 60, 1, 1000);
        SIN_LIMITE = b.comment("Llamadas sin límite de distancia (si es false, solo con jugadores a menos de 'distancia_llamadas' bloques).")
                .define("llamadas_sin_limite", true);
        DISTANCIA = b.comment("Distancia máxima de las llamadas cuando no son sin límite.").defineInRange("distancia_llamadas", 512, 16, 1000000);
        ENTRE_DIMENSIONES = b.comment("Permitir llamadas entre dimensiones distintas (Nether, End...) cuando no son sin límite.")
                .define("llamadas_entre_dimensiones", false);
        TIMBRE_S = b.comment("Segundos que suena una llamada antes de quedar como perdida.").defineInRange("timbre_segundos", 30, 5, 120);
        VIDEO = b.comment("Permitir videollamadas (imágenes pequeñas en JPEG reenviadas por el servidor solo al otro de la llamada).")
                .define("videollamadas", true);
        VIDEO_FPS = b.comment("Imágenes por segundo del vídeo (cada lado). Con 72x128 y 5 fps son unos 15-20 KB/s por sentido.")
                .defineInRange("video_fps", 5, 1, 12);
        VIDEO_ANCHO = b.comment("Ancho del vídeo en píxeles (el alto es ancho*16/9, en vertical como un móvil).")
                .defineInRange("video_ancho", 72, 36, 144);
        VIDEO_MAX_BYTES = b.comment("Tamaño máximo de cada imagen de vídeo (bytes); las más grandes se descartan.")
                .defineInRange("video_max_bytes", 9000, 1000, 30000);

        b.comment("Notas de voz en los chats (necesitan Simple Voice Chat en el cliente y en el servidor; se comprimen en Opus).",
                "Las notas se guardan EN EL SERVIDOR (miscelanea_movil/voz/) hasta que salen del historial del chat.").push("notas_voz");
        NOTAS_VOZ = b.comment("Permitir notas de voz.").define("activadas", true);
        VOZ_SEGUNDOS = b.comment("Duración máxima de cada nota (segundos).").defineInRange("max_segundos", 30, 3, 120);
        VOZ_MAX_BYTES = b.comment("Tamaño máximo de cada nota en bytes (30 s en Opus son unos 60-110 KB).").defineInRange("max_bytes", 160000, 8000, 900000);
        b.pop();

        b.comment("Minegram: red social del servidor (fotos con pie, me gusta, comentarios, seguir).",
                "Las publicaciones y sus fotos se guardan EN EL SERVIDOR (data/miscelanea_minegram.dat y miscelanea_movil/minegram/).").push("minegram");
        MINEGRAM = b.comment("Activar la app Minegram.").define("activada", true);
        MG_MAX_POSTS = b.comment("Publicaciones como máximo por jugador (al llegar, hay que borrar alguna).").defineInRange("max_posts_jugador", 30, 1, 1000);
        MG_MAX_PIE = b.comment("Longitud máxima del pie de foto.").defineInRange("max_pie", 150, 0, 256);
        MG_MAX_COMENT = b.comment("Longitud máxima de cada comentario.").defineInRange("max_comentario", 120, 10, 256);
        MG_MAX_COMENTARIOS = b.comment("Comentarios como máximo por publicación (los más viejos se borran).").defineInRange("max_comentarios_post", 100, 5, 1000);
        b.pop();

        b.comment("Ubicación en tiempo real: compartir tu posición con otros jugadores durante unos minutos (reales).").push("ubicacion");
        UBICACION = b.comment("Permitir compartir la ubicación.").define("activada", true);
        UBI_MAX_MIN = b.comment("Minutos máximos que se puede compartir de una vez (las opciones son 15, 30 y 60).").defineInRange("max_minutos", 60, 15, 600);
        b.pop();

        b.comment("Banco y Bizum: saldo en el móvil, transferencias entre jugadores y envío de objetos. Con OmegaChips el dinero son",
                "chips (1 = moneda de cobre; hierro 9, oro 81, diamante 729, bloques x4); sin él, esmeraldas.",
                "El cajero (bloque) ingresa y retira sin límite; desde el móvil solo un poco.",
                "Comando de operadores: /miscelanea banco <jugador> ver|dar|quitar <cantidad>.").push("banco");
        BANCO = b.comment("Activar la app Banco.").define("activado", true);
        BANCO_OBJETOS = b.comment("Permitir enviar objetos del inventario a otro jugador conectado.").define("enviar_objetos", true);
        BANCO_MOVIL_OP = b.comment("Desde el móvil (lejos de un cajero): máximo por cada ingreso o retirada. 0 = solo en el cajero.")
                .defineInRange("movil_max_operacion", 81, 0, Integer.MAX_VALUE);
        BANCO_MOVIL_DIA = b.comment("Desde el móvil: máximo al día (sumando ingresos y retiradas; el día se reinicia a medianoche del servidor).")
                .defineInRange("movil_max_dia", 729, 0, Integer.MAX_VALUE);
        BANCO_CAJERO_DIST = b.comment("Distancia (bloques) a la que se puede usar un cajero.").defineInRange("cajero_distancia", 6, 2, 16);
        PRECIO_FICHA = b.comment("Precio de cada ficha de recreativa en el cajero (en chips o esmeraldas). 0 = gratis.")
                .defineInRange("precio_ficha", 9, 0, 100000);
        BANCO_MAX_TRANSFER = b.comment("Dinero como máximo por transferencia (Bizum).").defineInRange("max_transferencia", 100000, 1, Integer.MAX_VALUE);
        BANCO_MAX_CONCEPTO = b.comment("Longitud máxima del concepto.").defineInRange("max_concepto", 40, 0, 120);
        b.pop();

        b.comment("Linterna: ilumina de verdad colocando bloques de luz invisibles (minecraft:light) en el aire delante del jugador.",
                "Nunca sustituye bloques que no sean aire y los quita al moverse, apagarla, salir, morir o parar el servidor.").push("linterna");
        LINTERNA_REAL = b.comment("Luz de verdad (si es false, solo el efecto visual en el cliente).").define("luz_real", true);
        LINTERNA_NIVEL = b.comment("Nivel de luz del haz (1-15).").defineInRange("nivel", 12, 1, 15);
        LINTERNA_ALCANCE = b.comment("Distancia máxima del haz en bloques.").defineInRange("alcance", 10, 2, 24);
        b.pop();
        b.pop();
    }

    private static int i(ForgeConfigSpec.IntValue v) {
        try {
            return com.miscelanea.ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v == null ? 0 : v.getDefault();
        }
    }

    private static boolean b(ForgeConfigSpec.BooleanValue v) {
        try {
            return com.miscelanea.ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v != null && v.getDefault();
        }
    }

    public static int maxFotos() { return i(MAX_FOTOS); }
    public static int maxBytesFoto() { return Math.min(TOPE_BYTES_FOTO, i(MAX_BYTES_FOTO)); }
    public static int historial() { return i(HISTORIAL); }
    public static int maxChats() { return i(MAX_CHATS); }
    public static boolean sinLimite() { return b(SIN_LIMITE); }
    public static int distancia() { return i(DISTANCIA); }
    public static boolean entreDimensiones() { return b(ENTRE_DIMENSIONES); }
    public static int timbreTicks() { return i(TIMBRE_S) * 20; }
    public static boolean video() { return b(VIDEO); }
    public static int videoFps() { return i(VIDEO_FPS); }
    public static int videoAncho() { return i(VIDEO_ANCHO) & ~1; }
    public static int videoAlto() { return (videoAncho() * 16 / 9) & ~1; }
    public static int videoMaxBytes() { return i(VIDEO_MAX_BYTES); }
    public static boolean notasVoz() { return b(NOTAS_VOZ); }
    public static int vozSegundos() { return i(VOZ_SEGUNDOS); }
    public static int vozMaxBytes() { return i(VOZ_MAX_BYTES); }
    public static boolean minegram() { return b(MINEGRAM); }
    public static int mgMaxPosts() { return i(MG_MAX_POSTS); }
    public static int mgMaxPie() { return i(MG_MAX_PIE); }
    public static int mgMaxComentario() { return i(MG_MAX_COMENT); }
    public static int mgMaxComentarios() { return i(MG_MAX_COMENTARIOS); }
    public static boolean ubicacion() { return b(UBICACION); }
    public static int ubiMaxMinutos() { return i(UBI_MAX_MIN); }
    public static boolean banco() { return b(BANCO); }
    public static boolean bancoObjetos() { return b(BANCO_OBJETOS); }
    public static int bancoMaxTransferencia() { return i(BANCO_MAX_TRANSFER); }
    public static int bancoMaxConcepto() { return i(BANCO_MAX_CONCEPTO); }
    public static int bancoMovilOperacion() { return i(BANCO_MOVIL_OP); }
    public static int bancoMovilDia() { return i(BANCO_MOVIL_DIA); }
    public static int bancoCajeroDistancia() { return i(BANCO_CAJERO_DIST); }
    public static int precioFicha() { return i(PRECIO_FICHA); }
    public static boolean linternaReal() { return b(LINTERNA_REAL); }
    public static int linternaNivel() { return i(LINTERNA_NIVEL); }
    public static int linternaAlcance() { return i(LINTERNA_ALCANCE); }
}
