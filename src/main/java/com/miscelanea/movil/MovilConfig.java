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
}
