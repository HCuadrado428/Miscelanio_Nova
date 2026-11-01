package com.miscelanea.parchis;

import java.util.ArrayList;
import java.util.List;

/** Botones físicos del parchís (en el centro del tablero). Los calculan igual el servidor y el cliente. */
public final class ParchisBotones {
    public static final int UNIRSE = 1, CPU = 2, EMPEZAR = 3, TIRAR = 4, NUEVA = 5, TERMINAR = 6;

    public record Boton(int id, double x, double y, double w, double h, String clave, int color) {
        public boolean dentro(double tx, double ty) {
            return tx >= x - w / 2 && tx <= x + w / 2 && ty >= y - h / 2 && ty <= y + h / 2;
        }
    }

    private ParchisBotones() {}

    public static List<Boton> de(ParchisPartida p) {
        List<Boton> o = new ArrayList<>();
        switch (p.fase) {
            case ParchisPartida.LOBBY -> {
                o.add(new Boton(UNIRSE, 256, 224, 104, 22, "unirse", 0x30A050));
                o.add(new Boton(CPU, 256, 252, 104, 22, "cpu", 0x3070C0));
                o.add(new Boton(EMPEZAR, 256, 284, 104, 26, "empezar", p.js.size() >= 2 ? 0xE0A020 : 0x707070));
            }
            case ParchisPartida.TIRAR -> {
                if (p.actual() != null && !p.actual().cpu()) o.add(new Boton(TIRAR, 256, 256, 62, 28, "tirar", 0xE0A020));
            }
            case ParchisPartida.FIN -> o.add(new Boton(NUEVA, 256, 256, 104, 26, "nueva", 0x30A050));
            default -> {}
        }
        if (p.fase != ParchisPartida.LOBBY && p.fase != ParchisPartida.FIN) o.add(new Boton(TERMINAR, 478, 502, 56, 14, "terminar", 0x606060));
        return o;
    }

    public static Boton en(ParchisPartida p, double tx, double ty) {
        for (Boton b : de(p)) if (b.dentro(tx, ty)) return b;
        return null;
    }
}
