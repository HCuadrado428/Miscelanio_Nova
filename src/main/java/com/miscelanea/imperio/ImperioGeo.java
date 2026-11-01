package com.miscelanea.imperio;

/** Geometría del tablero en coordenadas de textura (0..512): casillas, centros y bandas de color. */
public final class ImperioGeo {
    public static final int N = 512, C = 67, S = 42;
    public static final double LADO = 2.0;     // bloques

    private ImperioGeo() {}

    public static int casillaEn(double tx, double ty) {
        if (tx < 0 || ty < 0 || tx >= N || ty >= N) return -1;
        if (ty >= N - C) {
            if (tx >= N - C) return 0;
            if (tx < C) return 10;
            return 1 + (int) ((N - C - tx) / S);
        }
        if (tx < C) {
            if (ty < C) return 20;
            return 11 + (int) ((N - C - ty) / S);
        }
        if (ty < C) {
            if (tx >= N - C) return 30;
            return 21 + (int) ((tx - C) / S);
        }
        if (tx >= N - C) return 31 + (int) ((ty - C) / S);
        return -1;
    }

    /** Centro de la casilla (zona de fichas, separada de la banda de color). */
    public static double[] centro(int i) {
        int lado = i / 10, k = i % 10;
        if (k == 0) return switch (lado) {
            case 0 -> new double[]{N - C / 2.0, N - C / 2.0};
            case 1 -> new double[]{C / 2.0, N - C / 2.0};
            case 2 -> new double[]{C / 2.0, C / 2.0};
            default -> new double[]{N - C / 2.0, C / 2.0};
        };
        double a = S * (k - 1) + S / 2.0;
        return switch (lado) {
            case 0 -> new double[]{N - C - a, N - 26};
            case 1 -> new double[]{26, N - C - a};
            case 2 -> new double[]{C + a, 26};
            default -> new double[]{N - 26, C + a};
        };
    }

    /** Centro de la banda de color (donde van las casas) y dirección a lo largo de la banda. */
    public static double[] banda(int i) {
        int lado = i / 10, k = i % 10;
        double a = S * (k - 1) + S / 2.0;
        return switch (lado) {
            case 0 -> new double[]{N - C - a, N - C + 7.5, 1, 0};
            case 1 -> new double[]{C - 7.5, N - C - a, 0, 1};
            case 2 -> new double[]{C + a, C - 7.5, 1, 0};
            default -> new double[]{N - C + 7.5, C + a, 0, 1};
        };
    }

    /** Lado del tablero (0 abajo = quien lo colocó, 1 izquierda, 2 arriba, 3 derecha) en el que se sienta cada jugador. */
    public static int asiento(int jugador) {
        return jugador % 4;
    }
}
