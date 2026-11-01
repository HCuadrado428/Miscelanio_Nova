package com.miscelanea.domino;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometría de la mesa de dominó (textura 512): la cadena serpentea desde el centro (el brazo derecho baja al girar,
 * el izquierdo sube) y cada jugador tiene sus fichas de pie en su lado. Ficha tumbada: 32 x 16 px.
 */
public final class DominoGeo {
    public static final int N = 512;
    public static final double LADO = 2.0, XMAX = 150, MANO = 222, PASO_MANO = 19;
    public static final double POZO_X = 452, POZO_Y = 62;

    public record Colocada(int ficha, double x, double y, double ang, boolean volteada, boolean doble) {}

    private DominoGeo() {}

    public static double[] tex(int asiento, double lx, double ly) {
        return switch (asiento & 3) {
            case 0 -> new double[]{256 + lx, 256 + ly};
            case 1 -> new double[]{256 + ly, 256 - lx};
            case 2 -> new double[]{256 - lx, 256 - ly};
            default -> new double[]{256 - ly, 256 + lx};
        };
    }

    /** Dirección hacia el jugador sentado en un asiento (en coordenadas de textura). */
    public static double[] haciaFuera(int asiento) {
        double[] a = tex(asiento, 0, 0), b = tex(asiento, 0, 1);
        return new double[]{b[0] - a[0], b[1] - a[1]};
    }

    /** Todas las fichas de la cadena con su posición y giro, más el siguiente hueco de cada extremo. */
    public static final class Cadena {
        public final List<Colocada> fichas = new ArrayList<>();
        /** Por extremo (0 izquierdo, 1 derecho): punto donde empezaría la siguiente ficha y dirección. */
        public final double[][] hueco = new double[2][4];
    }

    public static Cadena cadena(DominoPartida p) {
        Cadena c = new Cadena();
        if (p.centro < 0) return c;
        boolean dc = DominoPartida.doble(p.centro);
        c.fichas.add(new Colocada(p.centro, 256, 256, dc ? Math.PI / 2 : 0, false, dc));
        double medio = dc ? 8 : 16;
        for (int lado = -1; lado <= 1; lado += 2) {
            List<DominoPartida.Pieza> l = lado < 0 ? p.izq : p.der;
            double px = 256 + lado * medio, py = 256, hx = lado, hy = 0;
            double ux = lado;          // última dirección horizontal
            for (DominoPartida.Pieza pz : l) {
                boolean d = DominoPartida.doble(pz.ficha());
                double len = d ? 16 : 32;
                if (hy == 0 && Math.abs(px + hx * len - 256) > XMAX) {
                    double nx = 0, ny = lado;
                    px = px - hx * 8 + nx * 8;
                    py = py - hy * 8 + ny * 8;
                    hx = nx;
                    hy = ny;
                }
                double ang = Math.atan2(hy, hx);
                if (d) c.fichas.add(new Colocada(pz.ficha(), px + hx * 8, py + hy * 8, ang + Math.PI / 2, false, true));
                else c.fichas.add(new Colocada(pz.ficha(), px + hx * 16, py + hy * 16, ang, DominoPartida.A[pz.ficha()] != pz.dentro(), false));
                px += hx * len;
                py += hy * len;
                if (hy != 0) {
                    double nx = -ux, ny = 0;
                    px = px - hx * 8 + nx * 8;
                    py = py - hy * 8 + ny * 8;
                    hx = nx;
                    hy = ny;
                    ux = nx;
                }
            }
            c.hueco[lado < 0 ? 0 : 1] = new double[]{px + hx * 12, py + hy * 12, hx, hy};
        }
        return c;
    }

    public static double[] mano(DominoPartida.Jugador j, int k) {
        int n = j.mano.size();
        return tex(j.asiento, (k - (n - 1) / 2.0) * PASO_MANO, MANO);
    }

    /** Índice de la ficha de la mano bajo el punto, o -1. */
    public static int manoEn(DominoPartida.Jugador j, double tx, double ty) {
        for (int k = 0; k < j.mano.size(); k++) {
            double[] q = mano(j, k);
            double[] fuera = haciaFuera(j.asiento);
            double dx = tx - q[0], dy = ty - q[1];
            double a = dx * fuera[0] + dy * fuera[1];           // hacia el jugador
            double b = -dx * fuera[1] + dy * fuera[0];          // a lo largo de la fila
            if (Math.abs(b) <= PASO_MANO / 2 && a > -24 && a < 14) return k;
        }
        return -1;
    }
}
