package com.miscelanea.domino;

import java.util.ArrayList;
import java.util.List;

/** Botones físicos del dominó. */
public final class DominoBotones {
    public static final int UNIRSE = 1, CPU = 2, EMPEZAR = 3, NUEVA = 4, TERMINAR = 5;
    public static final double MARGEN = 4;

    public record Boton(int id, double x, double y, double w, double h, String clave, int color) {
        public boolean dentro(double tx, double ty) {
            return tx >= x - w / 2 - MARGEN && tx <= x + w / 2 + MARGEN && ty >= y - h / 2 - MARGEN && ty <= y + h / 2 + MARGEN;
        }
    }

    private DominoBotones() {}

    public static List<Boton> de(DominoPartida p) {
        List<Boton> o = new ArrayList<>();
        switch (p.fase) {
            case DominoPartida.LOBBY -> {
                o.add(new Boton(UNIRSE, 256, 214, 130, 26, "unirse", 0x30A050));
                o.add(new Boton(CPU, 256, 250, 130, 26, "cpu", 0x3070C0));
                o.add(new Boton(EMPEZAR, 256, 292, 130, 30, "empezar", p.js.size() >= 2 ? 0xE0A020 : 0x707070));
            }
            case DominoPartida.JUGAR -> o.add(new Boton(TERMINAR, 46, 494, 64, 16, "terminar", 0x606060));
            default -> o.add(new Boton(NUEVA, 256, 380, 130, 28, "nueva", 0x30A050));
        }
        return o;
    }

    public static Boton en(DominoPartida p, double tx, double ty) {
        for (Boton b : de(p)) if (b.dentro(tx, ty)) return b;
        return null;
    }
}
