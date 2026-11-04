package com.miscelanea.client.camara;

/**
 * Ruido suave de una dimensión (Perlin con gradientes por hash): para todo lo que tiembla o deriva en la cámara.
 * A diferencia de un aleatorio por fotograma, es continuo (y su derivada también), así que no salta aunque cambien los
 * FPS; y a diferencia de sumar senos, no se repite a ojos vista.
 */
public final class Ruido {
    private Ruido() {}

    private static float gradiente(int semilla, int i) {
        int h = i * 0x27D4EB2D ^ semilla * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 32767.5F - 1F;
    }

    /** Ruido en [-1, 1] (más o menos), suave; una "ondulación" por unidad de x. */
    public static float perlin(int semilla, double x) {
        double fl = Math.floor(x);
        int i = (int) fl;
        double f = x - fl;
        double g0 = gradiente(semilla, i) * f, g1 = gradiente(semilla, i + 1) * (f - 1.0);
        double u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
        return (float) ((g0 + (g1 - g0) * u) * 2.0);
    }

    /** Varias octavas (la segunda al doble de frecuencia y la mitad de fuerza...), normalizado a [-1, 1]. */
    public static float fbm(int semilla, double x, int octavas) {
        float s = 0F, a = 1F, total = 0F;
        double f = 1.0;
        for (int o = 0; o < octavas; o++) {
            s += perlin(semilla + o * 1013, x * f) * a;
            total += a;
            a *= 0.5F;
            f *= 2.0;
        }
        return s / total;
    }
}
