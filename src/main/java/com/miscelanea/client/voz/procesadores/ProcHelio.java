package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/** Helio: voz de pito, tono ×1.6 (más con el amplificador). */
public class ProcHelio implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(1.6);
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        tono.setRazon(Math.min(2.0, 1.6 + 0.15 * amp));
        b = Dsp.aFloat(frame, b);
        tono.procesar(b);
        Dsp.aShort(b, frame);
    }
}
