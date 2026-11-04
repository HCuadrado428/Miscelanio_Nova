package com.miscelanea.oca;

import java.util.ArrayList;
import java.util.List;

/** Botones físicos de la Oca: en el centro durante la preparación y en el marco durante la partida. */
public final class OcaBotones {
    public static final int UNIRSE = 1, CPU = 2, EMPEZAR = 3, NUEVA = 5, TERMINAR = 6, VELOCIDAD = 7, REVANCHA = 8, SALIR = 9;
    public static final double MARGEN = 4;

    public record Boton(int id, double x, double y, double w, double h, String clave, int color) {
        public boolean dentro(double tx, double ty) {
            return tx >= x - w / 2 - MARGEN && tx <= x + w / 2 + MARGEN && ty >= y - h / 2 - MARGEN && ty <= y + h / 2 + MARGEN;
        }
    }

    private OcaBotones() {}

    public static List<Boton> de(OcaPartida p) {
        List<Boton> o = new ArrayList<>();
        switch (p.fase) {
            case OcaPartida.LOBBY -> {
                o.add(new Boton(UNIRSE, 219, 196, 72, 28, "unirse", 0x30A050));
                o.add(new Boton(SALIR, 293, 196, 72, 28, "salir", 0xA04040));
                o.add(new Boton(CPU, 256, 236, 150, 28, "cpu", 0x3070C0));
                o.add(new Boton(VELOCIDAD, 256, 276, 150, 28, "vel_" + p.velocidad, 0x607080));
                o.add(new Boton(EMPEZAR, 256, 320, 150, 32, "empezar", p.js.size() >= 2 ? 0xE0A020 : 0x707070));
            }
            case OcaPartida.FIN -> {
                o.add(new Boton(REVANCHA, 256, 236, 150, 30, "revancha", 0xE0A020));
                o.add(new Boton(NUEVA, 256, 276, 150, 26, "nueva", 0x30A050));
            }
            default -> {
                o.add(new Boton(TERMINAR, 440, 497, 70, 16, "terminar", 0x606060));
                o.add(new Boton(VELOCIDAD, 72, 497, 70, 16, "vel_" + p.velocidad, 0x607080));
            }
        }
        return o;
    }

    public static Boton en(OcaPartida p, double tx, double ty) {
        for (Boton b : de(p)) if (b.dentro(tx, ty)) return b;
        return null;
    }
}
