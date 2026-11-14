package com.miscelanea.armasfuego.escopeta;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Pringado de miel (cartucho de miel): no deja saltar mientras dura (ver {@link EscopetaEventos#salto}). */
public class PringadoEffect extends MobEffect {
    public PringadoEffect() {
        super(MobEffectCategory.HARMFUL, 0xF2A51B);
    }
}
