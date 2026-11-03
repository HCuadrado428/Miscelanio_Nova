package com.miscelanea.tortura.efectos;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/** Dardo de cerbatana en vuelo: casi recto, deja un rastro verde; a la entidad viva que le da, 45 s de miedo (las dosis se acumulan: ver EfectosEventos). */
public class DardoMiedoEntity extends ThrowableItemProjectile {
    public static final int DURACION = 900;
    private static final DustParticleOptions RASTRO = new DustParticleOptions(new Vector3f(0.35F, 0.75F, 0.22F), 0.6F);

    public DardoMiedoEntity(EntityType<? extends DardoMiedoEntity> t, Level l) {
        super(t, l);
    }

    public DardoMiedoEntity(Level l, LivingEntity quien) {
        super(EfectosRegistro.DARDO_LANZADO.get(), quien, l);
    }

    @Override
    protected Item getDefaultItem() {
        return EfectosRegistro.DARDO_MIEDO.get();
    }

    @Override
    protected float getGravity() {
        return 0.008F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) level().addParticle(RASTRO, getX(), getY(), getZ(), 0, 0, 0);
        if (!level().isClientSide && tickCount > 120) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        super.onHitEntity(r);
        if (level().isClientSide || !(r.getEntity() instanceof LivingEntity t)) return;
        t.hurt(damageSources().thrown(this, getOwner()), 1.0F);
        t.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), DURACION), getOwner());
        level().playSound(null, t.getX(), t.getEyeY(), t.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (level().isClientSide || isRemoved()) return;
        if (r.getType() == HitResult.Type.BLOCK)
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.5F, 1.8F);
        level().broadcastEntityEvent(this, (byte) 3);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != 3) {
            super.handleEntityEvent(id);
            return;
        }
        for (int i = 0; i < 8; i++)
            level().addParticle(ParticleTypes.ENTITY_EFFECT, getX(), getY(), getZ(), 0.31, 0.54, 0.18);
    }
}
