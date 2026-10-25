package com.miscelanea.carcel.enemigos;

import com.miscelanea.programa.Jugadores;
import com.miscelanea.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

/** Pusher: máscara de gas y tanque a la espalda. Cuando te alcanza te inyecta gas: te baja mucho la cordura. */
public class Pusher extends EnemigoCarcel {
    private int enfriar;

    public Pusher(EntityType<? extends Monster> type, Level level) {
        super(type, level, "pusher");
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || estadoEspecial() != 0) return;
        if (enfriar > 0) {
            enfriar--;
            return;
        }
        LivingEntity t = getTarget();
        if (t instanceof ServerPlayer p && distanceTo(p) < 1.8 && hasLineOfSight(p)) {
            enfriar = 200;
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            p.hurt(damageSources().mobAttack(this), 2.0F);
            Jugadores.drenar(p, 120);
            p.addEffect(new MobEffectInstance(ModEffects.PSICOSIS.get(), 100, 0, false, false, true));
            ServerLevel sl = (ServerLevel) level();
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.2F, 0.6F);
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 1.4, p.getZ(), 40, 0.4, 0.4, 0.4, 0.01);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mover", 5, this::moverse));
        c.add(new AnimationController<>(this, "golpe", 0, state -> {
            if (swinging) return state.setAndContinue(animUnaVez("golpe"));
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }
}
