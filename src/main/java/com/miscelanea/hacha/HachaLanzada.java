package com.miscelanea.hacha;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.Blocks;
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

import java.util.HashSet;
import java.util.Set;

/**
 * El Hacha Leviatán lanzada. Ligero: gira en horizontal y rebota en el enemigo. Pesado: gira en vertical, se clava y lo congela.
 * Se queda clavada (en un bloque o en un mob) hasta que la llamas: entonces vuelve girando a la mano y golpea lo que pilla.
 */
public class HachaLanzada extends Projectile implements GeoEntity {
    public static final byte VOLANDO = 0, CLAVADA = 1, VOLVIENDO = 2;
    private static final EntityDataAccessor<Byte> ESTADO = SynchedEntityData.defineId(HachaLanzada.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> PESADO = SynchedEntityData.defineId(HachaLanzada.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CLAVADA_EN = SynchedEntityData.defineId(HachaLanzada.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RUMBO_Y = SynchedEntityData.defineId(HachaLanzada.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RUMBO_X = SynchedEntityData.defineId(HachaLanzada.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.hacha_lanzada.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<Integer> golpeados = new HashSet<>();
    private boolean helada, rebotada;
    private int vida, vuelta;
    private Vec3 lado = Vec3.ZERO;

    public HachaLanzada(EntityType<? extends HachaLanzada> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public HachaLanzada(Level level, Player duenyo, boolean pesado, boolean helada) {
        this(ModEntities.HACHA_LANZADA.get(), level);
        setOwner(duenyo);
        entityData.set(PESADO, pesado);
        this.helada = helada;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ESTADO, VOLANDO);
        entityData.define(PESADO, false);
        entityData.define(CLAVADA_EN, -1);
        entityData.define(RUMBO_Y, 0F);
        entityData.define(RUMBO_X, 0F);
    }

    public byte estado() {
        return entityData.get(ESTADO);
    }

    public boolean pesado() {
        return entityData.get(PESADO);
    }

    public float rumboY() {
        return entityData.get(RUMBO_Y);
    }

    public float rumboX() {
        return entityData.get(RUMBO_X);
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
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e instanceof LivingEntity && e != getOwner();
    }

    /** Llamada: empieza a volver a la mano. */
    public void volver() {
        if (estado() == VOLVIENDO) return;
        entityData.set(ESTADO, VOLVIENDO);
        entityData.set(CLAVADA_EN, -1);
        golpeados.clear();
        vuelta = 0;
        Entity o = getOwner();
        if (o != null) {
            Vec3 d = o.position().subtract(position());
            Vec3 l = d.cross(new Vec3(0, 1, 0));
            lado = l.lengthSqr() < 1e-4 ? Vec3.ZERO : l.normalize().scale(0.6);   // vuelve haciendo una curva
        }
    }

    private void apuntar(Vec3 v) {
        if (v.lengthSqr() < 1e-6) return;
        entityData.set(RUMBO_Y, (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        entityData.set(RUMBO_X, (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Entity duenyo = getOwner();
        boolean servidor = !level().isClientSide;
        if (servidor && (!(duenyo instanceof ServerPlayer p) || !p.isAlive() || p.level() != level())) {
            discard();
            return;
        }
        byte e = estado();
        if (e == VOLANDO) {
            Vec3 v = getDeltaMovement();
            if (servidor) {
                HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
                if (hit.getType() != HitResult.Type.MISS) {
                    onHit(hit);
                    if (estado() != VOLANDO) return;
                    v = getDeltaMovement();
                }
                apuntar(v);
            }
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            setDeltaMovement(v.scale(0.99).add(0, rebotada ? -0.06 : -0.025, 0));
            if (servidor && vida > 300) volver();
        } else if (e == CLAVADA) {
            setDeltaMovement(Vec3.ZERO);
            int en = entityData.get(CLAVADA_EN);
            if (en >= 0) {
                Entity t = level().getEntity(en);
                if (t == null || !t.isAlive()) {
                    if (servidor) {
                        entityData.set(CLAVADA_EN, -1);
                        entityData.set(ESTADO, VOLANDO);
                        rebotada = true;
                    }
                } else {
                    setPos(t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ());
                    if (servidor && t instanceof LivingEntity le && vida % 10 == 0) Leviatan.helar(le, 20, 6);
                }
            }
        } else {
            ServerPlayer p = duenyo instanceof ServerPlayer sp ? sp : null;
            if (duenyo != null) {
                vuelta++;
                Vec3 mano = duenyo.position().add(0, duenyo.getBbHeight() * 0.7, 0);
                Vec3 d = mano.subtract(position());
                double dist = d.length();
                if (servidor && dist < 1.3) {
                    discard();
                    if (p != null) Leviatan.atrapar(p);
                    return;
                }
                double vel = Math.min(2.6, 0.7 + vuelta * 0.12);
                Vec3 curva = lado.scale(Math.max(0, 1 - vuelta / 12.0));
                Vec3 v = d.normalize().add(curva).normalize().scale(Math.min(vel, dist));
                if (servidor) {
                    apuntar(v);
                    for (Entity x : level().getEntities(this, getBoundingBox().expandTowards(v).inflate(0.7))) {
                        if (!(x instanceof LivingEntity le) || x == duenyo || !le.isAlive() || !golpeados.add(x.getId())) continue;
                        le.invulnerableTime = 0;
                        if (p != null && le.hurt(damageSources().playerAttack(p), 7.0F)) {
                            le.knockback(0.5, -v.x, -v.z);
                            Leviatan.helar(le, 40, 1);
                            ((ServerLevel) level()).sendParticles(ParticleTypes.SNOWFLAKE, le.getX(), le.getY() + 1, le.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
                            level().playSound(null, le.getX(), le.getY(), le.getZ(), ModSounds.HACHA_IMPACTO.get(), SoundSource.PLAYERS, 1F, 1.1F);
                        }
                    }
                }
                setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
                setDeltaMovement(v);
            }
        }
        if (level().isClientSide && (e != CLAVADA) && vida % 2 == 0)
            level().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.2, getZ(), 0, 0, 0);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (!(r.getEntity() instanceof LivingEntity v) || !(getOwner() instanceof ServerPlayer p)) return;
        v.invulnerableTime = 0;
        float dano = (pesado() ? 10F : 8F) + (helada ? 3F : 0F);
        v.hurt(damageSources().playerAttack(p), dano);
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 14, 0.3, 0.3, 0.3, 0.15);
        level().playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.HACHA_IMPACTO.get(), SoundSource.PLAYERS, 1.1F, 0.9F);
        if (pesado()) {
            // se clava y congela
            Leviatan.helar(v, 100, 6);
            entityData.set(ESTADO, CLAVADA);
            entityData.set(CLAVADA_EN, v.getId());
            setDeltaMovement(Vec3.ZERO);
        } else {
            // rebota hacia atrás y cae
            v.knockback(0.5, -getDeltaMovement().x, -getDeltaMovement().z);
            Leviatan.helar(v, 40, 1);
            setDeltaMovement(getDeltaMovement().scale(-0.25).add(0, 0.25, 0));
            rebotada = true;
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        if (level().isClientSide) return;
        Vec3 l = r.getLocation();
        setPos(l.x, l.y, l.z);
        apuntar(getDeltaMovement());
        setDeltaMovement(Vec3.ZERO);
        entityData.set(ESTADO, CLAVADA);
        level().playSound(null, l.x, l.y, l.z, ModSounds.HACHA_IMPACTO.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
        ((ServerLevel) level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(r.getBlockPos())), l.x, l.y, l.z, 10, 0.1, 0.1, 0.1, 0.1);
    }

    @Override
    public void remove(RemovalReason motivo) {
        if (!level().isClientSide && getOwner() instanceof ServerPlayer p) {
            Leviatan.olvidar(p, this);
            if (motivo != RemovalReason.DISCARDED) Leviatan.atrapar(p);   // descargada / el dueño se fue: vuelve sola
        }
        super.remove(motivo);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "hacha", 0, s -> s.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
