package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/**
 * Fantasma: voz lejana y con eco (retardo de 120 ms con realimentación 0,5), apagada (paso bajo) y con un temblor
 * lento de tono. (Hacer que venga de detrás no es posible desde este evento: el voicechat ya ha colocado la fuente.)
 */
public class ProcFantasma implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(1.0);
    private final Dsp.Eco eco = new Dsp.Eco(120, 0.5F, 0.6F, 2600);
    private final Dsp.Eco eco2 = new Dsp.Eco(73, 0.35F, 0.35F, 2000);
    private final Dsp.Biquad lejos = Dsp.Biquad.pasoBajo(2500, 0.7);
    private double fase;
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        double incr = 2 * Math.PI * 0.3 / SR;
        b = Dsp.aFloat(frame, b);
        float vol = Math.max(0.4F, 0.7F - 0.1F * amp);
        for (int i = 0; i < b.length; i++) {
            fase += incr;
            if (fase > 2 * Math.PI) fase -= 2 * Math.PI;
            tono.setRazon(0.97 + 0.04 * Math.sin(fase));
            float y = lejos.tick(tono.tick(b[i]));
            b[i] = eco2.tick(eco.tick(y)) * vol;
        }
        Dsp.aShort(b, frame);
    }
}
