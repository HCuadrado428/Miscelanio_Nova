package com.miscelanea.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

final class EffectUtil {
    private EffectUtil() {}

    /** Todas las entidades vivas (jugadores o mobs) con el efecto dentro del radio, excluyendo a la propia entidad. */
    static List<LivingEntity> othersWithEffect(LivingEntity self, MobEffect effect, double radius) {
        double r2 = radius * radius;
        return self.level().getEntitiesOfClass(
                LivingEntity.class,
                self.getBoundingBox().inflate(radius),
                other -> other != self
                        && other.isAlive()
                        && !other.isSpectator()
                        && other.hasEffect(effect)
                        && other.distanceToSqr(self) <= r2);
    }

    /** La entidad más cercana con el efecto, o null si no hay ninguna. */
    @Nullable
    static LivingEntity nearestWithEffect(LivingEntity self, MobEffect effect, double radius) {
        return othersWithEffect(self, effect, radius).stream()
                .min(Comparator.comparingDouble(other -> other.distanceToSqr(self)))
                .orElse(null);
    }
}
