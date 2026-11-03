package com.miscelanea.voz;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Efecto que solo cambia la voz (helio, tartamudeo, ventilador): no hace nada por tick. El cambio de voz lo aplica el
 * cliente de quien escucha (ver {@code com.miscelanea.client.voz.Voces}) y el servidor avisa de quién tiene qué con
 * {@link VozSync}; el tartamudeo además repite sílabas en el chat ({@link Tartamudeo}).
 */
public class VozEffect extends MobEffect {
    public VozEffect(MobEffectCategory cat, int color) {
        super(cat, color);
    }
}
