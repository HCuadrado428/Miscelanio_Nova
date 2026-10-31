package com.miscelanea.consola;

/** Los juegos de la consola de bolsillo. Tetrominós viene incorporado; los demás necesitan su cartucho. */
public enum Juego {
    TETROMINOS("tetrominos", true),
    SERPIENTE("serpiente", false),
    LADRILLOS("ladrillos", false),
    PONG("pong", false),
    PAJARO("pajaro", false),
    INVASORES("invasores", false);

    public final String id;
    public final boolean incorporado;

    Juego(String id, boolean incorporado) {
        this.id = id;
        this.incorporado = incorporado;
    }

    public String item() {
        return "cartucho_" + id;
    }

    public String clave() {
        return "juego.miscelanea." + id;
    }

    public static Juego de(int i) {
        Juego[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
