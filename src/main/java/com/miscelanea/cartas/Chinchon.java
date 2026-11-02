package com.miscelanea.cartas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cuentas del chinchón: valor de cada carta (figuras 10), mejor reparto en combinaciones (tríos/cuartetos del mismo
 * número y escaleras de 3+ del mismo palo) para dejar el mínimo de puntos sueltos, y si una mano es chinchón.
 */
public final class Chinchon {
    private Chinchon() {}

    public static int valor(int c) {
        int n = Baraja.NUMERO[Baraja.indice(c)];
        return n >= 10 ? 10 : n;
    }

    /** Mínimo de puntos sueltos con las mejores combinaciones. */
    public static int sueltos(List<Integer> mano) {
        int n = mano.size();
        int[] memo = new int[1 << n];
        Arrays.fill(memo, -1);
        return resolver(mano, (1 << n) - 1, memo);
    }

    private static int resolver(List<Integer> m, int quedan, int[] memo) {
        if (quedan == 0) return 0;
        if (memo[quedan] >= 0) return memo[quedan];
        int i = Integer.numberOfTrailingZeros(quedan);
        int c = m.get(i);
        int mejor = valor(c) + resolver(m, quedan & ~(1 << i), memo);
        for (int combo : combinaciones(m, quedan, i)) mejor = Math.min(mejor, resolver(m, quedan & ~combo, memo));
        memo[quedan] = mejor;
        return mejor;
    }

    /** Máscaras de combinaciones que incluyen la carta i (dentro de las que quedan). */
    static List<Integer> combinaciones(List<Integer> m, int quedan, int i) {
        List<Integer> r = new ArrayList<>();
        int c = m.get(i);
        // mismo número
        List<Integer> iguales = new ArrayList<>();
        for (int j = 0; j < m.size(); j++) if (j != i && (quedan & (1 << j)) != 0 && Baraja.indice(m.get(j)) == Baraja.indice(c)) iguales.add(j);
        int k = iguales.size();
        for (int sub = 1; sub < (1 << k); sub++) {
            if (Integer.bitCount(sub) < 2) continue;
            int mask = 1 << i;
            for (int b = 0; b < k; b++) if ((sub & (1 << b)) != 0) mask |= 1 << iguales.get(b);
            r.add(mask);
        }
        // escaleras del mismo palo que contengan c
        int palo = Baraja.palo(c), idx = Baraja.indice(c);
        int[] pos = new int[10];
        Arrays.fill(pos, -1);
        for (int j = 0; j < m.size(); j++)
            if ((quedan & (1 << j)) != 0 && Baraja.palo(m.get(j)) == palo) pos[Baraja.indice(m.get(j))] = j;
        for (int ini = Math.max(0, idx - 9); ini <= idx; ini++)
            for (int fin = idx; fin < 10; fin++) {
                if (fin - ini < 2) continue;
                int mask = 0;
                boolean ok = true;
                for (int x = ini; x <= fin && ok; x++) {
                    if (pos[x] < 0) ok = false;
                    else mask |= 1 << pos[x];
                }
                if (ok) r.add(mask);
            }
        return r;
    }

    public static boolean esChinchon(List<Integer> mano) {
        if (mano.size() != 7) return false;
        int palo = Baraja.palo(mano.get(0));
        int min = 10, max = -1;
        boolean[] hay = new boolean[10];
        for (int c : mano) {
            if (Baraja.palo(c) != palo) return false;
            hay[Baraja.indice(c)] = true;
            min = Math.min(min, Baraja.indice(c));
            max = Math.max(max, Baraja.indice(c));
        }
        if (max - min != 6) return false;
        for (int x = min; x <= max; x++) if (!hay[x]) return false;
        return true;
    }

    /** Qué carta descartar para quedarse con menos sueltos (a igualdad, la de más valor). {índice, sueltos tras descartar} */
    public static int[] mejorDescarte(List<Integer> mano) {
        int mejor = 0, minimo = Integer.MAX_VALUE;
        for (int i = 0; i < mano.size(); i++) {
            List<Integer> resto = new ArrayList<>(mano);
            resto.remove(i);
            int s = sueltos(resto);
            if (s < minimo || (s == minimo && valor(mano.get(i)) > valor(mano.get(mejor)))) {
                minimo = s;
                mejor = i;
            }
        }
        return new int[]{mejor, minimo};
    }
}
