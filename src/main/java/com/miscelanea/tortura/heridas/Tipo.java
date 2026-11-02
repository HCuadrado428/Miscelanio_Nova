package com.miscelanea.tortura.heridas;

import java.util.Locale;

/**
 * Clase de herida. El orden importa: es la fila del atlas de calcomanías (textures/heridas/calcomanias.png).
 */
public enum Tipo {
    /** Espadas, hachas, flechas, mordiscos: línea roja oscura con el labio claro; costra y cicatriz al curar. */
    CORTE,
    /** Golpes contundentes (puños, caídas, mazos, explosiones): morado → verde → amarillo. */
    MORATON,
    /** Fuego, lava, rayos: carne chamuscada con ampollas; piel rosada al curar. */
    QUEMADURA,
    /** Arañazos leves (cactus, zarzas, golpes flojos con filo): trazos finos que se van pronto. */
    RASGUNO;

    public static final Tipo[] V = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String clave() {
        return "miscelanea.heridas.tipo." + id();
    }

    public static Tipo de(int i) {
        return V[Math.floorMod(i, V.length)];
    }

    public static Tipo porId(String s) {
        for (Tipo t : V) if (t.id().equalsIgnoreCase(s)) return t;
        return null;
    }
}
