package com.miscelanea.carcel.enemigos;

import com.miscelanea.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;

/**
 * Gritón (el Screamer de The Outlast Trials): narcoléptico, se pasa el rato dormido de pie. No te caza.
 * Si lo despiertas (ruido, pasar cerca sin agacharte o pegarle) grita por el altavoz del pecho: todos los
 * enemigos a 32 bloques van a por ti y quedas marcado. Luego huye a otro sitio y se vuelve a dormir.
 */
public class Griton extends EnemigoCarcel {
    private static final EntityDataAccessor<Integer> GRITANDO = SynchedEntityData.defineId(Griton.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DORMIDO = SynchedEntityData.defineId(Griton.class, EntityDataSerializers.BOOLEAN);
    @Nullable private LivingEntity culpable;
    private int huyendo;

    public Griton(EntityType<? extends Monster> type, Level level) {
        super(type, level, "griton");
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));   // no patrulla ni persigue: duerme, grita y huye
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(GRITANDO, 0);
        entityData.define(DORMIDO, true);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor l, DifficultyInstance d, MobSpawnType t, @Nullable SpawnGroupData g, @Nullable CompoundTag tag) {
        entityData.set(DORMIDO, true);
        return super.finalizeSpawn(l, d, t, g, tag);
    }

    public boolean dormido() {
        return entityData.get(DORMIDO);
    }

    public boolean gritando() {
        return entityData.get(GRITANDO) > 0;
    }

    /** Ruido, golpe o alguien que pasa al lado: se despierta y grita. */
    public void despertar(@Nullable Entity causa) {
        if (!dormido() || level().isClientSide) return;
        entityData.set(DORMIDO, false);
        entityData.set(GRITANDO, 40);
        LivingEntity quien = causa instanceof LivingEntity le ? le : null;
        if (quien == null || quien instanceof Monster) {
            quien = level().getNearestPlayer(this, 24);
        }
        culpable = quien;
        if (quien != null) getLookControl().setLookAt(quien);
        getNavigation().stop();
    }

    @Override
    public boolean hurt(DamageSource s, float n) {
        boolean r = super.hurt(s, n);
        if (r) despertar(s.getEntity());
        return r;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        int g = entityData.get(GRITANDO);
        if (g > 0) {
            entityData.set(GRITANDO, g - 1);
            getNavigation().stop();
            if (culpable != null) getLookControl().setLookAt(culpable);
            if (g == 30) gritar();
            if (g == 1) huir();
            return;
        }
        if (dormido()) {
            getNavigation().stop();
            // pasar cerca sin agacharse lo despierta
            Player p = level().getNearestPlayer(this, 3.5);
            if (p != null && !p.isCrouching() && !p.isCreative() && !p.isSpectator() && puedeVer(p)) despertar(p);
            return;
        }
        // huyendo: cuando llega (o se cansa) se vuelve a dormir donde esté
        if (huyendo > 0) huyendo--;
        if (huyendo == 0 || getNavigation().isDone()) {
            entityData.set(DORMIDO, true);
            culpable = null;
        }
    }

    private void huir() {
        Vec3 lejos = culpable != null ? DefaultRandomPos.getPosAway(this, 20, 7, culpable.position()) : DefaultRandomPos.getPos(this, 20, 7);
        if (lejos != null) getNavigation().moveTo(lejos.x, lejos.y, lejos.z, 1.5);
        huyendo = 200;
    }

    private void gritar() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.playSound(null, getX(), getY(), getZ(), ModSounds.GRITO.get(), SoundSource.HOSTILE, 3.0F, 0.9F + random.nextFloat() * 0.2F);
        LivingEntity t = culpable;
        if (t == null || !t.isAlive()) return;
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0), this);
        for (Monster m : sl.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(32), m -> m != this && m.isAlive() && !(m instanceof Griton))) {
            m.setTarget(t);
            m.getNavigation().moveTo(t, 1.25);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormido", dormido());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormido")) entityData.set(DORMIDO, tag.getBoolean("Dormido"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mover", 5, state -> {
            if (gritando()) return state.setAndContinue(animUnaVez("gritar"));
            if (dormido()) return state.setAndContinue(anim("dormir"));
            return moverse(state);
        }));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return dormido() ? SoundEvents.FOX_SLEEP : SoundEvents.ZOMBIE_AMBIENT;   // ronquidos
    }

    @Override
    public int getAmbientSoundInterval() {
        return dormido() ? 50 : 120;
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
        return dormido() ? 0.5F : 1.4F;
    }
}
