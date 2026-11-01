package com.miscelanea.juegos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Damas (reglas clásicas inglesas): las piezas avanzan en diagonal, comer es obligatorio y se encadenan saltos;
 * al llegar al fondo se coronan (dama: se mueve y come hacia atrás también). Pierde quien no puede mover.
 * Piezas: 1 blanca, 2 dama blanca, 3 negra, 4 dama negra. Movimiento: desde | hasta<<6 | (comida+1)<<12.
 */
public final class Damas {
    public int[] t = new int[64];
    public boolean blancas = true;
    public int obligada = -1;

    public static boolean esBlanca(int p) {
        return p == 1 || p == 2;
    }

    public static boolean esDama(int p) {
        return p == 2 || p == 4;
    }

    public void inicial() {
        t = new int[64];
        for (int f = 0; f < 8; f++)
            for (int c = 0; c < 8; c++)
                if ((f + c) % 2 == 0) {
                    if (f < 3) t[f * 8 + c] = 1;
                    else if (f > 4) t[f * 8 + c] = 3;
                }
        blancas = true;
        obligada = -1;
    }

    public Damas copia() {
        Damas d = new Damas();
        d.t = t.clone();
        d.blancas = blancas;
        d.obligada = obligada;
        return d;
    }

    public int[] guardar() {
        int[] o = new int[66];
        System.arraycopy(t, 0, o, 0, 64);
        o[64] = blancas ? 1 : 0;
        o[65] = obligada;
        return o;
    }

    public void cargar(int[] o) {
        if (o.length < 66) {
            inicial();
            return;
        }
        System.arraycopy(o, 0, t, 0, 64);
        blancas = o[64] == 1;
        obligada = o[65];
    }

    private boolean mia(int p) {
        return p != 0 && esBlanca(p) == blancas;
    }

    private void desde(List<Integer> capt, List<Integer> sim, int sq) {
        int p = t[sq];
        int f = sq >> 3, c = sq & 7;
        int adelante = esBlanca(p) ? 1 : -1;
        for (int df = -1; df <= 1; df += 2) {
            if (!esDama(p) && df != adelante) continue;
            for (int dc = -1; dc <= 1; dc += 2) {
                int f1 = f + df, c1 = c + dc;
                if (f1 < 0 || f1 > 7 || c1 < 0 || c1 > 7) continue;
                int s1 = f1 * 8 + c1;
                if (t[s1] == 0) {
                    if (sim != null) sim.add(sq | (s1 << 6));
                } else if (esBlanca(t[s1]) != esBlanca(p)) {
                    int f2 = f1 + df, c2 = c1 + dc;
                    if (f2 < 0 || f2 > 7 || c2 < 0 || c2 > 7) continue;
                    int s2 = f2 * 8 + c2;
                    if (t[s2] == 0) capt.add(sq | (s2 << 6) | ((s1 + 1) << 12));
                }
            }
        }
    }

    public List<Integer> legales() {
        List<Integer> capt = new ArrayList<>(), sim = new ArrayList<>();
        if (obligada >= 0) {
            desde(capt, null, obligada);
            return capt;
        }
        for (int sq = 0; sq < 64; sq++) if (mia(t[sq])) desde(capt, sim, sq);
        return capt.isEmpty() ? sim : capt;
    }

    /** Hace el movimiento; devuelve la casilla de la pieza comida o -1. */
    public int hacer(int m) {
        int de = m & 63, a = (m >> 6) & 63, cap = (m >> 12) - 1;
        int p = t[de];
        t[a] = p;
        t[de] = 0;
        boolean corona = false;
        if (p == 1 && (a >> 3) == 7) {
            t[a] = 2;
            corona = true;
        }
        if (p == 3 && (a >> 3) == 0) {
            t[a] = 4;
            corona = true;
        }
        if (cap >= 0) {
            t[cap] = 0;
            if (!corona) {
                List<Integer> mas = new ArrayList<>();
                desde(mas, null, a);
                if (!mas.isEmpty()) {
                    obligada = a;
                    return cap;
                }
            }
        }
        obligada = -1;
        blancas = !blancas;
        return cap;
    }

    // ------------------------------------------------------------------ CPU (minimax con poda; el turno puede repetirse en los saltos encadenados)
    private int evaluar() {
        int s = 0;
        for (int i = 0; i < 64; i++) {
            int p = t[i];
            if (p == 0) continue;
            int f = i >> 3;
            int v = esDama(p) ? 175 : 100 + (esBlanca(p) ? f : 7 - f) * 4;
            if ((i & 7) == 0 || (i & 7) == 7) v += 4;
            s += esBlanca(p) ? v : -v;
        }
        return s;
    }

    private int minimax(int prof, int alfa, int beta) {
        List<Integer> ms = legales();
        if (ms.isEmpty()) return blancas ? -100000 - prof : 100000 + prof;
        if (prof == 0) return evaluar();
        boolean max = blancas;
        int mejor = max ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int m : ms) {
            Damas d = copia();
            d.hacer(m);
            int v = d.minimax(prof - 1, alfa, beta);
            if (max) {
                mejor = Math.max(mejor, v);
                alfa = Math.max(alfa, v);
            } else {
                mejor = Math.min(mejor, v);
                beta = Math.min(beta, v);
            }
            if (beta <= alfa) break;
        }
        return mejor;
    }

    public int mejor(int prof, Random rnd) {
        List<Integer> ms = legales();
        if (ms.isEmpty()) return -1;
        Collections.shuffle(ms, rnd);
        boolean max = blancas;
        int mejor = ms.get(0), mv = max ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int m : ms) {
            Damas d = copia();
            d.hacer(m);
            int v = d.minimax(prof - 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (max ? v > mv : v < mv) {
                mv = v;
                mejor = m;
            }
        }
        return mejor;
    }
}
