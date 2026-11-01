package com.miscelanea.cuatro;

import java.util.Random;

/** Cuatro en raya: 7 columnas x 6 filas (fila 0 abajo). 1 = rojo (empieza), 2 = amarillo. */
public class Cuatro {
    public static final int COLS = 7, FILAS = 6;
    private static final int[] ORDEN = {3, 2, 4, 1, 5, 0, 6};
    public final int[] t = new int[COLS * FILAS];
    public int turno = 1, jugadas;

    public static int idx(int c, int f) {
        return f * COLS + c;
    }

    public int fila(int c) {
        for (int f = 0; f < FILAS; f++) if (t[idx(c, f)] == 0) return f;
        return -1;
    }

    public boolean puede(int c) {
        return c >= 0 && c < COLS && t[idx(c, FILAS - 1)] == 0;
    }

    /** Suelta una ficha del jugador de turno; devuelve la fila o -1. */
    public int soltar(int c) {
        if (!puede(c)) return -1;
        int f = fila(c);
        t[idx(c, f)] = turno;
        turno = 3 - turno;
        jugadas++;
        return f;
    }

    private void quitar(int c) {
        for (int f = FILAS - 1; f >= 0; f--) if (t[idx(c, f)] != 0) {
            t[idx(c, f)] = 0;
            turno = 3 - turno;
            jugadas--;
            return;
        }
    }

    public boolean lleno() {
        return jugadas >= COLS * FILAS;
    }

    /** Las cuatro casillas ganadoras (índices) o null. */
    public int[] linea() {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int f = 0; f < FILAS; f++)
            for (int c = 0; c < COLS; c++) {
                int v = t[idx(c, f)];
                if (v == 0) continue;
                for (int[] d : dirs) {
                    int[] l = new int[4];
                    boolean ok = true;
                    for (int k = 0; k < 4 && ok; k++) {
                        int cc = c + d[0] * k, ff = f + d[1] * k;
                        if (cc < 0 || cc >= COLS || ff < 0 || ff >= FILAS || t[idx(cc, ff)] != v) ok = false;
                        else l[k] = idx(cc, ff);
                    }
                    if (ok) return l;
                }
            }
        return null;
    }

    private boolean gana(int c, int f, int v) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int n = 1;
            for (int s = -1; s <= 1; s += 2) {
                int cc = c + d[0] * s, ff = f + d[1] * s;
                while (cc >= 0 && cc < COLS && ff >= 0 && ff < FILAS && t[idx(cc, ff)] == v) {
                    n++;
                    cc += d[0] * s;
                    ff += d[1] * s;
                }
            }
            if (n >= 4) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ CPU
    private int evaluar(int yo) {
        int s = 0;
        for (int f = 0; f < FILAS; f++) if (t[idx(3, f)] == yo) s += 3;
        else if (t[idx(3, f)] == 3 - yo) s -= 3;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int f = 0; f < FILAS; f++)
            for (int c = 0; c < COLS; c++)
                for (int[] d : dirs) {
                    int ce = c + d[0] * 3, fe = f + d[1] * 3;
                    if (ce < 0 || ce >= COLS || fe < 0 || fe >= FILAS) continue;
                    int mios = 0, suyos = 0;
                    for (int k = 0; k < 4; k++) {
                        int v = t[idx(c + d[0] * k, f + d[1] * k)];
                        if (v == yo) mios++;
                        else if (v != 0) suyos++;
                    }
                    if (suyos == 0) s += mios == 3 ? 12 : mios == 2 ? 3 : 0;
                    else if (mios == 0) s -= suyos == 3 ? 14 : suyos == 2 ? 3 : 0;
                }
        return s;
    }

    private int negamax(int prof, int alfa, int beta) {
        if (lleno()) return 0;
        int yo = turno;
        // ganar ya
        for (int c : ORDEN) if (puede(c) && gana(c, fila(c), yo)) return 100000 + prof;
        if (prof == 0) return evaluar(yo);
        int mejor = -1_000_000;
        for (int c : ORDEN) {
            if (!puede(c)) continue;
            soltar(c);
            int v = -negamax(prof - 1, -beta, -alfa);
            quitar(c);
            if (v > mejor) mejor = v;
            if (v > alfa) alfa = v;
            if (alfa >= beta) break;
        }
        return mejor;
    }

    /** Mejor columna para el jugador de turno (con un poco de azar entre las que empatan). */
    public int mejor(int prof, Random r) {
        int yo = turno;
        for (int c : ORDEN) if (puede(c) && gana(c, fila(c), yo)) return c;
        for (int c : ORDEN) if (puede(c) && gana(c, fila(c), 3 - yo)) return c;
        int mejor = -1, mv = Integer.MIN_VALUE;
        for (int c : ORDEN) {
            if (!puede(c)) continue;
            soltar(c);
            int v = -negamax(prof - 1, -1_000_000, 1_000_000) + r.nextInt(3);
            quitar(c);
            if (v > mv) {
                mv = v;
                mejor = c;
            }
        }
        return mejor;
    }

    public int[] guardar() {
        int[] a = new int[t.length + 2];
        System.arraycopy(t, 0, a, 0, t.length);
        a[t.length] = turno;
        a[t.length + 1] = jugadas;
        return a;
    }

    public void cargar(int[] a) {
        if (a.length < t.length + 2) return;
        System.arraycopy(a, 0, t, 0, t.length);
        turno = a[t.length];
        jugadas = a[t.length + 1];
    }
}
