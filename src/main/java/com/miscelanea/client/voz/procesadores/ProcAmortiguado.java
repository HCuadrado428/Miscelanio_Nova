package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/** Amortiguado (niebla): como a través de una pared de algodón; paso bajo fuerte (dos etapas) y un eco difuso. */
public class ProcAmortiguado implements ProcesadorVoz {
    private final Dsp.Biquad bajo1 = Dsp.Biquad.pasoBajo(600, 0.7);
    private final Dsp.Biquad bajo2 = Dsp.Biquad.pasoBajo(700, 0.7);
    private final Dsp.Eco eco = new Dsp.Eco(90, 0.3F, 0.3F, 900);
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        double fc = Math.max(450, 950 - 150 * amp);
        bajo1.setPasoBajo(fc, 0.7);
        bajo2.setPasoBajo(fc * 1.15, 0.7);
        b = Dsp.aFloat(frame, b);
        for (int i = 0; i < b.length; i++) b[i] = eco.tick(bajo2.tick(bajo1.tick(b[i])));
        Dsp.aShort(b, frame);
    }
}
