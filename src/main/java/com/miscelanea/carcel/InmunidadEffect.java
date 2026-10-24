package com.miscelanea.carcel;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Lo da el Antídoto: el gas no te afecta durante un rato. */
public class InmunidadEffect extends MobEffect {
    public InmunidadEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x3FD6C8);
    }
}
