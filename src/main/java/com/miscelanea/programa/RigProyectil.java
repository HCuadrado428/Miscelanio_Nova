package com.miscelanea.programa;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Rig Stun lanzado (aturde en un radio al chocar) o mina del Rig Blind (espera en el suelo y ciega con humo). */
public class RigProyectil extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> MINA = SynchedEntityData.defineId(RigProyectil.class, EntityDataSerializers.BOOLEAN);
    private int vida;

    public RigProyectil(EntityType<? extends RigProyectil> type, Level level) {
        super(type, level);
    }

    public RigProyectil(Level level, LivingEntity quien, ItemStack s, boolean mina) {
        super(ModEntities.RIG_PROYECTIL.get(), quien, level);
        setItem(s);
        entityData.set(MINA, mina);
        if (mina) setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MINA, false);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.RIG_STUN.get();
    }

    public boolean mina() {
        return entityData.get(MINA);
    }

    @Override
    public void tick() {
        if (!mina()) {
            super.tick();
            return;
        }
        baseTick();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (!(level() instanceof ServerLevel sl)) return;
        if (++vida > 20 * 60 * 5) discard();
        if (tickCount % 20 == 0) sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 1, 0.05, 0, 0.05, 0);
        var cerca = sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(2.0), Mob::isAlive);
        if (!cerca.isEmpty()) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.6F);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 80, 1.6, 0.8, 1.6, 0.02);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 40, 1.2, 0.8, 1.2, 0.02);
            for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(4.0), Mob::isAlive)) Rigs.cegar(m, 160);
            discard();
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (mina() || !(level() instanceof ServerLevel sl)) return;
        sl.playSound(null, getX(), getY(), getZ(), com.miscelanea.registry.ModSounds.DESCARGA.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 70, 1.8, 0.6, 1.8, 0.4);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(4.0), Mob::isAlive)) {
            Rigs.aturdir(m, 120);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.getX(), m.getY() + 1, m.getZ(), 15, 0.3, 0.6, 0.3, 0.2);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Mina", mina());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(MINA, tag.getBoolean("Mina"));
    }
}
