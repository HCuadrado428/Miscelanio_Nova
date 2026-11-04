package com.miscelanea.glitch;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * «Glitch»: lo que deja la onda de una muestra al pasar por encima de alguien. Suelta píxeles a su alrededor y, en el
 * cliente del jugador afectado, rompe un poco la pantalla ({@code GlitchPantalla}). Mientras dura, se le puede sacar una
 * muestra con el frasco de muestras.
 */
public class GlitchEfecto extends MobEffect {
    public GlitchEfecto() {
        super(MobEffectCategory.HARMFUL, GlitchRegistro.COLOR);
    }

    @Override
    public boolean isDurationEffectTick(int duracion, int nivel) {
        return duracion % 3 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int nivel) {
        // los efectos solo hacen tick en el servidor: las partículas se mandan a quien mire
        if (e.level() instanceof net.minecraft.server.level.ServerLevel sl && e.getRandom().nextInt(2) == 0)
            sl.sendParticles(GlitchRegistro.PARTICULA.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1,
                    e.getBbWidth() * 0.4, e.getBbHeight() * 0.35, e.getBbWidth() * 0.4, 0.0);
    }
}
