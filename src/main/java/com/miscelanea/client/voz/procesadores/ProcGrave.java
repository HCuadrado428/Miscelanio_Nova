package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/** Grave (gigante): tono ×0.65 (más bajo cuanto mayor el amplificador) con un retumbo corto que lo hace enorme. */
public class ProcGrave implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(0.65);
    private final Dsp.Eco retumbo = new Dsp.Eco(38, 0.35F, 0.45F, 900);
    private final Dsp.Biquad cuerpo = Dsp.Biquad.pasoBajo(2800, 0.6);
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        tono.setRazon(Math.max(0.45, 0.65 - 0.07 * amp));
        b = Dsp.aFloat(frame, b);
        tono.procesar(b);
        retumbo.procesar(b);
        cuerpo.procesar(b);
        for (int i = 0; i < b.length; i++) b[i] = Dsp.suave(b[i] * 1.15F);
        Dsp.aShort(b, frame);
    }
}
