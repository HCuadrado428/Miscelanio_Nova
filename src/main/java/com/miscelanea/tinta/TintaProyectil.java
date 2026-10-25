package com.miscelanea.tinta;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Chorro de tinta: vuela con un poco de caída, va dejando gotas en el suelo y al chocar salpica (pinta) y hace daño.
 * A los jugadores de tu mismo color no les hace nada.
 */
public class TintaProyectil extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(TintaProyectil.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TAM = SynchedEntityData.defineId(TintaProyectil.class, EntityDataSerializers.FLOAT);
    private float dano = 4.0F, radio = 1.2F;
    private int vida;

    public TintaProyectil(EntityType<? extends TintaProyectil> type, Level level) {
        super(type, level);
    }

    public TintaProyectil(Level level, LivingEntity quien, int color, float dano, float radio, float tam) {
        super(ModEntities.TINTA.get(), quien, level);
        entityData.set(COLOR, color);
        entityData.set(TAM, tam);
        this.dano = dano;
        this.radio = radio;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, ArmaTinta.COLOR_DEFECTO);
        entityData.define(TAM, 1.0F);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float tam() {
        return entityData.get(TAM);
    }

    @Override
    protected float getGravity() {
        return 0.035F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (++vida > 60) {
            discard();
            return;
        }
        // Gotas que caen del chorro y pintan el suelo por el camino
        if (vida % 3 == 0) {
            BlockHitResult abajo = sl.clip(new ClipContext(position(), position().add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (abajo.getType() == HitResult.Type.BLOCK) Tinta.salpicar(sl, abajo.getLocation(), 0.55, color());
        }
        int c = color();
        sl.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 0.9F),
                getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(hit.getEntity() instanceof LivingEntity le) || le == getOwner()) return;
        if (le instanceof Player p && ArmaTinta.colorJugador(p) == color()) return;
        le.hurt(damageSources().thrown(this, getOwner()), dano);
        le.invulnerableTime = Math.min(le.invulnerableTime, 4);   // los chorros seguidos pegan todos
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 donde = hit.getLocation();
        if (hit instanceof EntityHitResult eh) donde = new Vec3(eh.getEntity().getX(), eh.getEntity().getY() + 0.1, eh.getEntity().getZ());
        Tinta.salpicar(sl, donde, radio, color());
        sl.playSound(null, donde.x, donde.y, donde.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.5F, 1.4F + random.nextFloat() * 0.3F);
        int c = color();
        sl.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.4F),
                donde.x, donde.y + 0.1, donde.z, 10, 0.25, 0.15, 0.25, 0.05);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Color", color());
        t.putFloat("Dano", dano);
        t.putFloat("Radio", radio);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(COLOR, t.getInt("Color"));
        dano = t.getFloat("Dano");
        radio = t.getFloat("Radio");
    }
}
