package com.miscelanea.armasfuego.granada;

/** Los tres tipos de granada de cobre. */
public enum TipoGranada {
    /** Una nube de humo de 10 s que ciega y hace que los mobs pierdan de vista su objetivo. */
    HUMO("humo"),
    /** Metralla: explosión pequeña que hiere y empuja (rompe bloques solo si la config lo permite). */
    METRALLA("metralla"),
    /** Pegamento: salpica miel que deja pringados (lentos y sin saltar) a los que pilla. */
    MIEL("miel");

    public final String id;

    TipoGranada(String id) {
        this.id = id;
    }
}
