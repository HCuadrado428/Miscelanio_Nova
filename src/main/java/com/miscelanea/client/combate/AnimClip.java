package com.miscelanea.client.combate;

import net.minecraft.util.Mth;

/**
 * Animación del jugador hecha de poses clave (las genera anim_tool.py). Cada pose tiene 24 valores:
 * cuerpo rot(3) y pos(3), brazo dcho(3), brazo izdo(3), pierna dcha(3), pierna izda(3), ítem(3), cabeza(3).
 * NaN = esa parte no se toca (se queda la de vanilla).
 */
public final class AnimClip {
    public static final int N = 24;
    public final String nombre;
    public final int dur;
    public final boolean bucle;
    private final float[] t;
    private final int[] e;
    private final float[][] p;

    public AnimClip(String nombre, int dur, boolean bucle, int ocultaDesde, int ocultaHasta, float[] t, int[] e, float[][] p) {
        this.nombre = nombre;
        this.dur = dur;
        this.bucle = bucle;
        this.t = t;
        this.e = e;
        this.p = p;
    }

    private static float ease(int tipo, float x) {
        x = Mth.clamp(x, 0, 1);
        return switch (tipo) {
            case 1 -> x * x * (3 - 2 * x);
            case 2 -> 1 - (1 - x) * (1 - x) * (1 - x);
            case 3 -> x * x * x;
            default -> x;
        };
    }

    /** Pose en el instante 'tiempo' (ticks) → out[24]. */
    public void muestra(float tiempo, float[] out) {
        if (bucle) tiempo = ((tiempo % dur) + dur) % dur;
        if (tiempo <= t[0]) {
            System.arraycopy(p[0], 0, out, 0, N);
            return;
        }
        for (int i = 0; i + 1 < t.length; i++) {
            if (tiempo <= t[i + 1]) {
                float k = ease(e[i + 1], (tiempo - t[i]) / Math.max(1e-4F, t[i + 1] - t[i]));
                float[] a = p[i], b = p[i + 1];
                for (int j = 0; j < N; j++) {
                    float va = a[j], vb = b[j];
                    if (Float.isNaN(va)) out[j] = vb;
                    else if (Float.isNaN(vb)) out[j] = va;
                    else out[j] = va + (vb - va) * k;
                }
                return;
            }
        }
        System.arraycopy(p[p.length - 1], 0, out, 0, N);
    }
}
