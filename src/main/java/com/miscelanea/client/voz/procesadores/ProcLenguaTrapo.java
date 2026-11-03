package com.miscelanea.client.voz.procesadores;

import com.miscelanea.client.voz.ProcesadorVoz;
import com.miscelanea.client.voz.dsp.Dsp;

/**
 * Lengua de trapo (borrachera): el tono se va arriba y abajo despacio (±15 % a 0,5 Hz), de vez en cuando se arrastra
 * una palabra (un tirón lento que dura unos cientos de ms) y todo suena un poco apagado (paso bajo suave).
 */
public class ProcLenguaTrapo implements ProcesadorVoz {
    private final Dsp.Tono tono = new Dsp.Tono(1.0);
    private final Dsp.Biquad apagado = Dsp.Biquad.pasoBajo(3200, 0.6);
    private final Dsp.Ruido r = new Dsp.Ruido();
    private double fase;
    private int tiron, tironTotal;
    private float[] b;

    @Override
    public void procesar(short[] frame, int amp) {
        double prof = Math.min(0.3, 0.15 + 0.05 * amp);
        double incr = 2 * Math.PI * 0.5 / SR;
        b = Dsp.aFloat(frame, b);
        if (tiron <= 0 && r.entero(100) < 4 + 2 * amp) tiron = tironTotal = SR * (180 + r.entero(260)) / 1000;
        for (int i = 0; i < b.length; i++) {
            fase += incr;
            if (fase > 2 * Math.PI) fase -= 2 * Math.PI;
            double razon = 1 + prof * Math.sin(fase);
            if (tiron > 0) {
                // tirón: baja hasta 0,8 y vuelve, con forma de campana
                double u = 1 - tiron / (double) tironTotal;
                razon *= 1 - 0.2 * Math.sin(Math.PI * u);
                tiron--;
            }
            tono.setRazon(razon);
            b[i] = apagado.tick(tono.tick(b[i]));
        }
        Dsp.aShort(b, frame);
    }
}
