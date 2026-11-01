package com.miscelanea.tirolina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Polea que baja por el cable con el jugador colgado: gravedad a lo largo de la pendiente, rozamiento y velocidad máxima. */
public class Polea extends Entity {
    private static final EntityDataAccessor<BlockPos> A = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> B = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> T = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> V = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.FLOAT);
    private int vida;

    public Polea(EntityType<? extends Polea> t, net.minecraft.world.level.Level l) {
        super(t, l);
        this.noPhysics = true;
    }

    public void preparar(BlockPos a, BlockPos b, float t, float v) {
        entityData.set(A, a);
        entityData.set(B, b);
        entityData.set(T, t);
        entityData.set(V, v);
        Vec3 p = Cable.punto(Cable.anclaje(a), Cable.anclaje(b), t);
        setPos(p.x, p.y - 0.75, p.z);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(A, BlockPos.ZERO);
        entityData.define(B, BlockPos.ZERO);
        entityData.define(T, 0F);
        entityData.define(V, 0F);
    }

    public Vec3 a() {
        return Cable.anclaje(entityData.get(A));
    }

    public Vec3 b() {
        return Cable.anclaje(entityData.get(B));
    }

    public float t() {
        return entityData.get(T);
    }

    public float velocidad() {
        return entityData.get(V);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        vida++;
        Entity jinete = getFirstPassenger();
        if (jinete == null || vida > 20 * 120) {
            discard();
            return;
        }
        Vec3 a = a(), b = b();
        float t = t(), v = velocidad();
        Vec3 tan = Cable.tangente(a, b, t);
        double largo = Math.max(0.5, tan.length());
        Vec3 u = tan.scale(1 / largo);
        v += (float) (-0.045 * u.y);                       // gravedad a lo largo del cable
        v *= 0.994F;                                         // rozamiento del aire
        v -= Math.signum(v) * 0.0015F;                       // rozamiento de la polea
        v = Math.max(-1.3F, Math.min(1.3F, v));
        t += (float) (v / largo);
        if (t >= 1F || t <= 0F) {
            t = Math.max(0F, Math.min(1F, t));
            llegar(jinete, u.scale(Math.signum(v)), Math.abs(v));
            return;
        }
        entityData.set(T, t);
        entityData.set(V, v);
        Vec3 p = Cable.punto(a, b, t);
        setPos(p.x, p.y - 0.75, p.z);
        if (Math.abs(v) > 0.6F && tickCount % 2 == 0)
            ((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.05);
    }

    private void llegar(Entity jinete, Vec3 dir, float v) {
        jinete.stopRiding();
        // se suelta un poco antes del poste para no chocar con él
        Vec3 fin = Cable.punto(a(), b(), t()).subtract(dir.scale(1.0)).subtract(0, 2.4, 0);
        jinete.teleportTo(fin.x, fin.y, fin.z);
        jinete.setDeltaMovement(dir.x * v * 0.5, 0.25, dir.z * v * 0.5);
        jinete.hurtMarked = true;
        jinete.fallDistance = 0;
        discard();
    }

    @Override
    public double getPassengersRidingOffset() {
        return -1.7;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        return position().subtract(0, 2.0, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
