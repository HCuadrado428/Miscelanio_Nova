package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/**
 * Estática: la voz como por una radio rota. Banda de teléfono (300-3200 Hz), muestreo bajado a ~6 kHz con pocos bits
 * (sample-and-hold + cuantización), una cama de nieve que sigue a la voz, chasquidos y cortes al azar (20-90 ms) en los
 * que solo queda la nieve.
 */
public class ProcEstatica implements ProcesadorVoz {
    private final Dsp.Biquad alto = Dsp.Biquad.pasoAlto(300, 0.7), bajo = Dsp.Biquad.pasoBajo(3200, 0.8);
    private final Dsp.Envolvente env = new Dsp.Envolvente(5, 120);
    private final Dsp.Ruido r = new Dsp.Ruido();
    private float[] b;
    private float retenido;
    private int cuenta, corte;

    @Override
    public void procesar(short[] frame, int amp) {
        b = Dsp.aFloat(frame, b);
        int paso = 8 + Math.min(4, amp);                 // 48000 / 8 = 6 kHz
        float niveles = Math.max(6F, 14F - 2F * amp);    // ~4 bits
        float cama = 0.05F + 0.02F * Math.min(3, amp);
        for (int i = 0; i < b.length; i++) {
            float x = bajo.tick(alto.tick(b[i])) * 1.6F;
            float e = env.tick(x);
            if (cuenta++ % paso == 0) retenido = Math.round(Dsp.suave(x * 1.5F) * niveles) / niveles;
            float y = retenido;
            // cortes: la señal se va y solo queda la nieve
            if (corte > 0) {
                corte--;
                y = 0;
            } else if (r.entero(48000) < 9) corte = 960 + r.entero(3400);
            float n = r.tick() * (cama + e * 0.55F);
            if (r.entero(4000) == 0) n += (r.tick() > 0 ? 0.6F : -0.6F);   // chasquido
            b[i] = Dsp.suave(y * 0.9F + n);
        }
        Dsp.aShort(b, frame);
    }
}
