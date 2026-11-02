package com.miscelanea.tortura;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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

/**
 * El lazo volando: un aro de cuerda (lo dibuja el cliente con la cuerda desde la mano del que lo lanza). Llega hasta
 * {@link #alcance} bloques desde donde salió; después pierde fuerza, cae y se recoge solo. Si toca a un jugador o mob
 * válido (ver {@link Lazo#valido}) lo enlaza. No se guarda con el mundo.
 */
public class LazoEntity extends Projectile {
    private static final EntityDataAccessor<Float> CARGA = SynchedEntityData.defineId(LazoEntity.class, EntityDataSerializers.FLOAT);
    private int vida, caida;
    private double alcance = 10;
    private Vec3 origen;
    private boolean cayendo;

    public LazoEntity(EntityType<? extends LazoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public LazoEntity(Level level, Player duenyo, float carga) {
        this(TorturaRegistro.LAZO_LANZADO.get(), level);
        setOwner(duenyo);
        entityData.set(CARGA, carga);
        alcance = Lazo.ALCANCE_MIN + (Lazo.ALCANCE_MAX - Lazo.ALCANCE_MIN) * carga;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(CARGA, 0.5F);
    }

    /** 0..1: lo que se cargó el lanzamiento (el aro es más grande cuanto más). */
    public float carga() {
        return entityData.get(CARGA);
    }

    public boolean cayendo() {
        return cayendo;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity o = getOwner();
        return super.canHitEntity(e) && e instanceof LivingEntity && e != o && (o == null || e.getVehicle() != o);
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Entity duenyo = getOwner();
        boolean servidor = !level().isClientSide;
        if (servidor && (!(duenyo instanceof ServerPlayer p) || !p.isAlive() || p.level() != level() || distanceToSqr(p) > 30 * 30 || vida > 80)) {
            discard();
            return;
        }
        if (origen == null) origen = position();
        Vec3 v = getDeltaMovement();
        if (!cayendo) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && servidor) {
                onHit(hit);
                if (isRemoved()) return;
                v = getDeltaMovement();
            }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (!cayendo && position().distanceTo(origen) > alcance) cayendo = true;
        // vuela casi recto (un poco de caída); sin fuerza, se frena y cae
        v = cayendo ? new Vec3(v.x * 0.55, v.y * 0.55 - 0.09, v.z * 0.55) : v.add(0, -0.018, 0);
        setDeltaMovement(v);
        if (cayendo && servidor && ++caida > 12) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (level().isClientSide) return;
        Entity t = r.getEntity();
        if (getOwner() instanceof ServerPlayer p && Lazo.valido(p, t)) {
            Lazo.enlazar(p, (LivingEntity) t);
            discard();
            return;
        }
        // no vale (jefe, ya enlazado...): rebota y cae
        cayendo = true;
        setDeltaMovement(getDeltaMovement().scale(-0.2).add(0, 0.1, 0));
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        if (level().isClientSide) return;
        Vec3 l = r.getLocation();
        setPos(l.x, l.y, l.z);
        cayendo = true;
        setDeltaMovement(Vec3.ZERO);
        level().playSound(null, l.x, l.y, l.z, TorturaRegistro.LAZO_ATRAPAR.get(), SoundSource.PLAYERS, 0.35F, 1.5F);
    }

    @Override
    public void remove(RemovalReason motivo) {
        if (!level().isClientSide && getOwner() instanceof ServerPlayer p) Lazo.alQuitarLazo(p, this);
        super.remove(motivo);
    }
}
