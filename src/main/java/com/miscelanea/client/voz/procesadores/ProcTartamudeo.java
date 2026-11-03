package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/**
 * Tartamudeo: de vez en cuando se graba un trozo de 40–80 ms y se repite 2–4 veces con huecos (lo que llega
 * mientras tanto se pierde, como cuando se traba la lengua). Para no cortar en seco, los trozos tienen un
 * fundido corto en los bordes.
 */
public class ProcTartamudeo implements ProcesadorVoz {
    private static final int NORMAL = 0, GRABANDO = 1, REPITIENDO = 2;
    private static final int FUNDIDO = 96;
    private final Dsp.Ruido r = new Dsp.Ruido();
    private final float[] trozo = new float[SR * 80 / 1000];
    private int estado = NORMAL, largo, pos, repes, hueco, enHueco, espera;
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        b = Dsp.aFloat(frame, b);
        float energia = Dsp.rms(b);
        for (int i = 0; i < b.length; i++) {
            switch (estado) {
                case NORMAL -> {
                    if (i == 0 && espera-- <= 0 && energia > 0.02F && r.entero(100) < 7 + 5 * amp) {
                        estado = GRABANDO;
                        largo = SR * (40 + r.entero(41)) / 1000;
                        pos = 0;
                        repes = 2 + r.entero(Math.min(3, 2 + amp));
                        hueco = SR * (25 + r.entero(40)) / 1000;
                        trozo[pos++] = b[i];
                    }
                }
                case GRABANDO -> {
                    trozo[pos++] = b[i];
                    if (pos >= largo) {
                        estado = REPITIENDO;
                        pos = 0;
                        enHueco = hueco;
                    }
                }
                case REPITIENDO -> {
                    if (enHueco > 0) {
                        enHueco--;
                        b[i] = 0;
                    } else {
                        float g = 1F;
                        if (pos < FUNDIDO) g = pos / (float) FUNDIDO;
                        else if (largo - pos < FUNDIDO) g = (largo - pos) / (float) FUNDIDO;
                        b[i] = trozo[pos++] * g;
                        if (pos >= largo) {
                            pos = 0;
                            if (--repes <= 0) {
                                estado = NORMAL;
                                espera = 12 + r.entero(20);   // fotogramas de respiro
                            } else enHueco = hueco;
                        }
                    }
                }
                default -> {}
            }
        }
        Dsp.aShort(b, frame);
    }
}
