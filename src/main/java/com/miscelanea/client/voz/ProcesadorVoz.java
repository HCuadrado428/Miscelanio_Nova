package com.miscelanea.client.voz;

/**
 * Un cambio de voz. Se crea UNA instancia por hablante (guarda estado entre fotogramas: búferes, filtros, fases) y
 * recibe los fotogramas de Simple Voice Chat tal cual llegan: PCM 16 bits, mono, 48 kHz, 20 ms (960 muestras), que
 * se modifican en el sitio. Se llama desde el hilo de audio del voicechat, nunca desde el del juego.
 */
public interface ProcesadorVoz {
    /** Frecuencia de muestreo y tamaño de fotograma del voicechat. */
    int SR = 48000, FRAME = 960;

    /**
     * @param frame muestras a modificar en el sitio
     * @param amp   amplificador del efecto que provoca la voz (0 = nivel I)
     */
    void procesar(short[] frame, int amp);
}
