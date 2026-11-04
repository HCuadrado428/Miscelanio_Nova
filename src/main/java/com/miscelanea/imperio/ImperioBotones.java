package com.miscelanea.imperio;

import java.util.ArrayList;
import java.util.List;

/** Botones físicos del centro del tablero según el estado de la partida (los calcula igual el servidor y el cliente). */
public final class ImperioBotones {
    public static final int UNIRSE = 1, CPU = 2, RONDAS = 3, EMPEZAR = 4, TIRAR = 5, PAGAR_CARCEL = 6, CARTA_CARCEL = 7, COMPRAR = 8, NO_COMPRAR = 9,
            FIN_TURNO = 10, BANCARROTA = 11, NUEVA = 12, CONSTRUIR = 13, VENDER = 14, HIPOTECAR = 15, DESHIPOTECAR = 16, TERMINAR = 17, VELOCIDAD = 18, REVANCHA = 19;
    /** Margen extra alrededor de cada botón para que sea fácil acertar. */
    public static final double MARGEN = 4;

    public record Boton(int id, double x, double y, double w, double h, String clave, Object[] args, int color) {
        public boolean dentro(double tx, double ty) {
            return tx >= x - w / 2 - MARGEN && tx <= x + w / 2 + MARGEN && ty >= y - h / 2 - MARGEN && ty <= y + h / 2 + MARGEN;
        }
    }

    private ImperioBotones() {}

    private static void fila(List<Boton> out, double y, double w, double h, Object[]... bs) {
        int n = bs.length;
        double gap = 10, total = n * w + (n - 1) * gap, x0 = 256 - total / 2 + w / 2;
        for (int i = 0; i < n; i++) {
            Object[] b = bs[i];
            Object[] args = b.length > 3 ? java.util.Arrays.copyOfRange(b, 3, b.length) : new Object[0];
            out.add(new Boton((int) b[0], x0 + i * (w + gap), y, w, h, (String) b[1], args, (int) b[2]));
        }
    }

    public static List<Boton> de(Partida p) {
        List<Boton> o = new ArrayList<>();
        Partida.Jugador j = p.actual();
        switch (p.fase) {
            case Partida.LOBBY -> {
                fila(o, 330, 100, 30, new Object[]{UNIRSE, "unirse", 0x30A050}, new Object[]{CPU, "cpu", 0x3070C0},
                        new Object[]{RONDAS, Partida.LIMITES[p.limite] > 0 ? "rondas" : "rondas_inf", 0x806040, Partida.LIMITES[p.limite]});
                fila(o, 375, 130, 32, new Object[]{VELOCIDAD, "vel_" + p.velocidad, 0x607080},
                        new Object[]{EMPEZAR, "empezar", p.js.size() >= 2 ? 0xE0A020 : 0x707070});
            }
            case Partida.TIRAR -> {
                if (j.carcel > 0) {
                    List<Object[]> bs = new ArrayList<>();
                    bs.add(new Object[]{TIRAR, "tirar_dobles", 0xE0A020});
                    if (j.dinero >= Partida.MULTA) bs.add(new Object[]{PAGAR_CARCEL, "pagar_carcel", 0x3070C0, Partida.MULTA});
                    if (j.libre > 0) bs.add(new Object[]{CARTA_CARCEL, "carta_carcel", 0x30A050});
                    fila(o, 330, 110, 30, bs.toArray(new Object[0][]));
                } else fila(o, 330, 150, 34, new Object[]{TIRAR, "tirar", 0xE0A020});
            }
            case Partida.COMPRAR -> fila(o, 330, 120, 32, new Object[]{COMPRAR, "comprar", 0x30A050, Casillas.precio(j.pos)},
                    new Object[]{NO_COMPRAR, "no_comprar", 0xA04040});
            case Partida.GESTION -> fila(o, 330, 150, 32, new Object[]{FIN_TURNO, p.tiroOtraVez && j.carcel == 0 ? "otra_vez" : "fin_turno", 0xE0A020});
            case Partida.DEUDA -> fila(o, 330, 150, 32, new Object[]{BANCARROTA, "bancarrota", 0xA03030});
            case Partida.FIN -> fila(o, 330, 120, 32, new Object[]{REVANCHA, "revancha", 0xE0A020}, new Object[]{NUEVA, "nueva", 0x30A050});
            default -> {}
        }
        // acciones sobre la propiedad seleccionada
        if (p.sel >= 0 && j != null && (p.fase == Partida.TIRAR || p.fase == Partida.GESTION || p.fase == Partida.DEUDA)) {
            int q = p.turno, c = p.sel;
            List<Object[]> bs = new ArrayList<>();
            if (p.fase != Partida.DEUDA && p.puedeConstruir(q, c)) bs.add(new Object[]{CONSTRUIR, p.casas[c] == 4 ? "hotel" : "casa", 0x30A050, Casillas.casa(c)});
            if (p.puedeVender(q, c)) bs.add(new Object[]{VENDER, "vender", 0xC08030, Casillas.casa(c) / 2});
            if (p.puedeHipotecar(q, c)) bs.add(new Object[]{HIPOTECAR, "hipotecar", 0x806090, Casillas.precio(c) / 2});
            if (p.fase != Partida.DEUDA && p.puedeDeshipotecar(q, c)) bs.add(new Object[]{DESHIPOTECAR, "deshipotecar", 0x3070C0, Partida.costeDeshipoteca(c)});
            if (!bs.isEmpty()) fila(o, 382, 100, 28, bs.toArray(new Object[0][]));
        }
        if (p.fase != Partida.LOBBY && p.fase != Partida.FIN) {
            o.add(new Boton(TERMINAR, 420, 428, 64, 18, "terminar", new Object[0], 0x606060));
            o.add(new Boton(VELOCIDAD, 92, 428, 64, 18, "vel_" + p.velocidad, new Object[0], 0x607080));
        }
        return o;
    }

    public static Boton en(Partida p, double tx, double ty) {
        for (Boton b : de(p)) if (b.dentro(tx, ty)) return b;
        return null;
    }
}
