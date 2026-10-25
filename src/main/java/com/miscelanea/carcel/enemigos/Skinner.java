package com.miscelanea.carcel.enemigos;

import com.miscelanea.programa.Jugadores;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;

import java.util.Optional;
import java.util.UUID;

/**
 * Skinner Man: aparece cuando te quedas sin cordura (psicosis). Solo lo ves tú, atraviesa paredes y te persigue
 * hasta que se te pasa la psicosis. Si te alcanza te hace mucho daño. No se le puede matar.
 */
public class Skinner extends EnemigoCarcel {
    private static final EntityDataAccessor<Optional<UUID>> VICTIMA = SynchedEntityData.defineId(Skinner.class, EntityDataSerializers.OPTIONAL_UUID);
    private int vida;

    public Skinner(EntityType<? extends Monster> type, Level level) {
        super(type, level, "skinner");
        noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VICTIMA, Optional.empty());
    }

    public void setVictima(UUID u) {
        entityData.set(VICTIMA, Optional.of(u));
    }

    public Optional<UUID> victima() {
        return entityData.get(VICTIMA);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public boolean hurt(DamageSource s, float n) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
        if (level().isClientSide) return;
        if (++vida > 20 * 60 || victima().isEmpty()) {
            discard();
            return;
        }
        if (!(level().getPlayerByUUID(victima().get()) instanceof ServerPlayer p) || !p.isAlive() || p.level() != level()) {
            discard();
            return;
        }
        Vec3 dir = p.position().subtract(position());
        double d = dir.length();
        if (d < 1.2) {
            Jugadores.atrapadoPorSkinner(p);
            discard();
            return;
        }
        Vec3 v = dir.normalize().scale(0.19);
        setDeltaMovement(v);
        move(net.minecraft.world.entity.MoverType.SELF, v);
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        setYBodyRot(getYRot());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mover", 5, state -> state.setAndContinue(anim("correr"))));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }
}
