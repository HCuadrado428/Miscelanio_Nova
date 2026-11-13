package com.miscelanea.espera;

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
 * Habitante de La Espera. No ve nada: te nota si te acercas, sin importar si corres, hablas o vas agachado. Cuando te
 * nota va hacia donde estabas (con los brazos por delante,
 * a tientas) y, si te alcanza, te "purga" otra vez: te quedas a oscuras un momento y apareces en otra parte del
 * Espera. No hace daño ni se le puede hacer (si le pegas, se descoloca y aparece un poco más allá).
 * Hay pocos (unos dos por jugador) y no piensan nada mientras no te notan: no cuestan.
 */
public class Paciente extends PathfinderMob {
    public enum Variante {HEROBRINE, SIN_TEXTURA, RELLENO}

    private static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(Paciente.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CAZANDO = SynchedEntityData.defineId(Paciente.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private Vec3 objetivo;
    private int paciencia;
    private double prisa = 1;
    /** Para ver si se ha atascado: dónde estaba hace un rato, cuántas veces seguidas no ha avanzado y cuándo buscó camino. */
    @Nullable
    private Vec3 antes;
    private int atascos, ultimoCamino = -100;

    public Paciente(EntityType<? extends Paciente> t, Level l) {
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
            level().playSound(null, getX(), getEyeY(), getZ(), ModSounds.PACIENTE_OYE.get(), SoundSource.HOSTILE, 1F, 0.9F + random.nextFloat() * 0.2F);
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
        if (tickCount % 40 == 0 && (level().getNearestPlayer(this, 80) == null || !EsperaConfig.activado())) {
            discard();
            return;
        }
        if (objetivo != null) {
            // el camino se busca como mucho cada 10 tics (cada 20 si el último no llegaba): barato aunque no se pueda llegar
            boolean hecho = getNavigation().isDone();
            if (tickCount - ultimoCamino >= (hecho ? 20 : 10) && distanceToSqr(objetivo) >= 2.0) {
                getNavigation().moveTo(objetivo.x, objetivo.y, objetivo.z, prisa);
                ultimoCamino = tickCount;
            }
            getLookControl().setLookAt(objetivo.x, objetivo.y + 1.6, objetivo.z);
            if (distanceToSqr(objetivo) < 2.0 || hecho) {
                if (--paciencia <= 0) olvidar();
            }
            if (objetivo != null && tickCount % 40 == 0) desatascar();
        } else if (random.nextInt(300) == 0) {
            // quieto casi siempre; a ratos da unos pasos sin rumbo
            getNavigation().moveTo(getX() + random.nextGaussian() * 4, getY(), getZ() + random.nextGaussian() * 4, 0.5);
        } else if (random.nextInt(120) == 0) {
            getLookControl().setLookAt(getX() + random.nextGaussian() * 5, getEyeY() + random.nextGaussian(), getZ() + random.nextGaussian() * 5);
        }
        if (tickCount % 2 == 0)
            for (ServerPlayer p : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.4)))
                if (!p.isSpectator() && Espera.en(p.level())) {
                    purgar(p);
                    return;
                }
    }

    private void olvidar() {
        objetivo = null;
        antes = null;
        atascos = 0;
        getNavigation().stop();
        entityData.set(CAZANDO, false);
    }

    /**
     * Si va detrás de algo y en 2 s no ha avanzado ni medio bloque (contra una esquina, una valla, una silla...), se
     * "descoloca" un par de bloques hacia donde iba, como cuando le pegas; a la tercera, lo deja.
     */
    private void desatascar() {
        Vec3 aqui = position();
        boolean quieto = antes != null && aqui.distanceToSqr(antes) < 0.25 && distanceToSqr(objetivo) > 4.0;
        antes = aqui;
        if (!quieto) {
            atascos = 0;
            return;
        }
        if (++atascos >= 3) {
            olvidar();
            return;
        }
        Vec3 hacia = objetivo.subtract(aqui).multiply(1, 0, 1).normalize();
        for (int i = 0; i < 6; i++) {
            double k = 1.5 + random.nextDouble() * 2;
            if (randomTeleport(getX() + hacia.x * k + random.nextGaussian() * 0.8, getY(), getZ() + hacia.z * k + random.nextGaussian() * 0.8, false)) {
                getNavigation().stop();
                ultimoCamino = -100;
                break;
            }
        }
    }

    /** Te ha encontrado: a oscuras un momento y a otra parte del Espera. Él también se va. */
    void purgar(ServerPlayer p) {
        ServerLevel l = (ServerLevel) level();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.PACIENTE_PURGA.get(), SoundSource.HOSTILE, 1.2F, 1F);
        l.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.02);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        com.miscelanea.espera.eventos.EsperaExtras.pillado(p);   // logros
        Espera.recolocar(p);
        p.displayClientMessage(Component.translatable("espera.miscelanea.paciente"), true);
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

    /** No se guardan con el chunk: así no se van acumulando en los sitios por los que ya ha pasado alguien. */
    @Override
    public boolean shouldBeSaved() {
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
