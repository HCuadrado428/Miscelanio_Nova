package com.miscelanea.flota;

/** Tablero de hundir la flota (512): el mar del jugador 0 cerca de quien lo colocó, el del jugador 1 al otro lado. */
public final class FlotaGeo {
    public static final int N = 512, CELDA = 20, X0 = 156;
    public static final double LADO = 2.0;

    private FlotaGeo() {}

    public static int y0(int mar) {
        return mar == 0 ? 286 : 26;
    }

    public static double[] centro(int mar, int celda) {
        return new double[]{X0 + CELDA * (celda % 10 + 0.5), y0(mar) + CELDA * (celda / 10 + 0.5)};
    }

    /** {mar, celda} bajo el punto, o null. */
    public static int[] en(double tx, double ty) {
        for (int m = 0; m < 2; m++) {
            int c = (int) Math.floor((tx - X0) / CELDA), f = (int) Math.floor((ty - y0(m)) / CELDA);
            if (c >= 0 && c < 10 && f >= 0 && f < 10) return new int[]{m, f * 10 + c};
        }
        return null;
    }
}
