package com.miscelanea.tortura.efectos;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Fantasma (solo creativo). Quien lo lleva no se ve: ni modelo, ni armadura, ni objetos, ni partículas, ni nombre, ni
 * sombra. Solo asoma, medio transparente y pálido, por el rabillo del ojo de los demás: cuando alguien lo tiene en el
 * borde de su campo de visión. En cuanto lo mira de frente se desvanece (unas partículas de alma y ceniza) y vuelve a
 * asomar al apartar la vista. Nunca se ve a la espalda. En el servidor no cambia nada físico (sigue chocando y se le
 * puede pegar a ciegas), salvo que los mobs no lo eligen como objetivo. Todo el dibujo está en el cliente
 * ({@code FantasmaCliente}); el servidor solo avisa de quién es fantasma con {@link FantasmaPacket}.
 */
public class FantasmaEffect extends MobEffect {
    /** Azul celeste muy pálido (la poción y el icono). */
    public static final int COLOR = 0xBFE8FF;

    public FantasmaEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
