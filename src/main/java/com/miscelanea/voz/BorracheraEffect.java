package com.miscelanea.voz;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Borrachera: a los jugadores se les mece la cámara, los controles se van solos, ven doble, hablan con lengua de
 * trapo (voz y chat) y tienen hipo; agachados 3 s seguidos vomitan y se les pasa. Los mobs andan en zigzag. Aquí solo
 * va el tick de los mobs (lo del jugador necesita quitar el efecto, y eso no se puede hacer desde este tick: ver
 * {@link VozEventos}).
 */
public class BorracheraEffect extends MobEffect {
    public static final int COLOR = 0xC98A3A;

    public BorracheraEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean isDurationEffectTick(int duracion, int amplificador) {
        return duracion % 4 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplificador) {
        if (e.level().isClientSide || !(e instanceof PathfinderMob m)) return;
        Borrachera.tickMob(m, amplificador);
    }
}
