package com.miscelanea.effect;

import com.miscelanea.registry.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Enemigo: si dos entidades con este efecto se acercan, ambas se hacen daño.
 * Cuanto más cerca, más duele.
 */
public class EnemigoEffect extends MobEffect {
    /** Distancia a partir de la cual empiezan a hacerse daño. */
    public static final double RADIUS = 5.0;
    /** Si están más cerca que esto, el daño se duplica. */
    private static final double CLOSE_RADIUS = 2.0;
    /** Cada cuántos ticks se aplica el daño (20 = 1 segundo, encaja con los frames de invulnerabilidad). */
    private static final int DAMAGE_INTERVAL = 20;

    public EnemigoEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B1A1A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return;

        LivingEntity enemy = EffectUtil.nearestWithEffect(entity, this, RADIUS);
        if (enemy == null) return;
        EffectUtil.avisarLazo(entity, enemy, 1);

        // Chispas de odio mientras están cerca
        if (entity.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(),
                    1, 0.25, 0.1, 0.25, 0.0);
        }

        if (entity.tickCount % DAMAGE_INTERVAL != 0) return;

        // Medio corazón base por segundo, +1 corazón por nivel extra; el doble si están pegados.
        float damage = 1.0F + 2.0F * amplifier;
        if (entity.distanceTo(enemy) <= CLOSE_RADIUS) {
            damage *= 2.0F;
        }

        // El daño no se atribuye directamente al enemigo para que funcione aunque el PvP esté desactivado,
        // pero sí lo marcamos como "último atacante" para el mensaje de muerte y para que los mobs se peleen.
        if (entity.hurt(ModDamageTypes.enemigo(level), damage)) {
            if (enemy instanceof Player player) {
                entity.setLastHurtByPlayer(player);
            }
            entity.setLastHurtByMob(enemy);

            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    3, 0.2, 0.2, 0.2, 0.1);
        }
    }
}
