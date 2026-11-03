package com.miscelanea.client.voz.dsp;

/**
 * Piezas de procesado de audio en Java puro (sin librerías) para los cambios de voz: conversión, filtros biquad,
 * desplazador de tono en tiempo real, línea de retardo con realimentación (eco/reverb barata) y ruido.
 * Todo trabaja en float [-1, 1] a 48 kHz.
 */
public final class Dsp {
    private Dsp() {}

    public static final int SR = 48000;

    // ------------------------------------------------------------------ conversión
    public static float[] aFloat(short[] in, float[] out) {
        if (out == null || out.length != in.length) out = new float[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] / 32768F;
        return out;
    }

    public static void aShort(float[] in, short[] out) {
        for (int i = 0; i < out.length; i++) {
            float v = in[i];
            if (v > 1F) v = 1F;
            else if (v < -1F) v = -1F;
            out[i] = (short) Math.round(v * 32767F);
        }
    }

    /** Saturación suave (tanh aproximada). */
    public static float suave(float x) {
        if (x > 3F) return 1F;
        if (x < -3F) return -1F;
        float x2 = x * x;
        return x * (27F + x2) / (27F + 9F * x2);
    }

    // ------------------------------------------------------------------ filtro biquad (fórmulas RBJ)
    public static final class Biquad {
        private double b0, b1, b2, a1, a2;
        private double z1, z2;

        public static Biquad pasoBajo(double fc, double q) {
            Biquad f = new Biquad();
            f.setPasoBajo(fc, q);
            return f;
        }

        public static Biquad pasoAlto(double fc, double q) {
            Biquad f = new Biquad();
            f.setPasoAlto(fc, q);
            return f;
        }

        public void setPasoBajo(double fc, double q) {
            double w = 2 * Math.PI * fc / SR, cs = Math.cos(w), sn = Math.sin(w), al = sn / (2 * q);
            double a0 = 1 + al;
            b0 = (1 - cs) / 2 / a0;
            b1 = (1 - cs) / a0;
            b2 = b0;
            a1 = -2 * cs / a0;
            a2 = (1 - al) / a0;
        }

        public void setPasoAlto(double fc, double q) {
            double w = 2 * Math.PI * fc / SR, cs = Math.cos(w), sn = Math.sin(w), al = sn / (2 * q);
            double a0 = 1 + al;
            b0 = (1 + cs) / 2 / a0;
            b1 = -(1 + cs) / a0;
            b2 = b0;
            a1 = -2 * cs / a0;
            a2 = (1 - al) / a0;
        }

        public float tick(float x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return (float) y;
        }

        public void procesar(float[] b) {
            for (int i = 0; i < b.length; i++) b[i] = tick(b[i]);
        }
    }

    // ------------------------------------------------------------------ desplazador de tono (dos lecturas sobre una línea de retardo, fundido seno/coseno)
    /**
     * Cambio de tono en tiempo real, en el dominio del tiempo: dos cabezales leen de una línea de retardo a una
     * velocidad distinta de la de escritura (más rápido = más agudo) dentro de una ventana de {@link #VENTANA}
     * muestras (40 ms) y se funden con ganancias sin²/cos² para que el salto al volver al principio no se oiga.
     * Latencia máxima: la ventana (≤ 40 ms). Calidad "de juguete" pero continua entre fotogramas; la razón puede
     * cambiar muestra a muestra ({@link #setRazon}), lo que sirve para vibratos y flutter.
     */
    public static final class Tono {
        public static final int VENTANA = 1920;
        private static final int N = 8192, MASK = N - 1;
        private final float[] buf = new float[N];
        private int w;
        private double d1;        // retardo (muestras) del cabezal 1; el 2 va media ventana por delante
        private double razon = 1;

        public Tono(double razon) {
            setRazon(razon);
            d1 = VENTANA * 0.25;
        }

        public void setRazon(double r) {
            razon = Math.max(0.3, Math.min(3.0, r));
        }

        public double razon() {
            return razon;
        }

        private float leer(double retardo) {
            double pos = w - retardo;
            int i0 = (int) Math.floor(pos);
            float fr = (float) (pos - i0);
            float a = buf[i0 & MASK], b = buf[(i0 + 1) & MASK];
            return a + (b - a) * fr;
        }

        public float tick(float x) {
            buf[w & MASK] = x;
            // el cabezal avanza a 'razon' por cada muestra escrita: el retardo cambia (1 - razon) por muestra
            d1 += 1 - razon;
            if (d1 < 1) d1 += VENTANA;
            else if (d1 >= VENTANA + 1) d1 -= VENTANA;
            double d2 = d1 + VENTANA / 2.0;
            if (d2 >= VENTANA + 1) d2 -= VENTANA;
            double ph = Math.PI * (d1 - 1) / VENTANA;
            float g1 = (float) Math.sin(ph), g2 = (float) Math.cos(ph);
            float y = leer(d1) * g1 * g1 + leer(d2) * g2 * g2;
            w++;
            return y;
        }

        public void procesar(float[] b) {
            for (int i = 0; i < b.length; i++) b[i] = tick(b[i]);
        }
    }

    // ------------------------------------------------------------------ eco con realimentación (reverb barata)
    public static final class Eco {
        private final float[] buf;
        private int w;
        private final int retardo;
        private float fb, mezcla;
        private final Biquad apagado;

        /** @param ms retardo; @param fb realimentación 0..0.95; @param mezcla cuánto eco se suma; @param fcCola paso bajo de la cola */
        public Eco(int ms, float fb, float mezcla, double fcCola) {
            retardo = Math.max(1, ms * SR / 1000);
            int n = 1;
            while (n < retardo + 1) n <<= 1;
            buf = new float[n];
            this.fb = fb;
            this.mezcla = mezcla;
            apagado = Biquad.pasoBajo(fcCola, 0.7);
        }

        public float tick(float x) {
            int mask = buf.length - 1;
            float e = buf[(w - retardo) & mask];
            float cola = apagado.tick(e);
            buf[w & mask] = x + cola * fb;
            w++;
            return x + cola * mezcla;
        }

        public void procesar(float[] b) {
            for (int i = 0; i < b.length; i++) b[i] = tick(b[i]);
        }
    }

    // ------------------------------------------------------------------ varios
    /** Seguidor de envolvente (ataque rápido, caída lenta). */
    public static final class Envolvente {
        private float e;
        private final float sub, baj;

        public Envolvente(float msSubida, float msBajada) {
            sub = (float) Math.exp(-1.0 / (msSubida * SR / 1000));
            baj = (float) Math.exp(-1.0 / (msBajada * SR / 1000));
        }

        public float tick(float x) {
            float a = Math.abs(x);
            e = a > e ? sub * e + (1 - sub) * a : baj * e + (1 - baj) * a;
            return e;
        }
    }

    /** Ruido blanco rápido (xorshift). */
    public static final class Ruido {
        private int s = 0x2545F491;

        public float tick() {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            return (s & 0xFFFF) / 32768F - 1F;
        }

        public int entero(int n) {
            tick();
            return (s >>> 1) % n;
        }
    }

    public static float rms(float[] b) {
        double s = 0;
        for (float v : b) s += v * v;
        return (float) Math.sqrt(s / b.length);
    }
}
