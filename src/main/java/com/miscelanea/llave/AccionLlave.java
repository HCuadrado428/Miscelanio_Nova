package com.miscelanea.llave;

/**
 * Movimientos de la Llave Espada (Kingdom Hearts). Los usa el cliente (animación y movimiento) y el servidor (golpes).
 * recuperacion = ticks hasta poder encadenar la siguiente acción; coste = PM.
 */
public enum AccionLlave {
    COMBO1(6, 0, false, g(3, 3.3, 130, 6, Efecto.NORMAL)),
    COMBO2(6, 0, false, g(3, 3.3, 130, 6, Efecto.NORMAL)),
    GUARD_BREAK(12, 0, false, g(5, 3.6, 70, 7, Efecto.NORMAL), g(8, 3.2, 80, 6, Efecto.LANZAR)),
    EXPLOSION(14, 0, false, g(9, 4.4, 360, 9, Efecto.EXPLOSION)),
    AEREO1(5, 0, true, g(3, 3.4, 150, 5, Efecto.NORMAL)),
    AEREO2(5, 0, true, g(3, 3.4, 150, 5, Efecto.NORMAL)),
    AEREO_REMATE(12, 0, true, g(8, 3.8, 360, 10, Efecto.ABAJO)),
    EMBESTIDA(9, 0, false, g(6, 3.4, 130, 7, Efecto.NORMAL)),
    STRIKE_RAID(10, 0, false),
    PIRO(10, 8, false),
    HIELO(10, 10, false),
    CURA(14, 30, false),
    SONIC_BLADE(30, 25, false, g(4, 2.6, 360, 5, Efecto.NORMAL), g(11, 2.6, 360, 5, Efecto.NORMAL),
            g(18, 2.6, 360, 5, Efecto.NORMAL), g(25, 2.8, 360, 7, Efecto.LANZAR)),
    ARS_ARCANUM(38, 40, false, g(3, 3.4, 140, 3, Efecto.NORMAL), g(6, 3.4, 140, 3, Efecto.NORMAL), g(9, 3.4, 140, 3, Efecto.NORMAL),
            g(12, 3.4, 140, 3, Efecto.NORMAL), g(15, 3.4, 140, 3, Efecto.NORMAL), g(18, 3.4, 140, 3, Efecto.NORMAL),
            g(21, 3.4, 140, 3, Efecto.NORMAL), g(24, 3.4, 140, 3, Efecto.NORMAL), g(27, 3.4, 140, 3, Efecto.NORMAL),
            g(34, 4.0, 180, 10, Efecto.LEJOS));

    public enum Efecto { NORMAL, LANZAR, EXPLOSION, ABAJO, LEJOS }

    public record Golpe(int tick, double alcance, double angulo, float dano, Efecto efecto) {}

    public final int recuperacion;
    public final float coste;
    public final boolean aereo;
    public final Golpe[] golpes;

    AccionLlave(int recuperacion, float coste, boolean aereo, Golpe... golpes) {
        this.recuperacion = recuperacion;
        this.coste = coste;
        this.aereo = aereo;
        this.golpes = golpes;
    }

    private static Golpe g(int tick, double alcance, double angulo, float dano, Efecto efecto) {
        return new Golpe(tick, alcance, angulo, dano, efecto);
    }

    public static AccionLlave de(int i) {
        AccionLlave[] v = values();
        return i >= 0 && i < v.length ? v[i] : COMBO1;
    }

    /** Los "comandos" del menú (tecla de comando): magia y ataques especiales. */
    public static final AccionLlave[] COMANDOS = {PIRO, HIELO, CURA, SONIC_BLADE, ARS_ARCANUM};
}
