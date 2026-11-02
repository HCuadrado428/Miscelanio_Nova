package com.miscelanea.armas;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Carga de viento del mazo: al chocar suelta una ráfaga que empuja todo lo de alrededor (también a quien la tira). */
public class VientoEntity extends Projectile {
    static final double RADIO = 2.6;
    private int vida;

    public VientoEntity(EntityType<? extends VientoEntity> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    public VientoEntity(Level l, Player p) {
        this(ModEntities.VIENTO.get(), l);
        setOwner(p);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && (e != getOwner() || vida > 4);
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Vec3 v = getDeltaMovement();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.CLOUD, getX(), getY(), getZ(), 0, 0, 0);
            if (vida % 2 == 0) level().addParticle(ParticleTypes.WHITE_ASH, getX(), getY(), getZ(), 0, 0, 0);
        } else {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
            if (vida > 60) {
                rafaga(position());
                return;
            }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        r.getEntity().hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null), 1F);
        rafaga(r.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        rafaga(r.getLocation().add(Vec3.atLowerCornerOf(r.getDirection().getNormal()).scale(0.2)));
    }

    private void rafaga(Vec3 c) {
        if (isRemoved() || !(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.15);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 4, 0.8, 0.4, 0.8, 0);
        sl.playSound(null, c.x, c.y, c.z, ModSounds.VIENTO_RAFAGA.get(), SoundSource.PLAYERS, 1.4F, 0.9F + random.nextFloat() * 0.2F);
        for (Entity e : sl.getEntities(this, new AABB(c, c).inflate(RADIO + 1), x -> x.isAlive() && !x.isSpectator() && (x.isPushable() || x instanceof Player))) {
            Vec3 centro = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = centro.distanceTo(c);
            if (d > RADIO) continue;
            float f = (float) (1 - d / RADIO * 0.5);
            Vec3 emp = centro.subtract(c);
            emp = emp.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : emp.normalize();
            boolean propio = e == getOwner();
            e.setDeltaMovement(e.getDeltaMovement().add(emp.scale(1.1 * f)).add(0, (propio ? 0.95 : 0.45) * f, 0));
            e.hurtMarked = true;
            if (e instanceof LivingEntity le) le.resetFallDistance();
        }
        discard();
    }
}
