package com.miscelanea.tortura.saw;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Estado de la puerta de salida (también la propiedad "estado" del bloque, que decide el modelo y la colisión). */
public enum FaseSalida implements StringRepresentable {
    /** Parada: el captor pone el tiempo (arriba) y la arranca (abajo). */
    ESPERA,
    /** Cuenta atrás: cerrada pero sin echar la llave; quien la abre, sale. */
    CUENTA,
    /** Abierta un momento (alguien está saliendo). */
    ABIERTA,
    /** Se acabó el tiempo: cerrada con llave y las luces apagadas. */
    CERRADA;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FaseSalida de(int i) {
        FaseSalida[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
