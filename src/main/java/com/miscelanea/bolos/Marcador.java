package com.miscelanea.bolos;

import java.util.List;

/** Cuentas de los bolos: total, si toca rack nuevo, si se acabó la partida y el texto de cada frame. */
public final class Marcador {
    private Marcador() {}

    public static int total(List<Integer> t) {
        int s = 0, i = 0;
        for (int f = 0; f < 10 && i < t.size(); f++) {
            int a = t.get(i);
            if (a == 10) {
                s += 10 + get(t, i + 1) + get(t, i + 2);
                i++;
            } else {
                int b = get(t, i + 1);
                s += a + b + (a + b == 10 ? get(t, i + 2) : 0);
                i += 2;
            }
        }
        return s;
    }

    private static int get(List<Integer> t, int i) {
        return i < t.size() ? t.get(i) : 0;
    }

    /** Índice de la primera tirada del décimo frame (o -1 si aún no se ha llegado). */
    static int inicioDecimo(List<Integer> t) {
        int i = 0;
        for (int f = 0; f < 9; f++) {
            if (i >= t.size()) return -1;
            i += t.get(i) == 10 ? 1 : 2;
        }
        return i <= t.size() ? i : -1;
    }

    /** ¿La próxima tirada empieza con los 10 bolos? */
    public static boolean rackNuevo(List<Integer> t) {
        int d = inicioDecimo(t);
        if (d < 0 || d > t.size()) {
            int i = 0;
            while (i < t.size()) {
                if (t.get(i) == 10) i++;
                else if (i + 1 < t.size()) i += 2;
                else return false;
            }
            return true;
        }
        int n = t.size() - d;
        if (n == 0) return true;
        int a = t.get(d);
        if (n == 1) return a == 10;
        int b = t.get(d + 1);
        return a == 10 ? b == 10 : a + b == 10;
    }

    public static boolean terminada(List<Integer> t) {
        int d = inicioDecimo(t);
        if (d < 0) return false;
        int n = t.size() - d;
        if (n >= 3) return true;
        return n == 2 && t.get(d) + t.get(d + 1) < 10;
    }

    /** Frame en curso (1..10). */
    public static int frame(List<Integer> t) {
        int i = 0, f = 1;
        while (f < 10 && i < t.size()) {
            if (t.get(i) == 10) i++;
            else if (i + 1 < t.size()) i += 2;
            else break;
            if (i <= t.size()) f++;
        }
        return Math.min(f, 10);
    }

    /** Texto de cada frame jugado: "X", "7/", "9-", "8 1"... */
    public static String[] frames(List<Integer> t) {
        String[] r = new String[10];
        int i = 0;
        for (int f = 0; f < 10; f++) {
            if (i >= t.size()) {
                r[f] = "";
                continue;
            }
            StringBuilder sb = new StringBuilder();
            if (f < 9) {
                int a = t.get(i);
                if (a == 10) {
                    sb.append("X");
                    i++;
                } else {
                    sb.append(a == 0 ? "-" : String.valueOf(a));
                    if (i + 1 < t.size()) {
                        int b = t.get(i + 1);
                        sb.append(a + b == 10 ? "/" : b == 0 ? "-" : String.valueOf(b));
                    }
                    i += 2;
                }
            } else {
                int quedan = 10;
                boolean primera = true;
                for (int k = i; k < t.size(); k++) {
                    int v = t.get(k);
                    if (primera && v == 10) sb.append("X");
                    else if (!primera && v == quedan) sb.append("/");
                    else {
                        sb.append(v == 0 ? "-" : String.valueOf(v));
                        quedan -= v;
                        primera = false;
                        continue;
                    }
                    quedan = 10;
                    primera = true;
                }
                i = t.size();
            }
            r[f] = sb.toString();
        }
        return r;
    }
}
