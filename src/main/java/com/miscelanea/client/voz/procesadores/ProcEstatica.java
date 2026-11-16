package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/**
 * Estática: la voz como por una radio rota, pero que se entiende. Banda de teléfono (300-3200 Hz), muestreo bajado a
 * ~12 kHz con ~6 bits (sample-and-hold + cuantización), una cama de nieve suave que sigue a la voz, algún chasquido y
 * cortes cortos (10-30 ms, más o menos uno por segundo) en los que solo queda la nieve. (Antes: 6 kHz y 4 bits, nieve
 * tan alta como media voz y unos 9 cortes por segundo de hasta 90 ms: no se entendía nada.)
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
        int paso = 4 + Math.min(2, amp);                 // 48000 / 4 = 12 kHz (con la máxima carga, 8 kHz)
        float niveles = Math.max(24F, 48F - 6F * amp);   // ~5-6 bits
        float cama = 0.03F + 0.01F * Math.min(3, amp);
        for (int i = 0; i < b.length; i++) {
            float x = bajo.tick(alto.tick(b[i])) * 1.6F;
            float e = env.tick(x);
            if (cuenta++ % paso == 0) retenido = Math.round(Dsp.suave(x * 1.5F) * niveles) / niveles;
            float y = retenido;
            // cortes: la señal se va y solo queda la nieve
            if (corte > 0) {
                corte--;
                y = 0;
            } else if (r.entero(48000) < 1) corte = 480 + r.entero(960);   // ~1 por segundo, de 10 a 30 ms
            float n = r.tick() * (cama + e * 0.12F);
            if (r.entero(12000) == 0) n += (r.tick() > 0 ? 0.35F : -0.35F);   // chasquido
            b[i] = Dsp.suave(y * 0.9F + n);
        }
        Dsp.aShort(b, frame);
    }
}
