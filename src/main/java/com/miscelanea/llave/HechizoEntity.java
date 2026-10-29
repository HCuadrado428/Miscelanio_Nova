package com.miscelanea.llave;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

/** Hechizos de la Llave Espada: Piro (bola de fuego que quema) y Hielo (esquirlas que congelan y ralentizan). */
public class HechizoEntity extends Projectile {
    public static final byte PIRO = 0, HIELO = 1;
    private static final EntityDataAccessor<Byte> TIPO = SynchedEntityData.defineId(HechizoEntity.class, EntityDataSerializers.BYTE);
    private int vida;

    public HechizoEntity(EntityType<? extends HechizoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public HechizoEntity(Level level, Player duenyo, byte tipo) {
        this(ModEntities.HECHIZO.get(), level);
        setOwner(duenyo);
        entityData.set(TIPO, tipo);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TIPO, PIRO);
    }

    public byte tipo() {
        return entityData.get(TIPO);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != getOwner();
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
            if (vida > 40) {
                discard();
                return;
            }
        } else {
            for (int i = 0; i < 3; i++) {
                double k = i / 3.0;
                double x = getX() - v.x * k, y = getY() + 0.15 - v.y * k, z = getZ() - v.z * k;
                if (tipo() == PIRO) {
                    level().addParticle(ParticleTypes.FLAME, x, y, z, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
                    if (i == 0) level().addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.02, 0);
                } else {
                    level().addParticle(ParticleTypes.SNOWFLAKE, x, y, z, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
                }
            }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (!(r.getEntity() instanceof LivingEntity v)) return;
        Entity duenyo = getOwner();
        v.invulnerableTime = 0;
        if (tipo() == PIRO) {
            v.hurt(damageSources().indirectMagic(this, duenyo), 7.0F);
            v.setSecondsOnFire(4);
        } else {
            v.hurt(damageSources().indirectMagic(this, duenyo), 5.0F);
            v.setTicksFrozen(Math.max(v.getTicksFrozen(), v.getTicksRequiredToFreeze() + 80));
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2));
        }
        if (duenyo instanceof net.minecraft.server.level.ServerPlayer sp) Magia.ganar(sp, 1F);
        estallar(r.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        estallar(r.getLocation());
    }

    private void estallar(Vec3 c) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (tipo() == PIRO) {
            sl.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 24, 0.25, 0.25, 0.25, 0.08);
            sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
        } else {
            sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 24, 0.3, 0.3, 0.3, 0.06);
            sl.sendParticles(ParticleTypes.ITEM_SNOWBALL, c.x, c.y, c.z, 10, 0.2, 0.2, 0.2, 0.1);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.6F);
        }
        discard();
    }
}
