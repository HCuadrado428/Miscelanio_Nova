package com.miscelanea.dream;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * «Mordisco venenoso» (esencia de Dream): mientras dura, clic derecho con la mano vacía sobre un jugador o un mob lo
 * agarra y empieza a morderle. El efecto en sí no hace nada en el tick: todo va en {@link Mordiscos}.
 */
public class MordiscoEfecto extends MobEffect {
    public MordiscoEfecto() {
        super(MobEffectCategory.BENEFICIAL, DreamRegistro.COLOR_MORDISCO);
    }
}
