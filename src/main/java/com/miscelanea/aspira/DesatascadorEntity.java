package com.miscelanea.aspira;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ventosa del desatascador, atada a la boquilla con una cuerda.
 * - Volando o sin aspirar: la cuerda va floja.
 * - Aspirando: la cuerda se tensa más cuanto más cerca está la víctima.
 * - Al llegar a la boquilla queda AGARRADA mientras se mantenga el clic, y va cargando fuerza de lanzamiento.
 */
public class DesatascadorEntity extends ThrowableItemProjectile implements GeoEntity {
    private static final EntityDataAccessor<Integer> PEGADO_A =
            SynchedEntityData.defineId(DesatascadorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EN_BLOQUE =
            SynchedEntityData.defineId(DesatascadorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TENSION =
            SynchedEntityData.defineId(DesatascadorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> AGARRADO =
            SynchedEntityData.defineId(DesatascadorEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation BOING = RawAnimation.begin().thenLoop("animation.desatascador.boing");
    private static final RawAnimation VUELO = RawAnimation.begin().thenLoop("animation.desatascador.vuelo");

    public static final double LONGITUD_CUERDA = 24.0;
    /** Tiempo que aguanta pegada si nadie tira de ella. */
    private static final int VIDA_SUELTA = 15 * 20;
    private static final int VIDA_BLOQUE = 10 * 20;
    /** Ticks agarrada para llegar a la fuerza máxima de lanzamiento. */
    public static final int CARGA_MAX = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int vida;
    private int ultimoTiron = -100;
    private int ticksAgarrado;

    public DesatascadorEntity(EntityType<? extends DesatascadorEntity> type, Level level) {
        super(type, level);
    }

    public DesatascadorEntity(Level level, LivingEntity owner) {
        super(ModEntities.DESATASCADOR.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PEGADO_A, -1);
        this.entityData.define(EN_BLOQUE, false);
        this.entityData.define(TENSION, 0.0F);
        this.entityData.define(AGARRADO, false);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DESATASCADOR.get();
    }

    @Override
    protected float getGravity() {
        return 0.02F;
    }

    // ------------------------------------------------------------ estado

    @Nullable
    public LivingEntity pegadoA() {
        int id = this.entityData.get(PEGADO_A);
        if (id < 0) return null;
        Entity e = this.level().getEntity(id);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    public boolean enBloque() {
        return this.entityData.get(EN_BLOQUE);
    }

    /** 0 = cuerda floja, 1 = totalmente recta. */
    public float tension() {
        return this.entityData.get(TENSION);
    }

    public boolean agarrado() {
        return this.entityData.get(AGARRADO);
    }

    /** 0–1: cuánta fuerza extra lleva acumulada el lanzamiento. */
    public float carga() {
        return Mth.clamp(ticksAgarrado / (float) CARGA_MAX, 0.0F, 1.0F);
    }

    /** Lo llama la boquilla cada tick que está tirando de la cuerda. */
    public void tirar(float tension, boolean agarrado) {
        this.ultimoTiron = this.tickCount;
        this.vida = 0;
        this.entityData.set(TENSION, Mth.clamp(tension, 0.0F, 1.0F));
        if (agarrado && !agarrado()) {
            this.ticksAgarrado = 0;
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        if (agarrado) this.ticksAgarrado++;
        this.entityData.set(AGARRADO, agarrado);
    }

    // ------------------------------------------------------------ tick

    @Override
    public void tick() {
        Entity owner = getOwner();
        if (!level().isClientSide) {
            if (owner == null || !owner.isAlive() || owner.level() != level()
                    || owner.distanceToSqr(this) > LONGITUD_CUERDA * LONGITUD_CUERDA) {
                discard();
                return;
            }
            // Si nadie tira, la cuerda se afloja y se suelta el agarre
            if (this.tickCount - this.ultimoTiron > 2) {
                float t = tension();
                if (t > 0.0F) this.entityData.set(TENSION, Math.max(0.0F, t - 0.08F));
                if (agarrado()) this.entityData.set(AGARRADO, false);
                this.ticksAgarrado = 0;
            }
        }

        if (this.entityData.get(PEGADO_A) >= 0) {
            LivingEntity target = pegadoA();
            if (target == null) {
                if (!level().isClientSide) discard();
                return;
            }
            Vec3 p = target.position().add(0, target.getBbHeight() * 0.6, 0);
            setPos(p.x, p.y, p.z);
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && ++vida > VIDA_SUELTA) discard();
            return;
        }

        if (enBloque()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && ++vida > VIDA_BLOQUE) discard();
            return;
        }

        super.tick();
        if (!level().isClientSide && this.tickCount > 60) discard(); // no ha dado a nada
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity target = result.getEntity();
        if (target == getOwner() || !(target instanceof LivingEntity)) return;
        this.entityData.set(PEGADO_A, target.getId());
        this.vida = 0;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide) return;
        Vec3 p = result.getLocation();
        setPos(p.x, p.y, p.z);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        this.entityData.set(EN_BLOQUE, true);
        this.vida = 0;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.9F);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard(); // no sobrevive a un reinicio del mundo
    }

    // ------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ventosa", 3, state -> {
            if (pegadoA() != null || enBloque()) return state.setAndContinue(BOING);
            return state.setAndContinue(VUELO);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
