package com.miscelanea.armasfuego.pistola;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Bengala del revólver: sale disparada, frena, sube despacio y se queda brillando en el cielo un rato (30 s; 60 s con
 * Señalero) mientras baja poco a poco como si llevara paracaídas. Ilumina de verdad con bloques de luz temporales
 * (uno donde está y otro en el suelo debajo, ver {@link LucesTemporales}) y se ve de lejos (partículas siempre
 * visibles en el cliente y un brillo que no se hace más pequeño con la distancia). Con Señalero hace brillar
 * (efecto Brillo) a los que ilumina.
 */
public class BengalaEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> SENALERO = SynchedEntityData.defineId(BengalaEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int DURACION = 30 * 20;
    private int vida, duracion = DURACION;

    public BengalaEntity(EntityType<? extends BengalaEntity> t, Level l) {
        super(t, l);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SENALERO, false);
    }

    public void preparar(boolean senalero) {
        entityData.set(SENALERO, senalero);
        duracion = senalero ? DURACION * 2 : DURACION;
    }

    public boolean senalero() {
        return entityData.get(SENALERO);
    }

    public int vida() {
        return vida;
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Level l = level();
        Vec3 v = getDeltaMovement();
        if (!l.isClientSide && vida < 30) {
            HitResult hr = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hr instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
                le.setSecondsOnFire(4);
                le.hurt(damageSources().onFire(), 2F);
                v = v.scale(0.1);
            }
        }
        // primero frena; luego flota hacia arriba despacio, se para y al final baja con su "paracaídas"
        double sube = vida < 200 ? 0.0045 * (1 - vida / 200.0) : vida > 300 ? -0.0012 : 0;
        v = new Vec3(v.x * 0.88, v.y * 0.88 + sube, v.z * 0.88);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (horizontalCollision) setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (isInWaterOrBubble()) {
            if (!l.isClientSide) {
                l.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6F, 1.4F);
                discard();
            }
            return;
        }
        if (l.isClientSide) {
            particulasCliente();
            return;
        }
        ServerLevel sl = (ServerLevel) l;
        if (vida >= duracion) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            discard();
            return;
        }
        if (vida % 40 == 1) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, 1.4F, 1.6F);
        if (vida % 4 == 0) iluminar(sl);
    }

    private void iluminar(ServerLevel l) {
        // se apaga poco a poco al final
        int nivel = vida > duracion - 60 ? Math.max(6, 15 - (vida - (duracion - 60)) / 6) : 15;
        BlockPos aqui = blockPosition();
        LucesTemporales.poner(l, aqui, nivel, 12);
        // el suelo debajo (hasta 40 bloques)
        int suelo = l.getHeight(Heightmap.Types.MOTION_BLOCKING, aqui.getX(), aqui.getZ());
        if (suelo <= aqui.getY() && aqui.getY() - suelo < 40) {
            BlockPos abajo = new BlockPos(aqui.getX(), suelo, aqui.getZ());
            if (!abajo.equals(aqui)) LucesTemporales.poner(l, abajo, nivel, 12);
            if (senalero() && vida % 20 == 0) brillar(l, Vec3.atCenterOf(abajo), aqui);
        } else if (senalero() && vida % 20 == 0) {
            brillar(l, position(), aqui);
        }
    }

    /** Señalero: los que están en la luz brillan (se ven a través de las paredes). */
    private void brillar(ServerLevel l, Vec3 suelo, BlockPos aqui) {
        AABB caja = new AABB(suelo, suelo).inflate(12, 4, 12).minmax(new AABB(aqui).inflate(8));
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, caja, e -> e.isAlive() && !e.isSpectator()))
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
    }

    private void particulasCliente() {
        Level l = level();
        var r = random;
        if (vida % 2 == 0)
            l.addAlwaysVisibleParticle(new DustParticleOptions(new Vector3f(1.0F, 0.25F + r.nextFloat() * 0.2F, 0.12F), 2.2F),
                    true, getX(), getY(), getZ(), 0, 0, 0);
        if (vida % 3 == 0)
            l.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, getX() + (r.nextFloat() - 0.5) * 0.1, getY() - 0.1, getZ(), 0, -0.02, 0);
        if (r.nextInt(6) == 0)
            l.addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != getOwner();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    /** Lo que brilla en el cielo: más grande al principio y apagándose al final. */
    public float brillo(float pt) {
        float t = vida + pt;
        float fin = Mth.clamp((duracion() - t) / 60F, 0F, 1F);
        return Math.min(1F, t / 6F) * fin;
    }

    public int duracion() {
        return senalero() ? DURACION * 2 : DURACION;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Vida", vida);
        t.putBoolean("Senalero", senalero());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        vida = t.getInt("Vida");
        preparar(t.getBoolean("Senalero"));
    }
}
