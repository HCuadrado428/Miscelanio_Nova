package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.ObjetoTortura;

/**
 * Bola de preso (The Escapists): clic derecho a un cautivo (con grilletes, enlazado, en el cepo... o a cualquiera en
 * creativo) para cerrarle el grillete del tobillo. La bola va detrás arrastrando por el suelo con su cadena. Quien se la
 * puso la quita con clic derecho con otra bola, con la llave del carcelero o con Shift + clic derecho con la mano vacía.
 * (El uso sobre entidades lo gestiona {@link CastigoEventos}.)
 */
public class BolaPresoItem extends ObjetoTortura {
    public BolaPresoItem(Properties p) {
        super(p, 3);
    }
}
