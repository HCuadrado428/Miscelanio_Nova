package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.ObjetoTortura;

/**
 * Llave del carcelero: abre y cierra con llave la jaula colgante (clic derecho a la jaula), abre el candado del cepo
 * (clic derecho al cepo) y quita la bola de preso (clic derecho al preso). La lógica está en cada sistema.
 */
public class LlaveCarceleroItem extends ObjetoTortura {
    public LlaveCarceleroItem(Properties p) {
        super(p, 2);
    }
}
