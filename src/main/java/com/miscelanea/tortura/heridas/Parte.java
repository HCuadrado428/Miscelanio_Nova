package com.miscelanea.tortura.heridas;

import java.util.Locale;

/** Zonas del cuerpo donde puede caer una herida (las mismas piezas del modelo del jugador). */
public enum Parte {
    CABEZA, TORSO, BRAZO_DER, BRAZO_IZQ, PIERNA_DER, PIERNA_IZQ;

    public static final Parte[] V = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String clave() {
        return "miscelanea.heridas.parte." + id();
    }

    public int maxHeridas() {
        return this == CABEZA ? 3 : this == TORSO ? 5 : 4;
    }

    public boolean esPierna() {
        return this == PIERNA_DER || this == PIERNA_IZQ;
    }

    public boolean esBrazo() {
        return this == BRAZO_DER || this == BRAZO_IZQ;
    }

    public boolean esExtremidad() {
        return esPierna() || esBrazo();
    }

    /** Lado derecho del personaje (para elegir la cara exterior de brazos y piernas). */
    public boolean derecha() {
        return this == BRAZO_DER || this == PIERNA_DER;
    }

    public static Parte de(int i) {
        return V[Math.floorMod(i, V.length)];
    }

    public static Parte porId(String s) {
        for (Parte p : V) if (p.id().equalsIgnoreCase(s)) return p;
        return null;
    }
}
