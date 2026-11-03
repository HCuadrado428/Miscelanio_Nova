package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/**
 * Susurro (sombra): se quita el cuerpo de la voz (paso alto) y se le añade aire (ruido filtrado que sigue la
 * envolvente de la voz, que es lo que hace que un susurro parezca susurro); todo bajito.
 */
public class ProcSusurro implements ProcesadorVoz {
    private final Dsp.Biquad alto = Dsp.Biquad.pasoAlto(1100, 0.7);
    private final Dsp.Biquad alto2 = Dsp.Biquad.pasoAlto(900, 0.7);
    private final Dsp.Biquad aireBanda = Dsp.Biquad.pasoBajo(5200, 0.5);
    private final Dsp.Biquad aireAlto = Dsp.Biquad.pasoAlto(1500, 0.6);
    private final Dsp.Envolvente env = new Dsp.Envolvente(4, 60);
    private final Dsp.Ruido ruido = new Dsp.Ruido();
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        b = Dsp.aFloat(frame, b);
        float aire = 0.9F + 0.3F * amp;
        for (int i = 0; i < b.length; i++) {
            float x = b[i];
            float e = env.tick(x);
            float voz = alto2.tick(alto.tick(x));
            float n = aireAlto.tick(aireBanda.tick(ruido.tick()));
            b[i] = (voz * 0.45F + n * e * aire * 2.2F) * 0.7F;
        }
        Dsp.aShort(b, frame);
    }
}
