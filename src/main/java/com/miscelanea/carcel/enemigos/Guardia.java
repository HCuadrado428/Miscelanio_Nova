package com.miscelanea.carcel.enemigos;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;

/**
 * Guardia: patrulla, va a los ruidos y te persigue a machetazos (los Grunts de The Outlast Trials).
 * A veces te agarra: te sujeta y te hace daño hasta que te sueltas (forcejeando o dándole con una botella o un ladrillo).
 */
public class Guardia extends EnemigoCarcel {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> AGARRANDO =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Guardia.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private net.minecraft.world.entity.player.Player victima;
    private int agarre, enfriar = 100;

    public Guardia(EntityType<? extends Monster> type, Level level) {
        super(type, level, "guardia");
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AGARRANDO, false);
    }

    public boolean agarrando() {
        return entityData.get(AGARRANDO);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource s, float n) {
        boolean r = super.hurt(s, n);
        if (r && victima != null) soltar();   // un golpe o un objeto lanzado te libera
        return r;
    }

    private void soltar() {
        victima = null;
        entityData.set(AGARRANDO, false);
        enfriar = 160;
        com.miscelanea.programa.Rigs.aturdir(this, 30);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (estadoEspecial() != 0) {
            if (victima != null) soltar();
            return;
        }
        if (victima != null) {
            if (!victima.isAlive() || distanceTo(victima) > 3 || com.miscelanea.programa.Jugadores.caido(victima)) {
                soltar();
                return;
            }
            getNavigation().stop();
            getLookControl().setLookAt(victima);
            net.minecraft.world.phys.Vec3 frente = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(0.9));
            victima.teleportTo(frente.x, victima.getY(), frente.z);
            victima.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
            if (++agarre % 20 == 0) {
                victima.hurt(damageSources().mobAttack(this), 2.0F);
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            if (agarre >= 70) soltar();
            return;
        }
        if (enfriar > 0) enfriar--;
        if (enfriar == 0 && getTarget() instanceof net.minecraft.world.entity.player.Player p && distanceTo(p) < 1.7 && random.nextFloat() < 0.05F) {
            victima = p;
            agarre = 0;
            entityData.set(AGARRANDO, true);
            playSound(SoundEvents.ZOMBIE_VILLAGER_AMBIENT, 1.2F, 0.6F);
        }
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mover", 5, state -> agarrando() ? state.setAndContinue(anim("agarrar")) : moverse(state)));
        c.add(new AnimationController<>(this, "golpe", 0, state -> {
            if (swinging) return state.setAndContinue(animUnaVez("golpe"));
            state.getController().forceAnimationReset();
            return software.bernie.geckolib.core.object.PlayState.STOP;
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
        return 0.7F;
    }
}
