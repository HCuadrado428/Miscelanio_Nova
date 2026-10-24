package com.miscelanea.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Cupido: si dos entidades (jugadores o mobs) con este efecto están a 32 bloques o menos,
 * se atraen solas la una hacia la otra y no pueden alejarse.
 *
 * Cada entidad con el efecto se empuja a sí misma hacia su pareja más cercana, así que
 * el tirón es mutuo: los dos acaban en el punto medio.
 */
public class CupidoEffect extends MobEffect {
    /** Radio en el que dos enamorados se detectan. */
    public static final double RADIUS = 32.0;
    /** Distancia a la que ya se consideran "juntos" y dejan de tirar. */
    private static final double TOGETHER_DISTANCE = 1.5;
    /** Tirón máximo por tick, para no salir disparados. */
    private static final double MAX_PULL = 0.9;

    public CupidoEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFF5FA2);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // se ejecuta todos los ticks
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return;

        LivingEntity partner = EffectUtil.nearestWithEffect(entity, this, RADIUS);
        if (partner == null) return;

        Vec3 diff = partner.position().subtract(entity.position());
        double distance = diff.length();

        if (distance > TOGETHER_DISTANCE) {
            Vec3 dir = diff.normalize();

            // Cuanto más lejos, más fuerte tira. El nivel II tira más.
            double strength = Math.min(
                    0.08 + (distance - TOGETHER_DISTANCE) * 0.04 * (1.0 + amplifier * 0.5),
                    MAX_PULL);

            Vec3 velocity = entity.getDeltaMovement();

            // "No se pueden separar": se anula cualquier movimiento que los aleje.
            double towards = velocity.dot(dir);
            if (towards < 0) {
                velocity = velocity.subtract(dir.scale(towards));
            }

            velocity = velocity.add(dir.scale(strength));

            // Pequeño impulso hacia arriba si está en el suelo, para superar bloques de un escalón.
            if (entity.onGround() && Math.abs(dir.y) < 0.5 && entity.horizontalCollision) {
                velocity = velocity.add(0, 0.42, 0);
            }

            entity.setDeltaMovement(velocity);
            entity.hurtMarked = true;   // obliga al servidor a mandar la velocidad al cliente (necesario en jugadores)
            entity.fallDistance = 0.0F; // que el tirón no les mate de caída
        }

        // Corazoncitos
        if (entity.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.HEART,
                    entity.getX(), entity.getY() + entity.getBbHeight() + 0.4, entity.getZ(),
                    1, 0.3, 0.15, 0.3, 0.0);

            if (distance <= TOGETHER_DISTANCE * 2) {
                Vec3 mid = entity.position().add(diff.scale(0.5));
                level.sendParticles(ParticleTypes.HEART,
                        mid.x, mid.y + entity.getBbHeight(), mid.z,
                        2, 0.25, 0.25, 0.25, 0.0);
            }
        }
    }
}
