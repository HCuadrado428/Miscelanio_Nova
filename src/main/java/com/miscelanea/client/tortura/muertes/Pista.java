package com.miscelanea.client.tortura.muertes;

import net.minecraft.util.Mth;

/**
 * Una pista de fotogramas clave con curvas (la misma interpolación que la trampa de oso y que scratchpad/muertes.py).
 * Filas {tick, x, y, z, curva} (vector) o {tick, v, curva} (escalar); la curva de una fila es la del tramo que acaba en
 * ella: 0 lineal, 1 suave, 2 frena al llegar, 3 acelera, 4 se pasa y vuelve, 5 rebote (muelle), 6 salto al final.
 */
public final class Pista {
    private final float[][] k;
    private final boolean escalar;

    public Pista(float[][] filas) {
        this.k = filas;
        this.escalar = filas.length > 0 && filas[0].length == 3;
    }

    public static float curva(int e, float x) {
        x = Mth.clamp(x, 0F, 1F);
        switch (e) {
            case 1: return x * x * (3F - 2F * x);
            case 2: { float u = 1F - x; return 1F - u * u * u; }
            case 3: return x * x * x;
            case 4: { float c = 1.9F, u = x - 1F; return 1F + (c + 1F) * u * u * u + c * u * u; }
            case 5: return 1F - Mth.cos(x * Mth.PI * 2.5F) * (float) Math.exp(-5F * x) * (1F - x);
            case 6: return x >= 1F ? 1F : 0F;
            default: return x;
        }
    }

    /** Componente c (0..2) en el tick t (con fracción). Los escalares solo tienen la 0. */
    public float en(float t, int c) {
        int n = escalar ? 1 : 3;
        if (c >= n) return 0F;
        if (k.length == 0) return 0F;
        if (t <= k[0][0]) return k[0][1 + c];
        for (int i = 1; i < k.length; i++) {
            float[] a = k[i - 1], b = k[i];
            if (t <= b[0]) {
                float d = b[0] - a[0];
                float s = curva((int) b[1 + n], d <= 0F ? 1F : (t - a[0]) / d);
                return a[1 + c] + (b[1 + c] - a[1 + c]) * s;
            }
        }
        return k[k.length - 1][1 + c];
    }

    public float x(float t) {
        return en(t, 0);
    }

    public float[] v(float t) {
        return new float[]{en(t, 0), en(t, 1), en(t, 2)};
    }

    // ------------------------------------------------------------------ ruido (= muertes.py)
    public static float ruido(float t, int sem) {
        return Mth.sin(t * 2.3F + sem * 1.7F) * 0.5F + Mth.sin(t * 3.9F + sem * 2.9F) * 0.3F + Mth.sin(t * 6.1F + sem * 0.7F) * 0.2F;
    }

    /** Convulsión: ruido rápido y a tirones. */
    public static float sacudida(float t, int sem) {
        float r = ruido(t * 2.6F, sem + 11);
        return Math.copySign((float) Math.pow(Math.abs(r), 0.6), r);
    }
}
