package com.miscelanea.tortura.aparatos;

import java.util.Locale;

/**
 * Herramientas del maletín del interrogador (la rueda que sale con el maletín en la mano). El orden importa: viaja por
 * red como ordinal y es el orden de la rueda (arriba, derecha, abajo, izquierda).
 * <ul>
 *   <li>{@link #PINZAS}: las pinzas de la batería del coche: descarga, convulsiones y una quemadura pequeña.</li>
 *   <li>{@link #LLAVE}: llave inglesa: golpe seco en rodillas o brazos (moratón).</li>
 *   <li>{@link #ALICATES}: le arranca un diente (corte en la cabeza, el diente cae al suelo, grito).</li>
 *   <li>{@link #ADRENALINA}: jeringuilla de adrenalina al pecho. Con el corazón parado lo reanima; si no, lo acelera.</li>
 * </ul>
 */
public enum Herramienta {
    PINZAS(26F, 8F), LLAVE(18F, 6F), ALICATES(24F, 8F), ADRENALINA(45F, 10F);

    public static final Herramienta[] V = values();
    /** Pulsaciones que sube (mínimo y extra al azar). */
    public final float sube, azar;

    Herramienta(float sube, float azar) {
        this.sube = sube;
        this.azar = azar;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String clave() {
        return "herramienta.miscelanea." + id();
    }

    public static Herramienta de(int i) {
        return V[Math.floorMod(i, V.length)];
    }
}
