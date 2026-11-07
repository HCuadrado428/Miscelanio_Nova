package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/** Grave (gigante): tono ×0.65 (más bajo cuanto mayor el amplificador) con un retumbo corto que lo hace enorme. */
public class ProcGrave implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(0.65);
    /** Cuerpo: un poco más de graves (paso bajo sumado) sin el eco corto de antes, que sonaba a lata. */
    private final Dsp.Biquad pecho = Dsp.Biquad.pasoBajo(260, 0.7);
    private final Dsp.Biquad cuerpo = Dsp.Biquad.pasoBajo(3400, 0.6);
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        tono.setRazon(Math.max(0.5, 0.68 - 0.06 * amp));
        b = Dsp.aFloat(frame, b);
        tono.procesar(b);
        for (int i = 0; i < b.length; i++) {
            float y = cuerpo.tick(b[i]);
            b[i] = Dsp.suave((y + pecho.tick(y) * 0.6F) * 1.05F);
        }
        Dsp.aShort(b, frame);
    }
}
