package com.miscelanea.hacha;

/**
 * Movimientos del Hacha Leviatán (God of War). Los usa el cliente (animación/movimiento) y el servidor (golpes).
 * recuperacion = ticks hasta poder encadenar la siguiente acción.
 */
public enum AccionHacha {
    LIGERO1(7, false, g(4, 3.4, 120, 6, Efecto.NORMAL)),
    LIGERO2(7, false, g(4, 3.4, 120, 6, Efecto.NORMAL)),
    LIGERO3(8, false, g(5, 3.4, 90, 7, Efecto.NORMAL)),
    LIGERO4(13, false, g(8, 3.6, 100, 9, Efecto.NORMAL), g(8, 3.3, 360, 4, Efecto.ESCARCHA)),
    PESADO1(14, false, g(9, 3.8, 170, 10, Efecto.NORMAL)),
    PESADO2(14, false, g(9, 3.6, 80, 12, Efecto.ONDA)),
    CLEAVE(14, false, g(3, 3.8, 90, 18, Efecto.ONDA)),
    SALTO(14, false, g(9, 3.6, 130, 10, Efecto.ONDA)),
    LANZAR_LIGERO(8, false),
    LANZAR_PESADO(8, false),
    LLAMAR(4, true),
    ESQUIVA(6, false),
    VOLTERETA(11, false),
    GOLPE_ESQUIVA(8, false, g(4, 3.4, 200, 7, Efecto.NORMAL)),
    PUNO1(5, true, g(2, 2.7, 90, 4, Efecto.PUNO)),
    PUNO2(5, true, g(2, 2.7, 90, 4, Efecto.PUNO)),
    PUNO3(10, true, g(5, 2.9, 90, 6, Efecto.LANZAR)),
    RUN_HEL(12, false, g(6, 5.0, 360, 8, Efecto.ONDA)),
    RUN_FRENZY(28, false, g(7, 3.6, 120, 7, Efecto.LANZAR), g(15, 3.6, 120, 7, Efecto.LANZAR), g(24, 4.0, 150, 12, Efecto.CONGELAR)),
    RUN_TEMPEST(20, false, g(6, 3.8, 360, 6, Efecto.NORMAL), g(10, 3.8, 360, 6, Efecto.NORMAL), g(14, 3.8, 360, 6, Efecto.NORMAL),
            g(17, 4.0, 360, 7, Efecto.ESCARCHA)),
    RUN_RIOS(16, false),
    ESCARCHA(20, false),
    ATRAPAR(0, false),
    PARRY(0, false);

    public enum Efecto { NORMAL, ESCARCHA, ONDA, LANZAR, CONGELAR, PUNO }

    public record Golpe(int tick, double alcance, double angulo, float dano, Efecto efecto) {}

    public final int recuperacion;
    /** Se puede hacer con el hacha lanzada (sin hacha en la mano). */
    public final boolean sinHacha;
    public final Golpe[] golpes;

    AccionHacha(int recuperacion, boolean sinHacha, Golpe... golpes) {
        this.recuperacion = recuperacion;
        this.sinHacha = sinHacha;
        this.golpes = golpes;
    }

    private static Golpe g(int tick, double alcance, double angulo, float dano, Efecto efecto) {
        return new Golpe(tick, alcance, angulo, dano, efecto);
    }

    public static AccionHacha de(int i) {
        AccionHacha[] v = values();
        return i >= 0 && i < v.length ? v[i] : LIGERO1;
    }

    public boolean runicoLigero() {
        return this == RUN_HEL || this == RUN_TEMPEST;
    }

    public boolean runicoPesado() {
        return this == RUN_FRENZY || this == RUN_RIOS;
    }
}
