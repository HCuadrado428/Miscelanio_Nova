package com.miscelanea.purgatorio;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Habitante del Purgatorio. No ve nada: se guía por el oído. Si hablas por el chat de voz te oye desde lejos, si corres
 * desde menos, si andas solo de cerca y agachado no te oye. Cuando oye algo va hacia allí (con los brazos por delante,
 * a tientas) y, si te alcanza, te "purga" otra vez: te quedas a oscuras un momento y apareces en otra parte del
 * Purgatorio. No hace daño ni se le puede hacer (si le pegas, se descoloca y aparece un poco más allá).
 * Hay pocos (unos dos por jugador) y no piensan nada mientras no oyen nada: no cuestan.
 */
public class Purgado extends PathfinderMob {
    public enum Variante {HEROBRINE, SIN_TEXTURA, RELLENO}

    private static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(Purgado.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CAZANDO = SynchedEntityData.defineId(Purgado.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private Vec3 objetivo;
    private int paciencia;
    private double prisa = 1;

    public Purgado(EntityType<? extends Purgado> t, Level l) {
        super(t, l);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder atributos() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANTE, 0);
        entityData.define(CAZANDO, false);
    }

    public Variante variante() {
        Variante[] v = Variante.values();
        return v[Math.floorMod(entityData.get(VARIANTE), v.length)];
    }

    public void setVariante(Variante v) {
        entityData.set(VARIANTE, v.ordinal());
    }

    /** ¿Va detrás de un ruido? (se dibuja con los brazos por delante). */
    public boolean cazando() {
        return entityData.get(CAZANDO);
    }

    // ------------------------------------------------------------------ oído
    /** Ha oído algo en ese sitio; si es fuerte (voz, carrera) va más deprisa. */
    public void oye(Vec3 donde, boolean fuerte) {
        if (objetivo == null)
            level().playSound(null, getX(), getEyeY(), getZ(), ModSounds.PURGADO_OYE.get(), SoundSource.HOSTILE, 1F, 0.9F + random.nextFloat() * 0.2F);
        objetivo = donde;
        paciencia = 80;
        prisa = fuerte ? 1.5 : 1.15;
        entityData.set(CAZANDO, true);
    }

    @Override
    protected void registerGoals() {
        // nada: se mueve solo por lo que oye (ver customServerAiStep)
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 40 == 0 && level().getNearestPlayer(this, 80) == null) {
            discard();
            return;
        }
        if (objetivo != null) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(objetivo.x, objetivo.y, objetivo.z, prisa);
            getLookControl().setLookAt(objetivo.x, objetivo.y + 1.6, objetivo.z);
            if (distanceToSqr(objetivo) < 2.0 || getNavigation().isDone()) {
                if (--paciencia <= 0) {
                    objetivo = null;
                    getNavigation().stop();
                    entityData.set(CAZANDO, false);
                }
            }
        } else if (random.nextInt(300) == 0) {
            // quieto casi siempre; a ratos da unos pasos sin rumbo
            getNavigation().moveTo(getX() + random.nextGaussian() * 4, getY(), getZ() + random.nextGaussian() * 4, 0.5);
        } else if (random.nextInt(120) == 0) {
            getLookControl().setLookAt(getX() + random.nextGaussian() * 5, getEyeY() + random.nextGaussian(), getZ() + random.nextGaussian() * 5);
        }
        if (tickCount % 2 == 0)
            for (ServerPlayer p : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.4)))
                if (!p.isSpectator() && Purgatorio.en(p.level())) {
                    purgar(p);
                    return;
                }
    }

    /** Te ha encontrado: a oscuras un momento y a otra parte del Purgatorio. Él también se va. */
    void purgar(ServerPlayer p) {
        ServerLevel l = (ServerLevel) level();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.PURGADO_PURGA.get(), SoundSource.HOSTILE, 1.2F, 1F);
        l.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.02);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        Purgatorio.recolocar(p);
        p.displayClientMessage(Component.translatable("purgatorio.miscelanea.purgado"), true);
        discard();
    }

    // ------------------------------------------------------------------ ni se le hace daño ni hace
    @Override
    public boolean hurt(DamageSource s, float f) {
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(s, f);
        if (!level().isClientSide && s.getEntity() != null && random.nextInt(2) == 0) {
            // se descoloca: aparece un poco más allá
            for (int i = 0; i < 8; i++)
                if (randomTeleport(getX() + random.nextGaussian() * 8, getY(), getZ() + random.nextGaussian() * 8, true)) break;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return true;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Variante", entityData.get(VARIANTE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(VARIANTE, t.getInt("Variante"));
    }
}
