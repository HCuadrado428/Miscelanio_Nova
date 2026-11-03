package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/**
 * Agudo (enano): tono ×1.45, menos que el helio, y un poco de brillo (paso alto suave) para que suene "pequeño".
 * Hablar más deprisa no se puede en tiempo real sin acumular retraso, así que se queda en el tono.
 */
public class ProcAgudo implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(1.45);
    private final Dsp.Biquad brillo = Dsp.Biquad.pasoAlto(220, 0.7);
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        tono.setRazon(Math.min(1.9, 1.45 + 0.12 * amp));
        b = Dsp.aFloat(frame, b);
        tono.procesar(b);
        brillo.procesar(b);
        Dsp.aShort(b, frame);
    }
}
