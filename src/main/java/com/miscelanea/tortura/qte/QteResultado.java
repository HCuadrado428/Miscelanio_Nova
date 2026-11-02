package com.miscelanea.tortura.qte;

/** Cómo ha terminado un minijuego. Viaja por red como ordinal. */
public enum QteResultado {
    /** Superado. */
    EXITO,
    /** Superado de forma perfecta (CIRCULO: todas en la franja buena; RITMO: casi todo perfecto). Cuenta como éxito. */
    PERFECTO,
    /** Fallado (o el tiempo se acabó, o el servidor no se creyó el resultado). */
    FALLO,
    /** GANZUA: la ganzúa se ha roto. Cuenta como fallo. */
    ROTO,
    /** Interrumpido: el jugador abrió un menú, se alejó, se desconectó, murió o lo canceló otro sistema. */
    CANCELADO;

    public boolean exito() {
        return this == EXITO || this == PERFECTO;
    }

    public static QteResultado de(int i) {
        QteResultado[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
