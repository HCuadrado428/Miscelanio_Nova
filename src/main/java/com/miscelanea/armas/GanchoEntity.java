package com.miscelanea.armas;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
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
 * Gancho de la superescopeta. Vuela rápido; si engancha un mob te arrastra hacia él, si engancha un bloque
 * te balanceas colgado (la cuerda se va recogiendo). Si no da a nada, se recoge solo.
 * La física del jugador la hace su propio cliente (es quien mueve al jugador en Minecraft).
 */
public class GanchoEntity extends Projectile implements GeoEntity {
    public static final byte VOLANDO = 0, MOB = 1, BLOQUE = 2;
    private static final EntityDataAccessor<Byte> ESTADO = SynchedEntityData.defineId(GanchoEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> OBJETIVO = SynchedEntityData.defineId(GanchoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LARGO = SynchedEntityData.defineId(GanchoEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation VUELO = RawAnimation.begin().thenLoop("animation.gancho.vuelo");
    private static final RawAnimation QUIETO = RawAnimation.begin().thenLoop("animation.gancho.enganchado");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int vida;
    private BlockPos ancla;
    /** Rumbo para dibujarlo (grados), calculado en cada lado. */
    public float rumboY, rumboX, rumboYO, rumboXO;

    public GanchoEntity(EntityType<? extends GanchoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public GanchoEntity(Level level, Player duenyo) {
        this(ModEntities.GANCHO.get(), level);
        setOwner(duenyo);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ESTADO, VOLANDO);
        entityData.define(OBJETIVO, -1);
        entityData.define(LARGO, 8.0F);
    }

    public byte estado() {
        return entityData.get(ESTADO);
    }

    public int objetivo() {
        return entityData.get(OBJETIVO);
    }

    public float largo() {
        return entityData.get(LARGO);
    }

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
        return super.canHitEntity(e) && e instanceof LivingEntity && e != getOwner();
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Entity duenyo = getOwner();
        boolean servidor = !level().isClientSide;
        if (servidor) {
            if (!(duenyo instanceof ServerPlayer p) || !p.isAlive() || p.level() != level()
                    || !(p.getMainHandItem().getItem() instanceof EscopetaItem) || distanceToSqr(p) > 48 * 48) {
                discard();
                return;
            }
            if (estado() != VOLANDO) p.resetFallDistance();
        }
        rumboYO = rumboY;
        rumboXO = rumboX;
        byte e = estado();
        if (e == VOLANDO) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && servidor) onHit(hit);
            if (estado() == VOLANDO) {
                Vec3 v = getDeltaMovement();
                setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
                apuntar(v);
            }
            if (servidor && vida > 12) {
                level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 0.6F, 1.4F);
                discard();
                return;
            }
        } else if (e == MOB) {
            Entity t = level().getEntity(objetivo());
            if (t == null || !t.isAlive()) {
                if (servidor) discard();
                return;
            }
            setPos(t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ());
            setDeltaMovement(Vec3.ZERO);
            if (servidor && duenyo != null && (duenyo.distanceTo(t) < 1.6 + t.getBbWidth() || vida > 100)) {
                discard();
                return;
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
            if (servidor) {
                entityData.set(LARGO, Math.max(2.5F, largo() - 0.1F));
                if ((ancla != null && level().getBlockState(ancla).isAir()) || vida > 400) {
                    discard();
                    return;
                }
            }
        }
        if (duenyo != null && e != VOLANDO) apuntar(position().subtract(duenyo.getEyePosition()));
        if (level().isClientSide && duenyo instanceof Player p && p.isLocalPlayer()) fisica(p);
        // la rotación "de verdad" se queda a 0: el renderizador usa el rumbo
        setYRot(0);
        setXRot(0);
        yRotO = 0;
        xRotO = 0;
    }

    private void apuntar(Vec3 v) {
        if (v.lengthSqr() < 1e-6) return;
        rumboY = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
        rumboX = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
    }

    /** Física del jugador enganchado (solo en su cliente). */
    private void fisica(Player p) {
        Vec3 punto = p.position().add(0, p.getBbHeight() * 0.6, 0);
        Vec3 hacia = position().subtract(punto);
        double d = hacia.length();
        if (d < 1e-3) return;
        Vec3 n = hacia.scale(1 / d);
        if (estado() == MOB) {
            Entity t = level().getEntity(objetivo());
            double lim = 1.6 + (t != null ? t.getBbWidth() : 0.6);
            if (d > lim) {
                Vec3 v = n.scale(1.35).add(0, 0.06, 0);
                p.setDeltaMovement(p.getDeltaMovement().scale(0.3).add(v.scale(0.7)));
            }
            p.resetFallDistance();
        } else if (estado() == BLOQUE) {
            double l = largo();
            Vec3 v = p.getDeltaMovement();
            if (d > l) {
                double alejarse = -v.dot(n);              // lo que se aleja del ancla: la cuerda lo frena
                if (alejarse > 0) v = v.add(n.scale(alejarse));
                v = v.add(n.scale(Math.min(0.5, (d - l) * 0.35)));
            }
            v = v.add(n.scale(0.03));
            p.setDeltaMovement(v);
            p.resetFallDistance();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (level().isClientSide) return;
        entityData.set(ESTADO, MOB);
        entityData.set(OBJETIVO, r.getEntity().getId());
        vida = 0;
        Entity duenyo = getOwner();
        if (duenyo instanceof Player p) r.getEntity().hurt(damageSources().playerAttack(p), 2.0F);
        level().playSound(null, getX(), getY(), getZ(), ModSounds.GANCHO_ENGANCHAR.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        if (level().isClientSide) return;
        entityData.set(ESTADO, BLOQUE);
        Vec3 l = r.getLocation();
        setPos(l.x, l.y, l.z);
        setDeltaMovement(Vec3.ZERO);
        ancla = r.getBlockPos();
        vida = 0;
        Entity duenyo = getOwner();
        if (duenyo != null) entityData.set(LARGO, (float) duenyo.position().add(0, duenyo.getBbHeight() * 0.6, 0).distanceTo(l));
        level().playSound(null, getX(), getY(), getZ(), ModSounds.GANCHO_ENGANCHAR.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @Override
    public void remove(RemovalReason motivo) {
        if (!level().isClientSide && getOwner() instanceof ServerPlayer p) Gancho.alQuitar(p);
        super.remove(motivo);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "gancho", 0, s -> s.setAndContinue(estado() == VOLANDO ? VUELO : QUIETO)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
