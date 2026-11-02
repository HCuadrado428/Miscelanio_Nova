package com.miscelanea.tortura;

/**
 * Grilletes. Clic derecho a alguien desde detrás y muy cerca: se los pones (Shift: también la cadena).
 * Sobre alguien ya atado: clic derecho los quita (si fuiste tú o estás en creativo); Shift + clic derecho engancha / suelta la cadena.
 * (El uso lo gestiona {@link TorturaEventos}.)
 */
public class GrilletesItem extends ObjetoTortura {
    public GrilletesItem(Properties p) {
        super(p, 3);
    }
}
