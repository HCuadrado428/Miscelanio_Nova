package com.miscelanea.parchis;

import java.util.List;

/**
 * Geometría del tablero de parchís en coordenadas de textura (0..512). Cada brazo tiene tres carriles de 8 casillas;
 * el color c tiene su brazo, su casa en el cuadrante a su derecha y su pasillo en el carril central.
 * Marco local de un brazo: lx a lo ancho (-60..60) y ly hacia fuera desde el centro (0..256).
 */
public final class ParchisGeo {
    public static final int N = 512;
    public static final double LADO = 2.0, CARRIL = 40, FONDO = 24.5, INICIO = 60;

    private ParchisGeo() {}

    public static double[] tex(int brazo, double lx, double ly) {
        return switch (brazo & 3) {
            case 0 -> new double[]{256 + lx, 256 + ly};
            case 1 -> new double[]{256 + ly, 256 - lx};
            case 2 -> new double[]{256 - lx, 256 - ly};
            default -> new double[]{256 - ly, 256 + lx};
        };
    }

    /** Casilla del recorrido (0..67): brazo, carril (lx) y fila k (0 junto al centro .. 7 en el borde). */
    public static double[] casilla(int g, int hueco, int total) {
        int a = g / 17, m = g % 17, brazo, k;
        double lx;
        if (m == 0) {
            brazo = a; lx = 0; k = 7;
        } else if (m <= 8) {
            brazo = a; lx = CARRIL; k = 8 - m;
        } else {
            brazo = (a + 1) % 4; lx = -CARRIL; k = m - 9;
        }
        return tex(brazo, lx + desplaza(hueco, total), INICIO + FONDO * (k + 0.5));
    }

    private static double desplaza(int hueco, int total) {
        return total <= 1 ? 0 : (hueco == 0 ? -9.5 : 9.5);
    }

    public static double[] casa(int color, int f) {
        return tex(color, 124 + (f % 2) * 64, 124 + (f / 2) * 64);
    }

    public static double[] pasillo(int color, int q, int hueco, int total) {
        return tex(color, desplaza(hueco, total), INICIO + FONDO * (6 - q + 0.5));
    }

    public static double[] meta(int color, int f) {
        return tex(color, -27 + 18 * f, 44);
    }

    /** Dónde está dibujada una ficha según el estado de la partida. */
    public static double[] ficha(ParchisPartida p, int c, int f) {
        int q = p.prog[c][f];
        if (q == ParchisPartida.CASA) return casa(c, f);
        if (q == ParchisPartida.META) return meta(c, f);
        List<Integer> l = p.en(ParchisPartida.clave(c, q));
        int hueco = Math.max(0, l.indexOf(c * 4 + f)), total = l.size();
        if (q >= ParchisPartida.PASILLO) return pasillo(c, q - ParchisPartida.PASILLO, hueco, total);
        return casilla(ParchisPartida.casilla(c, q), hueco, total);
    }

    /** Posición de un progreso concreto (para animar el camino). */
    public static double[] paso(int c, int q, int f) {
        if (q == ParchisPartida.CASA) return casa(c, f);
        if (q == ParchisPartida.META) return meta(c, f);
        if (q >= ParchisPartida.PASILLO) return pasillo(c, q - ParchisPartida.PASILLO, 0, 1);
        return casilla(ParchisPartida.casilla(c, q), 0, 1);
    }

    /** Ficha del color c que hay bajo el punto (tx, ty), o -1. */
    public static int fichaEn(ParchisPartida p, int c, double tx, double ty) {
        int mejor = -1;
        double md = 13 * 13;
        for (int f = 0; f < 4; f++) {
            double[] q = ficha(p, c, f);
            double d = (q[0] - tx) * (q[0] - tx) + (q[1] - ty) * (q[1] - ty);
            if (d < md) {
                md = d;
                mejor = f;
            }
        }
        return mejor;
    }
}
