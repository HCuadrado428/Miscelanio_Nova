package com.miscelanea.arcade;

/** Juegos de la recreativa: los exclusivos (a todo color) y los de la consola (aquí no hacen falta cartuchos). */
public final class ArcadeJuegos {
    public static final String[] IDS = {"chatarra", "barrio", "duelo", "comecocos", "bloques", "paso", "pinball", "mazmorra", "turbo", "astro", "tetrominos", "serpiente", "ladrillos", "pong", "pajaro", "invasores"};
    /** Juegos que se pueden jugar a la vez entre dos en la recreativa doble. */
    public static final String[] DOS = {"chatarra", "barrio", "duelo", "comecocos", "bloques", "paso"};

    private ArcadeJuegos() {}

    /** Récords de la feria (pizarra): caseta de tiro, bolos, mejor turno de dardos, torre, tragaperras y brisca. */
    public static final String[] FERIA = {"tiro", "bolos", "dardos", "torre", "traga", "brisca"};

    public static boolean valido(String id) {
        for (String s : IDS) if (s.equals(id)) return true;
        for (String s : FERIA) if (s.equals(id)) return true;
        return false;
    }

    public static boolean dosJugadores(String id) {
        for (String s : DOS) if (s.equals(id)) return true;
        return false;
    }
}
