package com.miscelanea.carcel.enemigos;

import com.miscelanea.registry.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Gritón: débil, pero si te ve se para, echa la cabeza atrás y grita. Todos los enemigos a 32 bloques
 * van a por ti, y tú quedas marcado (brillando) unos segundos.
 */
public class Griton extends EnemigoCarcel {
    private static final EntityDataAccessor<Integer> GRITANDO = SynchedEntityData.defineId(Griton.class, EntityDataSerializers.INT);
    private int enfriar;

    public Griton(EntityType<? extends Monster> type, Level level) {
        super(type, level, "griton");
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(GRITANDO, 0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        int g = entityData.get(GRITANDO);
        if (g > 0) {
            entityData.set(GRITANDO, g - 1);
            getNavigation().stop();
            if (g == 30) gritar();
            return;
        }
        if (enfriar > 0) enfriar--;
        LivingEntity t = getTarget();
        if (t != null && enfriar == 0 && hasLineOfSight(t) && distanceTo(t) < 24) {
            entityData.set(GRITANDO, 40);   // 2 s: se prepara y grita
            enfriar = 240;
            getLookControl().setLookAt(t);
        }
    }

    private void gritar() {
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity t = getTarget();
        sl.playSound(null, getX(), getY(), getZ(), ModSounds.GRITO.get(), SoundSource.HOSTILE, 3.0F, 0.9F + random.nextFloat() * 0.2F);
        if (t == null) return;
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0), this);
        for (Monster m : sl.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(32), m -> m != this && m.isAlive())) {
            m.setTarget(t);
            m.getNavigation().moveTo(t, 1.25);
        }
    }

    public boolean gritando() {
        return entityData.get(GRITANDO) > 0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mover", 5, state -> {
            if (gritando()) return state.setAndContinue(animUnaVez("gritar"));
            return moverse(state);
        }));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.4F;
    }
}
