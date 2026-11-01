package com.miscelanea.imperio;

/** Las 40 casillas del tablero (diseño propio: calles = biomas de Minecraft, dinero = esmeraldas). */
public final class Casillas {
    public static final int SALIDA = 0, PROP = 1, COFRE = 2, IMPUESTO = 3, ESTACION = 4, DESTINO = 5, CARCEL = 6, UTIL = 7, PARKING = 8, IR_CARCEL = 9;

    /** tipo, grupo (0..7, -1 sin grupo), precio, coste de casa. */
    public static final int[][] D = {
            {SALIDA, -1, 0, 0},
            {PROP, 0, 60, 50}, {COFRE, -1, 0, 0}, {PROP, 0, 60, 50}, {IMPUESTO, -1, 200, 0}, {ESTACION, -1, 200, 0},
            {PROP, 1, 100, 50}, {DESTINO, -1, 0, 0}, {PROP, 1, 100, 50}, {PROP, 1, 120, 50},
            {CARCEL, -1, 0, 0},
            {PROP, 2, 140, 100}, {UTIL, -1, 150, 0}, {PROP, 2, 140, 100}, {PROP, 2, 160, 100}, {ESTACION, -1, 200, 0},
            {PROP, 3, 180, 100}, {COFRE, -1, 0, 0}, {PROP, 3, 180, 100}, {PROP, 3, 200, 100},
            {PARKING, -1, 0, 0},
            {PROP, 4, 220, 150}, {DESTINO, -1, 0, 0}, {PROP, 4, 220, 150}, {PROP, 4, 240, 150}, {ESTACION, -1, 200, 0},
            {PROP, 5, 260, 150}, {PROP, 5, 260, 150}, {UTIL, -1, 150, 0}, {PROP, 5, 280, 150},
            {IR_CARCEL, -1, 0, 0},
            {PROP, 6, 300, 200}, {PROP, 6, 300, 200}, {COFRE, -1, 0, 0}, {PROP, 6, 320, 200}, {ESTACION, -1, 200, 0},
            {DESTINO, -1, 0, 0}, {PROP, 7, 350, 200}, {IMPUESTO, -1, 100, 0}, {PROP, 7, 400, 200}};

    public static final String[] ID = {"salida", "llanuras", "cofre", "pradera", "impuesto_herreria", "estacion_norte",
            "playa", "destino", "rio", "arrecife", "carcel",
            "abedules", "redstone", "bosque_oscuro", "taiga", "estacion_este",
            "sabana", "cofre", "desierto", "aldea_desierto", "parking",
            "pantano", "destino", "jungla", "templo_jungla", "estacion_sur",
            "montanas", "cumbres", "lava", "amatista", "ir_carcel",
            "cerezos", "champinon", "cofre", "ciudad_ancestral", "estacion_oeste",
            "destino", "fortaleza_nether", "impuesto_lujo", "ciudad_end"};

    public static final int[] COLOR = {0x8B5A2B, 0x7FD4F0, 0xE060B0, 0xF29A30, 0xE03838, 0xF0D838, 0x30B050, 0x3858D8};

    private Casillas() {}

    public static int tipo(int i) {
        return D[i][0];
    }

    public static int grupo(int i) {
        return D[i][1];
    }

    public static int precio(int i) {
        return D[i][2];
    }

    public static int casa(int i) {
        return D[i][3];
    }

    public static boolean comprable(int i) {
        int t = tipo(i);
        return t == PROP || t == ESTACION || t == UTIL;
    }

    /** Alquiler de una calle según casas (0..5, 5 = hotel). */
    public static int alquilerCalle(int i, int casas) {
        int base = Math.max(2, Math.round(precio(i) * 0.07F / 2) * 2);
        int[] mult = {1, 5, 15, 40, 55, 70};
        return base * mult[Math.max(0, Math.min(5, casas))];
    }

    public static String clave(int i) {
        return "imperio.miscelanea.casilla." + ID[i];
    }
}
