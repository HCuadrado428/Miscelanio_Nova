package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/** Gutural (sed de sangre): tono ×0,8, saturación (waveshaper suave) y un gruñido (modulación de amplitud ~30 Hz, ligera). */
public class ProcGutural implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(0.8);
    private final Dsp.Biquad cuerpo = Dsp.Biquad.pasoBajo(3000, 0.7);
    /** Antes de distorsionar se quitan los agudos: así la ronquera no chisporrotea. */
    private final Dsp.Biquad antes = Dsp.Biquad.pasoBajo(1800, 0.7);
    private double fase;
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        tono.setRazon(Math.max(0.6, 0.8 - 0.06 * amp));
        double incr = 2 * Math.PI * 30 / SR;
        float ganancia = 1.8F + 0.4F * amp, prof = 0.15F + 0.05F * amp;
        b = Dsp.aFloat(frame, b);
        for (int i = 0; i < b.length; i++) {
            fase += incr;
            if (fase > 2 * Math.PI) fase -= 2 * Math.PI;
            float y = Dsp.suave(antes.tick(tono.tick(b[i])) * ganancia) * 0.75F;
            float g = 1 - prof * (0.5F + 0.5F * (float) Math.sin(fase));
            b[i] = cuerpo.tick(y * g);
        }
        Dsp.aShort(b, frame);
    }
}
