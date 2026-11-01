package com.miscelanea.ocarina;

/** Las canciones de la ocarina (melodías originales con las notas 1..5 = Do Re Mi Sol La). */
public enum Cancion {
    SOL("sol", 60 * 20, 1, 2, 3, 5, 4, 5),
    LUNA("luna", 60 * 20, 5, 4, 3, 1, 2, 1),
    TORMENTA("tormenta", 60 * 20, 2, 1, 2, 1, 4, 4),
    DESPEJAR("despejar", 60 * 20, 4, 3, 4, 5, 5, 5),
    REGRESO("regreso", 180 * 20, 3, 2, 3, 4, 3, 1);

    public final String id;
    public final int espera;
    public final int[] notas;

    Cancion(String id, int espera, int... notas) {
        this.id = id;
        this.espera = espera;
        this.notas = notas;
    }

    public String clave() {
        return "cancion.miscelanea." + id;
    }

    public static Cancion de(int i) {
        Cancion[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }

    /** ¿Las últimas notas tocadas (1..5) terminan con esta canción? */
    public boolean coincide(java.util.List<Integer> tocadas) {
        int n = notas.length, m = tocadas.size();
        if (m < n) return false;
        for (int i = 0; i < n; i++) if (tocadas.get(m - n + i) != notas[i]) return false;
        return true;
    }
}
