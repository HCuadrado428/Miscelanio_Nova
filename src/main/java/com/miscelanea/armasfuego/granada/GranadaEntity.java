package com.miscelanea.armasfuego.granada;

import com.miscelanea.ConfigServidor;
import com.miscelanea.armasfuego.comun.ArmasFuegoRegistro;
import com.miscelanea.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * La granada en el aire: rebota en los bloques (pierde fuerza en cada rebote), hace chisporrotear la mecha y a los 60
 * ticks estalla según su tipo. La de humo se queda 10 s en el sitio echando humo.
 */
public class GranadaEntity extends ThrowableItemProjectile {
    public static final int MECHA = 60, HUMO_DURA = 200;
    private int vida, humo = -1;

    public GranadaEntity(EntityType<? extends GranadaEntity> t, Level l) {
        super(t, l);
    }

    public GranadaEntity(EntityType<? extends GranadaEntity> t, LivingEntity tirador, Level l) {
        super(t, tirador, l);
    }

    @Override
    protected Item getDefaultItem() {
        return com.miscelanea.registry.ModItems.GRANADA_METRALLA.get();
    }

    public TipoGranada tipo() {
        return getItem().getItem() instanceof GranadaItem g ? g.tipo : TipoGranada.METRALLA;
    }

    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    public void tick() {
        if (humo >= 0) {
            // la nube de humo: quieta, echando humo y cegando a quien esté dentro
            setDeltaMovement(Vec3.ZERO);
            if (level() instanceof ServerLevel l) nube(l);
            if (++humo > HUMO_DURA) discard();
            return;
        }
        super.tick();
        vida++;
        if (level().isClientSide) {
            if (vida % 2 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.15, getZ(), 0, 0.02, 0);
            return;
        }
        if (vida % 10 == 0) level().playSound(null, getX(), getY(), getZ(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.25F, 1.8F);
        if (vida >= MECHA) estallar((ServerLevel) level());
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        // rebota: invierte la componente de la cara y pierde fuerza
        Vec3 v = getDeltaMovement();
        var n = r.getDirection().getNormal();
        double vx = n.getX() != 0 ? -v.x * 0.35 : v.x * 0.6, vy = n.getY() != 0 ? -v.y * 0.35 : v.y * 0.6, vz = n.getZ() != 0 ? -v.z * 0.35 : v.z * 0.6;
        setDeltaMovement(vx, vy, vz);
        if (v.lengthSqr() > 0.01)
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.COPPER_HIT, SoundSource.PLAYERS, 0.6F, 1.3F + random.nextFloat() * 0.3F);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        setDeltaMovement(getDeltaMovement().scale(-0.2));
    }

    @Override
    protected void onHit(net.minecraft.world.phys.HitResult r) {
        // sin super.onHit: no desaparece al chocar (vanilla la quitaría)
        if (r instanceof BlockHitResult b) onHitBlock(b);
        else if (r instanceof EntityHitResult e) onHitEntity(e);
    }

    private void estallar(ServerLevel l) {
        switch (tipo()) {
            case METRALLA -> {
                Level.ExplosionInteraction rompe = ConfigServidor.granadaRompe() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
                l.explode(this, getX(), getY(), getZ(), 2.5F, rompe);
                for (int i = 0; i < 16; i++) {
                    double a = random.nextDouble() * Math.PI * 2, s = 0.25 + random.nextDouble() * 0.25;
                    l.sendParticles(ArmasFuegoRegistro.CHISPA.get(), getX(), getY() + 0.2, getZ(), 0, Math.cos(a) * s, 0.15 + random.nextDouble() * 0.2, Math.sin(a) * s, 1.0);
                }
                logro(l);
                discard();
            }
            case MIEL -> {
                l.playSound(null, getX(), getY(), getZ(), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F);
                l.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
                l.sendParticles(ParticleTypes.LANDING_HONEY, getX(), getY() + 0.3, getZ(), 40, 1.6, 0.4, 1.6, 0);
                l.sendParticles(ParticleTypes.FALLING_HONEY, getX(), getY() + 0.8, getZ(), 30, 1.2, 0.6, 1.2, 0);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(3.5)))
                    e.addEffect(new MobEffectInstance(ModEffects.PRINGADO.get(), 160, 1), getOwner());
                logro(l);
                discard();
            }
            case HUMO -> {
                l.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
                logro(l);
                humo = 0;
                setNoGravity(true);
            }
        }
    }

    private void nube(ServerLevel l) {
        double r = Math.min(3.5, 1.0 + humo * 0.12);
        if (humo % 2 == 0)
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * r;
                l.sendParticles(ArmasFuegoRegistro.HUMO.get(), getX() + Math.cos(a) * d, getY() + 0.2 + random.nextDouble() * 1.8, getZ() + Math.sin(a) * d,
                        0, (random.nextDouble() - 0.5) * 0.04, 0.01, (random.nextDouble() - 0.5) * 0.04, 1.0);
            }
        if (humo % 10 == 0) l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 2, r * 0.5, 0.5, r * 0.5, 0.01);
        if (humo % 5 != 0) return;
        AABB caja = new AABB(blockPosition()).inflate(r, 2, r);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, caja)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            // dentro del humo no se ve a nadie: los mobs pierden el objetivo
            if (e instanceof Mob m) m.setTarget(null);
        }
        for (Mob m : l.getEntitiesOfClass(Mob.class, caja.inflate(16)))
            if (m.getTarget() != null && caja.contains(m.getTarget().position())) m.setTarget(null);
    }

    private void logro(ServerLevel l) {
        if (getOwner() instanceof ServerPlayer p) com.miscelanea.logros.LogrosMiscelanea.dar(p, "granada");
    }
}
