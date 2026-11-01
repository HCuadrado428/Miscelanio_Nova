package com.miscelanea.flota;

import java.util.ArrayList;
import java.util.List;

/** Botones físicos de hundir la flota (franja central entre los dos mares). */
public final class FlotaBotones {
    public static final int UNIRSE = 1, CPU = 2, EMPEZAR = 3, RECOLOCAR = 4, LISTO = 5, NUEVA = 6, TERMINAR = 7;
    public static final double MARGEN = 4;

    public record Boton(int id, double x, double y, double w, double h, String clave, int color) {
        public boolean dentro(double tx, double ty) {
            return tx >= x - w / 2 - MARGEN && tx <= x + w / 2 + MARGEN && ty >= y - h / 2 - MARGEN && ty <= y + h / 2 + MARGEN;
        }
    }

    private FlotaBotones() {}

    public static List<Boton> de(FlotaPartida p) {
        List<Boton> o = new ArrayList<>();
        switch (p.fase) {
            case FlotaPartida.LOBBY -> {
                o.add(new Boton(UNIRSE, 150, 256, 96, 26, "unirse", 0x30A050));
                o.add(new Boton(CPU, 256, 256, 96, 26, "cpu", 0x3070C0));
                o.add(new Boton(EMPEZAR, 362, 256, 96, 26, "empezar", p.js.size() >= 2 ? 0xE0A020 : 0x707070));
            }
            case FlotaPartida.COLOCAR -> {
                o.add(new Boton(RECOLOCAR, 196, 256, 110, 26, "recolocar", 0x3070C0));
                o.add(new Boton(LISTO, 316, 256, 110, 26, "listo", 0x30A050));
            }
            case FlotaPartida.JUGAR -> o.add(new Boton(TERMINAR, 470, 256, 60, 16, "terminar", 0x606060));
            default -> o.add(new Boton(NUEVA, 256, 256, 130, 26, "nueva", 0x30A050));
        }
        return o;
    }

    public static Boton en(FlotaPartida p, double tx, double ty) {
        for (Boton b : de(p)) if (b.dentro(tx, ty)) return b;
        return null;
    }
}
