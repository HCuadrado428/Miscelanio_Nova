package com.miscelanea.arcade;

/** Juegos de la recreativa: el exclusivo y los de la consola (aquí no hacen falta cartuchos). */
public final class ArcadeJuegos {
    public static final String[] IDS = {"astro", "tetrominos", "serpiente", "ladrillos", "pong", "pajaro", "invasores"};

    private ArcadeJuegos() {}

    public static boolean valido(String id) {
        for (String s : IDS) if (s.equals(id)) return true;
        return false;
    }
}
