package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/** Ventilador: hablar contra las aspas; trémolo rápido (~25 Hz, profundidad 0,8) y un flutter de tono ligero. */
public class ProcVentilador implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(1.0);
    private double fase, faseFl;
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        double f = 25 + 6 * amp, prof = 0.8;
        double incr = 2 * Math.PI * f / SR, incrFl = 2 * Math.PI * (f * 0.5) / SR;
        b = Dsp.aFloat(frame, b);
        for (int i = 0; i < b.length; i++) {
            fase += incr;
            faseFl += incrFl;
            if (fase > 2 * Math.PI) fase -= 2 * Math.PI;
            if (faseFl > 2 * Math.PI) faseFl -= 2 * Math.PI;
            // ventana de las aspas: casi apagado entre aspa y aspa
            double g = 1 - prof * (0.5 - 0.5 * Math.cos(fase));
            tono.setRazon(1 + 0.03 * Math.sin(faseFl));
            b[i] = tono.tick(b[i]) * (float) g * 1.25F;
        }
        Dsp.aShort(b, frame);
    }
}
