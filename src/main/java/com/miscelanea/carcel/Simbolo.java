package com.miscelanea.carcel;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Los 6 símbolos de las rejas, llaves y monitores. */
public final class Simbolo {
    public static final String[] NOMBRES = {"circulo", "triangulo", "cruz", "rayo", "ojo", "calavera"};
    public static final int TOTAL = NOMBRES.length;
    public static final IntegerProperty PROPIEDAD = IntegerProperty.create("simbolo", 0, TOTAL - 1);
    public static final String NBT = "Simbolo";

    private Simbolo() {}
}
