package com.miscelanea.arcade;

/** Juegos de la recreativa: los exclusivos (a todo color) y los de la consola (aquí no hacen falta cartuchos). */
public final class ArcadeJuegos {
    public static final String[] IDS = {"chatarra", "mazmorra", "turbo", "astro", "tetrominos", "serpiente", "ladrillos", "pong", "pajaro", "invasores"};
    /** Juegos que se pueden jugar a la vez entre dos en la recreativa doble. */
    public static final String[] DOS = {"chatarra"};

    private ArcadeJuegos() {}

    public static boolean valido(String id) {
        for (String s : IDS) if (s.equals(id)) return true;
        return false;
    }

    public static boolean dosJugadores(String id) {
        for (String s : DOS) if (s.equals(id)) return true;
        return false;
    }
}
