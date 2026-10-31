package com.miscelanea.bichos;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Bicho de la Bichopedia. Los voladores revolotean alrededor de donde salieron (y huyen si llegas corriendo);
 * los de suelo caminan a ratos y el saltamontes da saltos. El escorpión pica si te acercas sin agacharte.
 */
public class BichoEntity extends Mob {
    private static final EntityDataAccessor<Byte> ESPECIE = SynchedEntityData.defineId(BichoEntity.class, EntityDataSerializers.BYTE);
    private Vec3 casa, objetivo;
    private int pausa, vida, picadura;

    public BichoEntity(EntityType<? extends BichoEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static BichoEntity crear(Level level, Especie e, double x, double y, double z) {
        BichoEntity b = new BichoEntity(ModEntities.BICHO.get(), level);
        b.entityData.set(ESPECIE, (byte) e.ordinal());
        b.setPos(x, y, z);
        b.casa = new Vec3(x, y, z);
        b.setNoGravity(e.vuela());
        b.setYRot(level.random.nextFloat() * 360);
        return b;
    }

    public static AttributeSupplier.Builder atributos() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ESPECIE, (byte) 0);
    }

    public Especie especie() {
        int i = entityData.get(ESPECIE);
        Especie[] v = Especie.values();
        return i >= 0 && i < v.length ? v[i] : Especie.MARIPOSA_COL;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putByte("Especie", entityData.get(ESPECIE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(ESPECIE, t.getByte("Especie"));
        setNoGravity(especie().vuela());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity e) {}

    @Override
    public boolean causeFallDamage(float d, float m, DamageSource s) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return true;
    }

    @Override
    protected void serverAiStep() {
        Especie e = especie();
        vida++;
        if (casa == null) casa = position();
        if (isInWater() || vida > 20 * 60 * 4) {
            discard();
            return;
        }
        Player cerca = level().getNearestPlayer(this, 4.0);
        boolean asustado = cerca != null && !cerca.isShiftKeyDown()
                && (cerca.isSprinting() || cerca.getDeltaMovement().horizontalDistanceSqr() > 0.06) && cerca.distanceTo(this) < 3.0;
        if (e.vuela()) volar(e, cerca, asustado);
        else andar(e, cerca, asustado);
        // el escorpión pica
        if (e == Especie.ESCORPION && picadura-- <= 0 && cerca != null && cerca.distanceTo(this) < 1.4 && !cerca.isShiftKeyDown()
                && !cerca.getAbilities().instabuild) {
            cerca.hurt(damageSources().mobAttack(this), 1.0F);
            cerca.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            picadura = 40;
        }
    }

    private void volar(Especie e, Player cerca, boolean asustado) {
        if (asustado) {
            Vec3 lejos = position().subtract(cerca.position()).normalize();
            objetivo = position().add(lejos.x * 5, 2.5, lejos.z * 5);
        }
        if (objetivo == null || objetivo.distanceToSqr(position()) < 0.3 || random.nextInt(80) == 0) {
            BlockPos suelo = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(casa.x + (random.nextDouble() - 0.5) * 9, 0, casa.z + (random.nextDouble() - 0.5) * 9));
            objetivo = new Vec3(suelo.getX() + 0.5, suelo.getY() + 0.8 + random.nextDouble() * (e == Especie.LUCIERNAGA ? 2.2 : 2.8), suelo.getZ() + 0.5);
        }
        double vel = switch (e.tipo) {
            case ZUMBA -> asustado ? 0.28 : 0.11;
            case BRILLA -> 0.045;
            default -> asustado ? 0.22 : 0.075;
        };
        Vec3 d = objetivo.subtract(position());
        Vec3 v = d.normalize().scale(vel);
        // aleteo irregular (mariposas) / zumbido con paradas (libélula, abejorro)
        double t = tickCount * 0.35;
        if (e.tipo == Especie.Tipo.VUELA) v = v.add(Math.sin(t * 1.7) * 0.04, Math.sin(t * 2.3) * 0.05, Math.cos(t * 1.3) * 0.04);
        if (e.tipo == Especie.Tipo.ZUMBA && (tickCount / 20) % 3 == 0 && !asustado) v = v.scale(0.1);
        setDeltaMovement(getDeltaMovement().scale(0.6).add(v.scale(0.4)));
        if (getDeltaMovement().horizontalDistanceSqr() > 1e-5) {
            float yaw = (float) (Mth.atan2(getDeltaMovement().z, getDeltaMovement().x) * Mth.RAD_TO_DEG) - 90F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12F));
            yBodyRot = getYRot();
        }
    }

    private void andar(Especie e, Player cerca, boolean asustado) {
        if (asustado && onGround()) {
            Vec3 lejos = position().subtract(cerca.position()).normalize();
            setYRot((float) (Mth.atan2(lejos.z, lejos.x) * Mth.RAD_TO_DEG) - 90F);
            if (e.tipo == Especie.Tipo.SALTA) setDeltaMovement(lejos.x * 0.35, 0.42, lejos.z * 0.35);
            else setDeltaMovement(lejos.x * 0.15, getDeltaMovement().y, lejos.z * 0.15);
            pausa = 10;
            return;
        }
        if (pausa > 0) {
            pausa--;
            if (pausa > 20) {   // caminando
                Vec3 f = Vec3.directionFromRotation(0, getYRot()).scale(e == Especie.ESCARABAJO_RINOCERONTE ? 0.025 : 0.04);
                setDeltaMovement(f.x, getDeltaMovement().y, f.z);
            }
            return;
        }
        if (e.tipo == Especie.Tipo.SALTA && onGround() && random.nextInt(3) == 0) {
            Vec3 f = Vec3.directionFromRotation(0, getYRot() + random.nextInt(90) - 45);
            setDeltaMovement(f.x * 0.25, 0.38, f.z * 0.25);
        } else {
            setYRot(getYRot() + random.nextInt(120) - 60);
            // no se aleja demasiado de casa
            if (casa != null && casa.distanceToSqr(position()) > 25)
                setYRot((float) (Mth.atan2(casa.z - getZ(), casa.x - getX()) * Mth.RAD_TO_DEG) - 90F);
        }
        yBodyRot = getYRot();
        pausa = 30 + random.nextInt(60);
    }
}
