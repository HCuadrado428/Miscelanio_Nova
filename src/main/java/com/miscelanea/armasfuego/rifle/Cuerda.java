package com.miscelanea.armasfuego.rifle;

import net.minecraft.world.phys.Vec3;

/** Forma de la cuerda de una tirolina: recta tensa que cuelga un poco en el centro. La usan la física y el dibujo. */
public final class Cuerda {
    private Cuerda() {}

    public static double caida(Vec3 a, Vec3 b) {
        return Math.min(1.2, a.distanceTo(b) * 0.02);
    }

    public static Vec3 punto(Vec3 a, Vec3 b, double t) {
        double s = caida(a, b);
        return a.lerp(b, t).subtract(0, s * 4 * t * (1 - t), 0);
    }

    public static Vec3 tangente(Vec3 a, Vec3 b, double t) {
        double s = caida(a, b);
        return b.subtract(a).subtract(0, s * 4 * (1 - 2 * t), 0);
    }

    /** Parámetro (0..1) del punto de la cuerda más cercano a q (sobre la recta; la caída es pequeña). */
    public static double cercano(Vec3 a, Vec3 b, Vec3 q) {
        Vec3 d = b.subtract(a);
        double l2 = d.lengthSqr();
        if (l2 < 1e-6) return 0;
        double t = q.subtract(a).dot(d) / l2;
        t = Math.max(0, Math.min(1, t));
        // un par de pasos para corregir por la caída
        for (int i = 0; i < 2; i++) {
            Vec3 p = punto(a, b, t), tg = tangente(a, b, t);
            double g2 = tg.lengthSqr();
            if (g2 < 1e-6) break;
            t = Math.max(0, Math.min(1, t + q.subtract(p).dot(tg) / g2));
        }
        return t;
    }

    /** Distancia mínima entre el rayo (o + s·dir, 0 ≤ s ≤ alcance) y la cuerda; devuelve {distancia, s, t}. */
    public static double[] rayo(Vec3 a, Vec3 b, Vec3 o, Vec3 dir, double alcance) {
        int n = Math.max(4, Math.min(48, (int) (a.distanceTo(b) / 1.5)));
        double[] mejor = {Double.MAX_VALUE, 0, 0};
        Vec3 p0 = punto(a, b, 0);
        for (int i = 1; i <= n; i++) {
            Vec3 p1 = punto(a, b, i / (double) n);
            double[] r = segmentos(o, o.add(dir.scale(alcance)), p0, p1);
            if (r[0] < mejor[0]) {
                mejor[0] = r[0];
                mejor[1] = r[1] * alcance;
                mejor[2] = (i - 1 + r[2]) / n;
            }
            p0 = p1;
        }
        return mejor;
    }

    /** Distancia entre los segmentos p1-q1 y p2-q2; devuelve {distancia, s en el primero, t en el segundo}. */
    private static double[] segmentos(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        Vec3 d1 = q1.subtract(p1), d2 = q2.subtract(p2), r = p1.subtract(p2);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        double s, t;
        double c = d1.dot(r), b = d1.dot(d2), den = a * e - b * b;
        s = den > 1e-9 ? clamp((b * f - c * e) / den) : 0;
        t = e > 1e-9 ? (b * s + f) / e : 0;
        if (t < 0) {
            t = 0;
            s = a > 1e-9 ? clamp(-c / a) : 0;
        } else if (t > 1) {
            t = 1;
            s = a > 1e-9 ? clamp((b - c) / a) : 0;
        }
        Vec3 c1 = p1.add(d1.scale(s)), c2 = p2.add(d2.scale(t));
        return new double[]{c1.distanceTo(c2), s, t};
    }

    private static double clamp(double x) {
        return Math.max(0, Math.min(1, x));
    }
}
