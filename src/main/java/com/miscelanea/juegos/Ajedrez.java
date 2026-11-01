package com.miscelanea.juegos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Ajedrez con todas las reglas: enroques, captura al paso, promoción (a dama), jaque, mate y ahogado.
 * Casillas 0..63 = fila*8 + columna; la fila 0 es la de las blancas. Piezas: tipo 1..6 (P C A T D R), +8 si es negra.
 * Movimiento: desde | hasta<<6 | bandera<<12 (1 al paso, 2 enroque, 4 promoción).
 */
public final class Ajedrez {
    public static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6, NEGRA = 8;
    private static final int[] VALOR = {0, 100, 320, 330, 500, 900, 0};
    private static final int[][] SALTOS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] RECTO = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public int[] t = new int[64];
    public boolean blancas = true;
    public int enroque = 15;      // 1 blancas corto, 2 blancas largo, 4 negras corto, 8 negras largo
    public int alPaso = -1;

    public static int tipo(int p) {
        return p & 7;
    }

    public static boolean esNegra(int p) {
        return (p & 8) != 0;
    }

    public void inicial() {
        int[] fila = {R, N, B, Q, K, B, N, R};
        t = new int[64];
        for (int c = 0; c < 8; c++) {
            t[c] = fila[c];
            t[8 + c] = P;
            t[48 + c] = P | NEGRA;
            t[56 + c] = fila[c] | NEGRA;
        }
        blancas = true;
        enroque = 15;
        alPaso = -1;
    }

    public Ajedrez copia() {
        Ajedrez a = new Ajedrez();
        a.t = t.clone();
        a.blancas = blancas;
        a.enroque = enroque;
        a.alPaso = alPaso;
        return a;
    }

    public int[] guardar() {
        int[] o = new int[67];
        System.arraycopy(t, 0, o, 0, 64);
        o[64] = blancas ? 1 : 0;
        o[65] = enroque;
        o[66] = alPaso;
        return o;
    }

    public void cargar(int[] o) {
        if (o.length < 67) {
            inicial();
            return;
        }
        System.arraycopy(o, 0, t, 0, 64);
        blancas = o[64] == 1;
        enroque = o[65];
        alPaso = o[66];
    }

    private static boolean dentro(int f, int c) {
        return f >= 0 && f < 8 && c >= 0 && c < 8;
    }

    private boolean propia(int p, boolean b) {
        return p != 0 && esNegra(p) != b;
    }

    /** ¿La casilla está atacada por el bando indicado? */
    public boolean atacada(int sq, boolean porBlancas) {
        int f = sq >> 3, c = sq & 7;
        int dir = porBlancas ? -1 : 1;           // los peones atacantes están detrás
        for (int dc = -1; dc <= 1; dc += 2) {
            int ff = f + dir, cc = c + dc;
            if (dentro(ff, cc)) {
                int p = t[ff * 8 + cc];
                if (p != 0 && tipo(p) == P && esNegra(p) != porBlancas) return true;
            }
        }
        for (int[] s : SALTOS) {
            int ff = f + s[0], cc = c + s[1];
            if (dentro(ff, cc)) {
                int p = t[ff * 8 + cc];
                if (p != 0 && tipo(p) == N && esNegra(p) != porBlancas) return true;
            }
        }
        for (int df = -1; df <= 1; df++)
            for (int dc = -1; dc <= 1; dc++) {
                if (df == 0 && dc == 0) continue;
                int ff = f + df, cc = c + dc;
                if (dentro(ff, cc)) {
                    int p = t[ff * 8 + cc];
                    if (p != 0 && tipo(p) == K && esNegra(p) != porBlancas) return true;
                }
            }
        if (rayo(f, c, DIAG, porBlancas, B)) return true;
        return rayo(f, c, RECTO, porBlancas, R);
    }

    private boolean rayo(int f, int c, int[][] dirs, boolean porBlancas, int tipoLargo) {
        for (int[] d : dirs) {
            int ff = f + d[0], cc = c + d[1];
            while (dentro(ff, cc)) {
                int p = t[ff * 8 + cc];
                if (p != 0) {
                    if (esNegra(p) != porBlancas && (tipo(p) == tipoLargo || tipo(p) == Q)) return true;
                    break;
                }
                ff += d[0];
                cc += d[1];
            }
        }
        return false;
    }

    public int rey(boolean b) {
        for (int i = 0; i < 64; i++) if (t[i] == (K | (b ? 0 : NEGRA))) return i;
        return -1;
    }

    public boolean enJaque(boolean b) {
        int r = rey(b);
        return r >= 0 && atacada(r, !b);
    }

    private void anadir(List<Integer> l, int de, int a, int bandera) {
        l.add(de | (a << 6) | (bandera << 12));
    }

    private List<Integer> pseudo() {
        List<Integer> l = new ArrayList<>(48);
        boolean b = blancas;
        for (int sq = 0; sq < 64; sq++) {
            int p = t[sq];
            if (!propia(p, b)) continue;
            int f = sq >> 3, c = sq & 7;
            switch (tipo(p)) {
                case P -> {
                    int dir = b ? 1 : -1, ini = b ? 1 : 6, ult = b ? 7 : 0;
                    int ff = f + dir;
                    if (dentro(ff, c) && t[ff * 8 + c] == 0) {
                        anadir(l, sq, ff * 8 + c, ff == ult ? 4 : 0);
                        int f2 = f + 2 * dir;
                        if (f == ini && t[f2 * 8 + c] == 0) anadir(l, sq, f2 * 8 + c, 0);
                    }
                    for (int dc = -1; dc <= 1; dc += 2) {
                        int cc = c + dc;
                        if (!dentro(ff, cc)) continue;
                        int d = ff * 8 + cc;
                        if (t[d] != 0 && esNegra(t[d]) == b) anadir(l, sq, d, ff == ult ? 4 : 0);
                        else if (d == alPaso) anadir(l, sq, d, 1);
                    }
                }
                case N -> {
                    for (int[] s : SALTOS) {
                        int ff = f + s[0], cc = c + s[1];
                        if (dentro(ff, cc) && !propia(t[ff * 8 + cc], b)) anadir(l, sq, ff * 8 + cc, 0);
                    }
                }
                case B -> deslizar(l, sq, DIAG, b);
                case R -> deslizar(l, sq, RECTO, b);
                case Q -> {
                    deslizar(l, sq, DIAG, b);
                    deslizar(l, sq, RECTO, b);
                }
                case K -> {
                    for (int df = -1; df <= 1; df++)
                        for (int dc = -1; dc <= 1; dc++) {
                            if (df == 0 && dc == 0) continue;
                            int ff = f + df, cc = c + dc;
                            if (dentro(ff, cc) && !propia(t[ff * 8 + cc], b)) anadir(l, sq, ff * 8 + cc, 0);
                        }
                    int base = b ? 0 : 56;
                    if (sq == base + 4 && !atacada(sq, !b)) {
                        if ((enroque & (b ? 1 : 4)) != 0 && t[base + 5] == 0 && t[base + 6] == 0 && t[base + 7] == (R | (b ? 0 : NEGRA))
                                && !atacada(base + 5, !b) && !atacada(base + 6, !b)) anadir(l, sq, base + 6, 2);
                        if ((enroque & (b ? 2 : 8)) != 0 && t[base + 1] == 0 && t[base + 2] == 0 && t[base + 3] == 0 && t[base] == (R | (b ? 0 : NEGRA))
                                && !atacada(base + 3, !b) && !atacada(base + 2, !b)) anadir(l, sq, base + 2, 2);
                    }
                }
            }
        }
        return l;
    }

    private void deslizar(List<Integer> l, int sq, int[][] dirs, boolean b) {
        int f = sq >> 3, c = sq & 7;
        for (int[] d : dirs) {
            int ff = f + d[0], cc = c + d[1];
            while (dentro(ff, cc)) {
                int p = t[ff * 8 + cc];
                if (propia(p, b)) break;
                anadir(l, sq, ff * 8 + cc, 0);
                if (p != 0) break;
                ff += d[0];
                cc += d[1];
            }
        }
    }

    public List<Integer> legales() {
        List<Integer> out = new ArrayList<>();
        for (int m : pseudo()) {
            Ajedrez a = copia();
            boolean b = blancas;
            a.hacer(m);
            if (!a.enJaque(b)) out.add(m);
        }
        return out;
    }

    /** Devuelve la pieza capturada (o 0). */
    public int hacer(int m) {
        int de = m & 63, a = (m >> 6) & 63, band = m >> 12;
        int p = t[de];
        int capt = t[a];
        t[a] = p;
        t[de] = 0;
        if ((band & 1) != 0) {
            int cap = a + (blancas ? -8 : 8);
            capt = t[cap];
            t[cap] = 0;
        }
        if ((band & 4) != 0) t[a] = Q | (p & NEGRA);
        if ((band & 2) != 0) {
            if ((a & 7) == 6) {
                t[a - 1] = t[a + 1];
                t[a + 1] = 0;
            } else {
                t[a + 1] = t[a - 2];
                t[a - 2] = 0;
            }
        }
        alPaso = tipo(p) == P && Math.abs(a - de) == 16 ? (a + de) / 2 : -1;
        if (tipo(p) == K) enroque &= blancas ? ~3 : ~12;
        for (int sq : new int[]{de, a}) {
            if (sq == 0) enroque &= ~2;
            if (sq == 7) enroque &= ~1;
            if (sq == 56) enroque &= ~8;
            if (sq == 63) enroque &= ~4;
        }
        blancas = !blancas;
        return capt;
    }

    public static int desde(int m) {
        return m & 63;
    }

    public static int hasta(int m) {
        return (m >> 6) & 63;
    }

    // ------------------------------------------------------------------ CPU
    private int evaluar() {
        int s = 0;
        for (int i = 0; i < 64; i++) {
            int p = t[i];
            if (p == 0) continue;
            int ty = tipo(p), f = i >> 3, c = i & 7;
            int v = VALOR[ty];
            int fr = esNegra(p) ? 7 - f : f;
            if (ty == P) v += fr * 8 + (c >= 2 && c <= 5 && fr >= 3 ? 10 : 0);
            if (ty == N || ty == B) v += 12 - (Math.abs(3.5 - c) + Math.abs(3.5 - f) > 4 ? 15 : 0);
            if (ty == K && fr == 0 && (c <= 2 || c >= 6)) v += 25;
            s += esNegra(p) ? -v : v;
        }
        return blancas ? s : -s;
    }

    private int negamax(int prof, int alfa, int beta, int ply) {
        List<Integer> ms = legales();
        if (ms.isEmpty()) return enJaque(blancas) ? -100000 + ply : 0;
        if (prof == 0) return evaluar();
        ms.sort((x, y) -> Integer.compare(t[hasta(y)] != 0 ? VALOR[tipo(t[hasta(y)])] : 0, t[hasta(x)] != 0 ? VALOR[tipo(t[hasta(x)])] : 0));
        for (int m : ms) {
            Ajedrez a = copia();
            a.hacer(m);
            int v = -a.negamax(prof - 1, -beta, -alfa, ply + 1);
            if (v > alfa) alfa = v;
            if (alfa >= beta) break;
        }
        return alfa;
    }

    public int mejor(int prof, Random rnd) {
        List<Integer> ms = legales();
        if (ms.isEmpty()) return -1;
        Collections.shuffle(ms, rnd);
        int mejor = ms.get(0), alfa = -1_000_000;
        for (int m : ms) {
            Ajedrez a = copia();
            a.hacer(m);
            int v = -a.negamax(prof - 1, -1_000_000, -alfa, 1);
            if (v > alfa) {
                alfa = v;
                mejor = m;
            }
        }
        return mejor;
    }
}
