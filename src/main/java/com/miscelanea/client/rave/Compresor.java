package com.miscelanea.client.rave;

/**
 * Sube el volumen de lo que suena por la mesa de DJ hasta un nivel de fiesta sin que reviente: mide lo fuerte que va (media
 * de medio segundo), lo que va flojo lo sube (hasta {@code maximo} veces) y los picos los redondea en vez de cortarlos.
 * Minecraft no deja pasar de volumen 1, así que la única forma de que una canción grabada floja suene fuerte es subirle
 * el volumen al propio audio. Se usa con muestras de -1 a 1, una detrás de otra (en estéreo, intercaladas).
 */
public final class Compresor {
    /** Nivel medio que se busca (unos -13 dB): más o menos el de una canción moderna. */
    private static final float OBJETIVO = 0.22F;
    /** A partir de aquí los picos se redondean. */
    private static final float RODILLA = 0.75F;

    private final float maximo, kMedia, kBajar, kSubir;
    private float media = OBJETIVO * OBJETIVO, ganancia = 1F;

    /**
     * @param muestrasPorSegundo muestras que pasan por segundo (frecuencia por número de canales)
     * @param maximo             cuánto se sube como mucho lo que va flojo (1 = nada)
     */
    public Compresor(float muestrasPorSegundo, float maximo) {
        this.maximo = Math.max(1F, maximo);
        float n = Math.max(1000F, muestrasPorSegundo);
        kMedia = 1F / (0.5F * n);     // la media mira medio segundo
        kBajar = 1F / (0.03F * n);    // si de pronto va fuerte, baja enseguida
        kSubir = 1F / (1.5F * n);     // y sube despacio, para que no "respire"
    }

    public float ganancia() {
        return ganancia;
    }

    /** Una muestra (-1 a 1) → la misma, más fuerte y sin pasarse de 1. */
    public float procesar(float x) {
        media += (x * x - media) * kMedia;
        float quiere = OBJETIVO / (float) Math.sqrt(Math.max(media, 1.0e-6F));
        quiere = Math.max(1F, Math.min(maximo, quiere));
        ganancia += (quiere - ganancia) * (quiere < ganancia ? kBajar : kSubir);
        float y = x * ganancia, a = Math.abs(y);
        if (a > RODILLA) {
            a = RODILLA + (1F - RODILLA) * (float) Math.tanh((a - RODILLA) / (1F - RODILLA));
            y = y < 0 ? -a : a;
        }
        return y;
    }
}
