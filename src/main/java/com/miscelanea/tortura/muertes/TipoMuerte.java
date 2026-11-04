package com.miscelanea.tortura.muertes;

import javax.annotation.Nullable;

/**
 * Las cinemáticas de muerte que hay. {@link #muerte} = tick en el que muere (el servidor mata ahí con el daño del
 * aparato); tiene que coincidir con {@code MuertesDatos.<NOMBRE>_MUERTE} del cliente (las dos salen de
 * scratchpad/muertes.py).
 */
public enum TipoMuerte {
    DONCELLA(1, 84),
    POTRO(2, 72),
    SILLA(3, 96),
    PENDULO(4, 104),
    INMERSION(5, 100),
    COLLAR(6, 84),
    CAJA(7, 100),
    PARO(8, 96),
    COLGADO(9, 90);

    public final int id, muerte;

    TipoMuerte(int id, int muerte) {
        this.id = id;
        this.muerte = muerte;
    }

    @Nullable
    public static TipoMuerte de(int id) {
        for (TipoMuerte t : values()) if (t.id == id) return t;
        return null;
    }
}
