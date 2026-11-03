package com.miscelanea.armas;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * El mazo lanzado: vuela girando, atraviesa y golpea a todo lo que pilla, y al chocar con un bloque (o a los 24
 * bloques) suelta una onda pequeña y vuelve solo a la mano, golpeando otra vez por el camino.
 */
public class MazoLanzado extends Projectile implements GeoEntity {
    private static final EntityDataAccessor<Boolean> VUELVE = SynchedEntityData.defineId(MazoLanzado.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> RUMBO = SynchedEntityData.defineId(MazoLanzado.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mazo_lanzado.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<Integer> golpeados = new HashSet<>();
    private Vec3 salida;
    private int vida, vuelta;
    private boolean truenoUsado;

    public MazoLanzado(EntityType<? extends MazoLanzado> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    public MazoLanzado(Level l, Player p) {
        this(ModEntities.MAZO_LANZADO.get(), l);
        setOwner(p);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VUELVE, false);
        entityData.define(RUMBO, 0F);
    }

    public boolean vuelve() {
        return entityData.get(VUELVE);
    }

    public float rumbo() {
        return entityData.get(RUMBO);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        boolean servidor = !level().isClientSide;
        Entity duenyo = getOwner();
        if (servidor && (!(duenyo instanceof Player p) || !p.isAlive() || p.level() != level())) {
            discard();
            return;
        }
        if (salida == null) salida = position();
        Vec3 v = getDeltaMovement();
        if (!vuelve()) {
            if (servidor) {
                BlockHitResult b = level().clip(new ClipContext(position(), position().add(v), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (b.getType() != HitResult.Type.MISS) {
                    setPos(b.getLocation().x, b.getLocation().y, b.getLocation().z);
                    impacto(b.getLocation(), true);
                    empezarVuelta();
                    return;
                }
                golpear(v, 13F, 1.3);
                if (position().distanceTo(salida) > 24) empezarVuelta();
                entityData.set(RUMBO, (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
            }
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            setDeltaMovement(v.add(0, -0.02, 0));
        } else if (duenyo != null) {
            vuelta++;
            Vec3 mano = duenyo.position().add(0, duenyo.getBbHeight() * 0.65, 0);
            Vec3 d = mano.subtract(position());
            double dist = d.length();
            if (servidor && dist < 1.4) {
                if (duenyo instanceof Player p) {
                    MazoItem.devolver(p);
                    level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.MAZO_VUELVE.get(), SoundSource.PLAYERS, 1F, 1F);
                }
                discard();
                return;
            }
            v = d.normalize().scale(Math.min(Math.min(2.4, 0.6 + vuelta * 0.12), dist));
            if (servidor) {
                golpear(v, 7F, 0.8);
                entityData.set(RUMBO, (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
            }
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            setDeltaMovement(v);
        }
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 0, 0, 0);
            if (vida % 3 == 0) level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 0, 0, 0);
        }
    }

    private void empezarVuelta() {
        entityData.set(VUELVE, true);
        golpeados.clear();
        vuelta = 0;
    }

    private void golpear(Vec3 v, float dano, double empuje) {
        if (!(getOwner() instanceof Player p)) return;
        for (Entity x : level().getEntities(this, getBoundingBox().expandTowards(v).inflate(0.6))) {
            if (!(x instanceof LivingEntity le) || x == p || !le.isAlive() || !golpeados.add(x.getId())) continue;
            le.invulnerableTime = 0;
            if (le.hurt(damageSources().thrown(this, p), dano * com.miscelanea.ConfigServidor.mazoMult())) {
                Vec3 h = v.lengthSqr() < 1e-6 ? Vec3.ZERO : v.normalize();
                le.setDeltaMovement(le.getDeltaMovement().add(h.scale(empuje)).add(0, 0.35, 0));
                le.hurtMarked = true;
                ItemStack s = MazoItem.enMano(p);
                if (s != null) MazoItem.sumarTrueno(p, s);
                ((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + 1, le.getZ(), 15, 0.3, 0.4, 0.3, 0.2);
                level().playSound(null, le.getX(), le.getY(), le.getZ(), ModSounds.MAZO_GOLPE.get(), SoundSource.PLAYERS, 1F, 1.2F);
                if (!vuelve()) impacto(le.position(), false);
            }
        }
    }

    /** Onda al chocar contra un bloque; con el trueno cargado, cae un rayo. */
    private void impacto(Vec3 c, boolean bloque) {
        if (!(level() instanceof ServerLevel sl) || !(getOwner() instanceof Player p)) return;
        if (bloque) {
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sl.playSound(null, c.x, c.y, c.z, ModSounds.MAZO_GOLPE.get(), SoundSource.PLAYERS, 1.3F, 0.8F);
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5), x -> x != p && x.isAlive())) {
                Vec3 d = o.position().subtract(c);
                Vec3 emp = d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize();
                o.setDeltaMovement(o.getDeltaMovement().add(emp.scale(0.7)).add(0, 0.4, 0));
                o.hurtMarked = true;
            }
        }
        ItemStack s = MazoItem.enMano(p);
        if (!truenoUsado && s != null && MazoItem.trueno(s) >= MazoItem.MAX_TRUENO) {
            truenoUsado = true;
            s.getOrCreateTag().putInt(MazoItem.TRUENO, 0);
            MazoEventos.rayo(sl, c, p);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mazo", 0, s -> s.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
