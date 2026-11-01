package com.miscelanea.oca;

/** Tablero de la Oca: espiral de 64 casillas (0 = salida, 63 = jardín) en una cuadrícula de 8x8 con marco de 32 px. */
public final class OcaGeo {
    public static final int N = 512, MARCO = 32, CELDA = 56;
    public static final double LADO = 2.0;
    /** Columna y fila (fila 0 arriba) de cada casilla. */
    public static final int[][] CELDAS = new int[64][];

    static {
        int c = 0, f = 7, i = 0;
        int izq = 0, der = 7, arriba = 0, abajo = 7;
        int[][] dir = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};
        int d = 0;
        CELDAS[i++] = new int[]{c, f};
        while (i < 64) {
            int nc = c + dir[d][0], nf = f + dir[d][1];
            boolean fuera = nc < izq || nc > der || nf < arriba || nf > abajo || ocupada(nc, nf, i);
            if (fuera) {
                d = (d + 1) % 4;
                continue;
            }
            c = nc;
            f = nf;
            CELDAS[i++] = new int[]{c, f};
        }
    }

    private static boolean ocupada(int c, int f, int hasta) {
        for (int k = 0; k < hasta; k++) if (CELDAS[k][0] == c && CELDAS[k][1] == f) return true;
        return false;
    }

    private OcaGeo() {}

    public static double[] centro(int casilla) {
        int[] cf = CELDAS[Math.max(0, Math.min(63, casilla))];
        return new double[]{MARCO + CELDA * (cf[0] + 0.5), MARCO + CELDA * (cf[1] + 0.5)};
    }

    /** Sitio de cada jugador dentro de la casilla (cuatro huecos). */
    public static double[] hueco(int casilla, int color) {
        double[] c = centro(casilla);
        return new double[]{c[0] + (color % 2 == 0 ? -12 : 12), c[1] + (color < 2 ? -10 : 13)};
    }
}
