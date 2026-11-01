package com.miscelanea.juegos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reversi: se coloca una ficha encerrando en línea fichas rivales, que se voltean. Si un jugador no puede colocar, pasa;
 * si nadie puede, gana quien tenga más fichas. Fichas: 1 blanca, 3 negra (como las damas). Jugada: casilla | casilla<<6.
 */
public final class Reversi {
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[] PESO = {
            100, -20, 10, 5, 5, 10, -20, 100,
            -20, -50, -2, -2, -2, -2, -50, -20,
            10, -2, 1, 1, 1, 1, -2, 10,
            5, -2, 1, 0, 0, 1, -2, 5,
            5, -2, 1, 0, 0, 1, -2, 5,
            10, -2, 1, 1, 1, 1, -2, 10,
            -20, -50, -2, -2, -2, -2, -50, -20,
            100, -20, 10, 5, 5, 10, -20, 100};
    public int[] t = new int[64];
    public boolean blancas = true;
    /** Casillas volteadas en la última jugada (máscara de bits). */
    public long volteadas;
    public boolean paso;

    public void inicial() {
        t = new int[64];
        t[27] = 1;
        t[36] = 1;
        t[28] = 3;
        t[35] = 3;
        blancas = true;
        volteadas = 0;
    }

    private int mia() {
        return blancas ? 1 : 3;
    }

    private long voltea(int sq, int yo) {
        if (t[sq] != 0) return 0;
        long total = 0;
        int f0 = sq >> 3, c0 = sq & 7, rival = yo == 1 ? 3 : 1;
        for (int[] d : DIRS) {
            long linea = 0;
            int f = f0 + d[0], c = c0 + d[1];
            while (f >= 0 && f < 8 && c >= 0 && c < 8 && t[f * 8 + c] == rival) {
                linea |= 1L << (f * 8 + c);
                f += d[0];
                c += d[1];
            }
            if (linea != 0 && f >= 0 && f < 8 && c >= 0 && c < 8 && t[f * 8 + c] == yo) total |= linea;
        }
        return total;
    }

    /** Fichas que voltearía colocar en sq el jugador de turno (máscara de bits). */
    public long simular(int sq) {
        return voltea(sq, mia());
    }

    public List<Integer> legales() {
        List<Integer> o = new ArrayList<>();
        int yo = mia();
        for (int s = 0; s < 64; s++) if (voltea(s, yo) != 0) o.add(s | (s << 6));
        return o;
    }

    private boolean puede(boolean b) {
        boolean antes = blancas;
        blancas = b;
        boolean r = !legales().isEmpty();
        blancas = antes;
        return r;
    }

    /** Coloca y voltea; cambia el turno (o lo mantiene si el rival tiene que pasar). Devuelve cuántas volteó. */
    public int hacer(int m) {
        int sq = m & 63, yo = mia();
        long v = voltea(sq, yo);
        if (v == 0) return 0;
        t[sq] = yo;
        for (int s = 0; s < 64; s++) if ((v >> s & 1) != 0) t[s] = yo;
        volteadas = v;
        blancas = !blancas;
        paso = false;
        if (!puede(blancas) && puede(!blancas)) {
            blancas = !blancas;
            paso = true;
        }
        return Long.bitCount(v);
    }

    public int cuenta(int pieza) {
        int n = 0;
        for (int p : t) if (p == pieza) n++;
        return n;
    }

    public boolean terminado() {
        return !puede(true) && !puede(false);
    }

    private int evaluar(int yo) {
        int s = 0, rival = yo == 1 ? 3 : 1;
        for (int i = 0; i < 64; i++) if (t[i] == yo) s += PESO[i];
        else if (t[i] == rival) s -= PESO[i];
        // movilidad: tener opciones y quitárselas al rival
        int mias = 0, suyas = 0;
        for (int q = 0; q < 64; q++) {
            if (t[q] != 0) continue;
            if (voltea(q, yo) != 0) mias++;
            if (voltea(q, rival) != 0) suyas++;
        }
        return s + 6 * (mias - suyas);
    }

    private int negamax(int prof, int alfa, int beta) {
        int yo = mia();
        if (terminado()) {
            int d = cuenta(yo) - cuenta(yo == 1 ? 3 : 1);
            return d * 1000;
        }
        if (prof == 0) return evaluar(yo);
        List<Integer> ms = legales();
        if (ms.isEmpty()) {
            blancas = !blancas;
            int v = -negamax(prof - 1, -beta, -alfa);
            blancas = !blancas;
            return v;
        }
        int mejor = -1_000_000;
        for (int m : ms) {
            int[] copia = t.clone();
            boolean b = blancas;
            int sq = m & 63;
            long v = voltea(sq, yo);
            t[sq] = yo;
            for (int s = 0; s < 64; s++) if ((v >> s & 1) != 0) t[s] = yo;
            blancas = !blancas;
            int r = -negamax(prof - 1, -beta, -alfa);
            t = copia;
            blancas = b;
            if (r > mejor) mejor = r;
            if (r > alfa) alfa = r;
            if (alfa >= beta) break;
        }
        return mejor;
    }

    public int mejor(int prof, Random rnd) {
        List<Integer> ms = legales();
        if (ms.isEmpty()) return -1;
        int mejor = ms.get(0), mv = Integer.MIN_VALUE;
        int yo = mia();
        for (int m : ms) {
            int[] copia = t.clone();
            boolean b = blancas;
            int sq = m & 63;
            long v = voltea(sq, yo);
            t[sq] = yo;
            for (int s = 0; s < 64; s++) if ((v >> s & 1) != 0) t[s] = yo;
            blancas = !blancas;
            int r = -negamax(prof - 1, -1_000_000, 1_000_000) + rnd.nextInt(3);
            t = copia;
            blancas = b;
            if (r > mv) {
                mv = r;
                mejor = m;
            }
        }
        return mejor;
    }

    public int[] guardar() {
        int[] o = new int[68];
        System.arraycopy(t, 0, o, 0, 64);
        o[64] = blancas ? 1 : 0;
        o[65] = (int) volteadas;
        o[66] = (int) (volteadas >>> 32);
        o[67] = paso ? 1 : 0;
        return o;
    }

    public void cargar(int[] o) {
        if (o.length < 68) {
            inicial();
            return;
        }
        System.arraycopy(o, 0, t, 0, 64);
        blancas = o[64] == 1;
        volteadas = (o[65] & 0xFFFFFFFFL) | ((long) o[66] << 32);
        paso = o[67] == 1;
    }
}
