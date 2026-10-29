package com.miscelanea.armas;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Cohete del lanzacohetes: vuela recto (sin caída) y explota al chocar. Daño en área que baja hacia el borde;
 * no rompe bloques. Al que lo dispara le hace poco daño y lo lanza por los aires (rocket jump).
 */
public class CoheteEntity extends Projectile implements GeoEntity {
    private static final double RADIO = 3.5;
    private static final float DANO = 14.0F;
    private static final RawAnimation VUELO = RawAnimation.begin().thenLoop("animation.cohete.vuelo");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int vida;
    public float rumboY, rumboX, rumboYO, rumboXO;

    public CoheteEntity(EntityType<? extends CoheteEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public CoheteEntity(Level level, Player duenyo) {
        this(ModEntities.COHETE.get(), level);
        setOwner(duenyo);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != getOwner();
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        rumboYO = rumboY;
        rumboXO = rumboX;
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
            if (vida > 200 || isInWater()) {
                explotar(position(), null);
                return;
            }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (v.lengthSqr() > 1e-6) {
            rumboY = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
            rumboX = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
            if (vida <= 1) {
                rumboYO = rumboY;
                rumboXO = rumboX;
            }
        }
        setYRot(0);
        setXRot(0);
        yRotO = 0;
        xRotO = 0;
        if (level().isClientSide) {
            // estela: humo detrás y fuego en la tobera
            Vec3 atras = v.lengthSqr() > 1e-6 ? v.normalize().scale(-0.45) : Vec3.ZERO;
            double y = getY() + 0.125;
            level().addParticle(ParticleTypes.SMOKE, getX() + atras.x, y + atras.y, getZ() + atras.z, 0, 0.01, 0);
            if (vida % 2 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + atras.x * 2, y + atras.y * 2, getZ() + atras.z * 2,
                    (random.nextDouble() - 0.5) * 0.02, 0.02, (random.nextDouble() - 0.5) * 0.02);
            level().addParticle(ParticleTypes.FLAME, getX() + atras.x * 0.8, y + atras.y * 0.8, getZ() + atras.z * 0.8, atras.x * 0.05, atras.y * 0.05, atras.z * 0.05);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        explotar(r.getLocation(), r.getEntity());
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        Vec3 n = Vec3.atLowerCornerOf(r.getDirection().getNormal()).scale(0.15);
        explotar(r.getLocation().add(n), null);
    }

    private void explotar(Vec3 c, Entity directo) {
        if (level().isClientSide || isRemoved() || !(level() instanceof ServerLevel sl)) return;
        Entity duenyo = getOwner();
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.4, 0.4, 0.4, 0);
        sl.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.15);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 20, 0.6, 0.6, 0.6, 0.05);
        sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0);
        sl.playSound(null, c.x, c.y, c.z, ModSounds.COHETE_EXPLOSION.get(), SoundSource.PLAYERS, 2.2F, 0.9F + random.nextFloat() * 0.2F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RADIO + 1), x -> x.isAlive() && !x.isSpectator())) {
            Vec3 centro = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = Math.max(0, centro.distanceTo(c) - e.getBbWidth() * 0.5);
            if (d > RADIO) continue;
            boolean propio = e == duenyo;
            // a través de paredes no (salvo pegado a la explosión)
            if (!propio && d > 1.0 && sl.clip(new ClipContext(c, centro, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS)
                continue;
            float f = (float) (1.0 - 0.5 * d / RADIO);
            float dmg = (e == directo ? DANO : DANO * f) * (propio ? 0.35F : 1.0F);
            e.hurt(damageSources().explosion(this, duenyo), dmg);
            Vec3 emp = centro.subtract(c);
            emp = emp.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : emp.normalize();
            double fuerza = propio ? 1.25 * f : 0.9 * f;
            e.setDeltaMovement(e.getDeltaMovement().add(emp.scale(fuerza)).add(0, (propio ? 0.15 : 0.2) * f, 0));
            e.hurtMarked = true;
            if (propio) e.resetFallDistance();
        }
        discard();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "vuelo", 0, s -> s.setAndContinue(VUELO)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
