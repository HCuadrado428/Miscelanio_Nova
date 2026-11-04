package com.miscelanea.bromas;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Efecto de una broma: no hace nada por sí mismo, solo marca a la víctima (con su icono, para que sepa por qué le pasa
 * lo que le pasa) mientras dura. Lo que hace cada uno está en {@link Bromas} (servidor) y en el cliente.
 */
public class BromaEfecto extends MobEffect {
    public BromaEfecto(MobEffectCategory c, int color) {
        super(c, color);
    }
}
