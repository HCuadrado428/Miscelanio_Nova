package com.miscelanea.medigun;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** ÜberCarga: no recibes daño (salvo el vacío y /kill). Lo pone la medigun. */
public class UberEffect extends MobEffect {
    public UberEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE8463A);
    }
}
