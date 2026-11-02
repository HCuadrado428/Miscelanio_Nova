package com.miscelanea.armas;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Esfera de plasma: vuela despacio y recta. Cada pocos ticks lanza rayos verdes a los seres vivos que tiene cerca
 * (no al que dispara) y al chocar estalla con mucho daño en área. No rompe bloques.
 */
public class PlasmaEntity extends Projectile {
    static final double ALCANCE_RAYOS = 9, RADIO = 6;
    static final float DANO_RAYO = 7, DANO = 60;
    static final DustParticleOptions VERDE = new DustParticleOptions(new Vector3f(0.35F, 1F, 0.3F), 2.4F);
    static final DustParticleOptions VERDE_P = new DustParticleOptions(new Vector3f(0.6F, 1F, 0.5F), 0.9F);
    private int vida;

    public PlasmaEntity(EntityType<? extends PlasmaEntity> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    public PlasmaEntity(Level l, Player p) {
        this(ModEntities.PLASMA.get(), l);
        setOwner(p);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != getOwner();
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Vec3 v = getDeltaMovement();
        if (level().isClientSide) {
            for (int i = 0; i < 6; i++)
                level().addParticle(VERDE, getX() + (random.nextDouble() - 0.5) * 0.7, getY() + (random.nextDouble() - 0.5) * 0.7, getZ() + (random.nextDouble() - 0.5) * 0.7, 0, 0, 0);
            level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05);
            level().addParticle(ParticleTypes.GLOW, getX(), getY(), getZ(), 0, 0, 0);
        } else {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
            if (vida > 240) {
                estallar(position());
                return;
            }
            if (vida % 5 == 0) rayos();
            if (vida % 20 == 1) level().playSound(null, getX(), getY(), getZ(), ModSounds.BFG_ZUMBIDO.get(), SoundSource.PLAYERS, 1.2F, 1F);
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    private void rayos() {
        if (!(level() instanceof ServerLevel sl)) return;
        Entity duenyo = getOwner();
        Vec3 c = position();
        int n = 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(ALCANCE_RAYOS), x -> x.isAlive() && x != duenyo && !x.isSpectator()
                && !(x instanceof Player pl && pl.isCreative()))) {
            Vec3 obj = e.position().add(0, e.getBbHeight() * 0.5, 0);
            if (obj.distanceTo(c) > ALCANCE_RAYOS) continue;
            if (sl.clip(new ClipContext(c, obj, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            e.hurt(damageSources().indirectMagic(this, duenyo), DANO_RAYO);
            Vec3 d = obj.subtract(c);
            int pasos = (int) (d.length() * 4);
            for (int i = 0; i <= pasos; i++) {
                Vec3 q = c.add(d.scale(i / (double) pasos));
                sl.sendParticles(VERDE_P, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0);
            }
            if (++n >= 6) break;
        }
        if (n > 0) sl.playSound(null, c.x, c.y, c.z, ModSounds.BFG_RAYO.get(), SoundSource.PLAYERS, 0.8F, 0.9F + random.nextFloat() * 0.3F);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        estallar(r.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        estallar(r.getLocation().add(Vec3.atLowerCornerOf(r.getDirection().getNormal()).scale(0.3)));
    }

    private void estallar(Vec3 c) {
        if (isRemoved() || !(level() instanceof ServerLevel sl)) return;
        Entity duenyo = getOwner();
        sl.sendParticles(VERDE, c.x, c.y, c.z, 160, 2.2, 2.2, 2.2, 0);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.3, 0.3, 0.3, 0.45);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.playSound(null, c.x, c.y, c.z, ModSounds.BFG_ESTALLIDO.get(), SoundSource.PLAYERS, 4F, 1F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RADIO + 1), x -> x.isAlive() && !x.isSpectator())) {
            Vec3 centro = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = centro.distanceTo(c);
            if (d > RADIO) continue;
            float f = (float) (1 - 0.6 * d / RADIO);
            e.hurt(damageSources().indirectMagic(this, duenyo), DANO * f * (e == duenyo ? 0.25F : 1F));
            Vec3 emp = centro.subtract(c);
            emp = emp.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : emp.normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(emp.scale(1.2 * f)).add(0, 0.3 * f, 0));
            e.hurtMarked = true;
        }
        discard();
    }
}
