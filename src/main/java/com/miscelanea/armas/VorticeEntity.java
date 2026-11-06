package com.miscelanea.armas;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Agujero negro verde que deja la esfera de plasma: durante 3 s (5 en sobrecarga) atrae mobs y objetos sueltos,
 * hace daño a lo que está en el centro y al final revienta lanzándolo todo hacia fuera.
 */
public class VorticeEntity extends Entity {
    private static final EntityDataAccessor<Boolean> GRANDE = SynchedEntityData.defineId(VorticeEntity.class, EntityDataSerializers.BOOLEAN);
    @Nullable
    private Player duenyo;

    public VorticeEntity(EntityType<? extends VorticeEntity> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    public VorticeEntity(Level l, @Nullable Player p, boolean grande) {
        this(ModEntities.VORTICE.get(), l);
        duenyo = p;
        entityData.set(GRANDE, grande);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(GRANDE, false);
    }

    boolean grande() {
        return entityData.get(GRANDE);
    }

    int dura() {
        return grande() ? 100 : 60;
    }

    double radio() {
        return grande() ? 14 : 8;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 c = position();
        double r = radio();
        if (level().isClientSide) {
            // espiral de plasma cayendo hacia el centro
            for (int i = 0; i < (grande() ? 14 : 7); i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 0.5 + random.nextDouble() * r * 0.6, h = (random.nextDouble() - 0.5) * d * 0.5;
                double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                Vec3 hacia = c.subtract(x, c.y + h, z).normalize().scale(0.25);
                Vec3 giro = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.25);
                level().addParticle(PlasmaEntity.VERDE_P, x, c.y + h, z, hacia.x + giro.x, hacia.y, hacia.z + giro.z);
            }
            level().addParticle(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 0, 0, 0);
            level().addParticle(ParticleTypes.SQUID_INK, c.x + (random.nextDouble() - 0.5) * 0.6, c.y + (random.nextDouble() - 0.5) * 0.6, c.z + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (tickCount == 1 || tickCount % 20 == 0) sl.playSound(null, c.x, c.y, c.z, ModSounds.BFG_VORTICE.get(), SoundSource.PLAYERS, grande() ? 2.5F : 1.6F, grande() ? 0.7F : 1F);
        for (Entity e : sl.getEntities(this, getBoundingBox().inflate(r), x -> (x instanceof LivingEntity || x instanceof ItemEntity) && x.isAlive() && !x.isSpectator())) {
            if (e instanceof Player p && (p.isCreative() || p == duenyo)) continue;
            Vec3 d = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double dist = d.length();
            if (dist > r || dist < 1e-3) continue;
            double fuerza = (grande() ? 0.16 : 0.11) * (1.2 - dist / r);
            Vec3 tiron = d.normalize().scale(fuerza);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(tiron));
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof LivingEntity le && dist < 2.2 && tickCount % 10 == 0) {
                le.invulnerableTime = 0;
                DanoArma.golpear(le, damageSources().indirectMagic(this, duenyo), (grande() ? 5F : 3F) * com.miscelanea.ConfigServidor.bfgVorticeMult(), DanoArma.Arma.BFG_VORTICE);
            }
        }
        if (tickCount >= dura()) {
            sl.sendParticles(PlasmaEntity.VERDE, c.x, c.y, c.z, 200, 1.5, 1.5, 1.5, 0);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 0.2, 0.2, 0.2, 0.6);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sl.playSound(null, c.x, c.y, c.z, ModSounds.BFG_ESTALLIDO.get(), SoundSource.PLAYERS, 3F, 1.3F);
            TemblorPacket.enviar(sl, c, r * 3, grande() ? 2.5F : 1.4F, 16);
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r * 0.5), x -> x.isAlive() && x != duenyo)) {
                Vec3 d = le.position().subtract(c);
                Vec3 emp = d.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : d.normalize();
                le.invulnerableTime = 0;
                DanoArma.golpear(le, damageSources().indirectMagic(this, duenyo), (grande() ? 30F : 18F) * com.miscelanea.ConfigServidor.bfgVorticeMult(), DanoArma.Arma.BFG_VORTICE);
                le.setDeltaMovement(emp.scale(1.4).add(0, 0.6, 0));
                le.hurtMarked = true;
            }
            discard();
        }
    }
}
