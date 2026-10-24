package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.FlashbangPacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Los modos de Shift + clic derecho que dan los encantamientos. */
public final class Modos {
    private Modos() {}

    private static Component msg(String key, Object... args) {
        return Component.translatable("msg." + Miscelanea.MOD_ID + "." + key, args);
    }

    // ================================================================ DESTELLO

    public static final int CARGA_DESTELLO = 20; // 1 s manteniendo
    private static final double ALCANCE_DESTELLO = 14.0;

    public static void destelloCargando(Player player, int usado) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 boca = AspiraUtil.boca(player);
        if (usado < CARGA_DESTELLO && usado % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, boca.x, boca.y, boca.z, 1 + usado / 5, 0.05, 0.05, 0.05, 0.01);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4F, 0.8F + usado * 0.06F);
        } else if (usado == CARGA_DESTELLO) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 2.0F);
        } else if (usado > CARGA_DESTELLO && usado % 6 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, boca.x, boca.y, boca.z, 2, 0.08, 0.08, 0.08, 0.0);
        }
    }

    public static void destello(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 boca = AspiraUtil.boca(player);
        Vec3 look = player.getViewVector(1.0F);

        level.sendParticles(ParticleTypes.FLASH, boca.x, boca.y, boca.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, boca.x, boca.y, boca.z, 40, 0.3, 0.3, 0.3, 0.25);
        level.playSound(null, boca.x, boca.y, boca.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5F, 1.4F);
        level.playSound(null, boca.x, boca.y, boca.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 2.0F);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ALCANCE_DESTELLO),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Vec3 haciaE = e.getEyePosition().subtract(boca);
            double dist = haciaE.length();
            if (dist > ALCANCE_DESTELLO || dist < 1.0E-3) continue;
            // Tiene que estar delante de la boquilla...
            if (haciaE.scale(1.0 / dist).dot(look) < 0.5) continue;
            // ...y mirándote
            double mirando = e.getViewVector(1.0F).dot(haciaE.scale(-1.0 / dist));
            if (mirando < 0.3) continue;
            if (!e.hasLineOfSight(player)) continue;

            float intensidad = (float) (Mth.clamp((mirando - 0.3) / 0.7, 0.0, 1.0) * (1.0 - dist / ALCANCE_DESTELLO * 0.5));
            intensidad = Math.max(0.25F, intensidad);
            int dur = 40 + (int) (100 * intensidad);

            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, dur, 0), player);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 2), player);

            if (e instanceof Mob mob) {
                // Aturdido: se olvida de su objetivo y casi no se mueve (y es más fácil de capturar)
                mob.setTarget(null);
                mob.getNavigation().stop();
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 6), player);
                Vec3 c = e.getEyePosition();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.4, c.z, 8, 0.3, 0.1, 0.3, 0.05);
            }
            if (e instanceof ServerPlayer sp) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new FlashbangPacket(intensidad));
            }
        }
    }

    // ================================================================ DESATASCADOR

    public static void dispararDesatascador(Player player) {
        DesatascadorEntity d = new DesatascadorEntity(player.level(), player);
        d.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.2F, 0.3F);
        player.level().addFreshEntity(d);
        Succion.registrarDesatascador(player, d);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    // ================================================================ SOPLIDO

    /** @param mejorado true con el encantamiento Soplido: más fuerza y devuelve proyectiles. */
    public static void soplido(Player player, int usado, boolean mejorado) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 boca = AspiraUtil.boca(player);
        Vec3 look = player.getViewVector(1.0F);

        if (usado % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                Vec3 dir = look.add((player.getRandom().nextDouble() - 0.5) * 0.5,
                        (player.getRandom().nextDouble() - 0.5) * 0.5, (player.getRandom().nextDouble() - 0.5) * 0.5);
                AspiraUtil.aire(level, boca, boca.add(dir.normalize().scale(6.0)), false);
            }
        }
        if (usado % 20 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.4F, 1.6F);
        }

        for (Entity e : AspiraUtil.enCono(player, Entity.class, Succion.ALCANCE, 0.75)) {
            double dist = AspiraUtil.centro(e).distanceTo(boca);
            double fuerza = (0.1 + 0.25 * (1.0 - dist / Succion.ALCANCE)) * (mejorado ? 1.0 : 0.6);

            if (e instanceof Projectile p) {
                if (!mejorado) continue;
                // Devolver proyectiles
                if (p instanceof DesatascadorEntity) continue;
                double vel = Math.max(1.0, p.getDeltaMovement().length());
                p.setDeltaMovement(look.scale(vel));
                p.setOwner(player);
                p.hurtMarked = true;
                continue;
            }
            if (e instanceof LivingEntity le) {
                if (le.getBbWidth() >= 1.4F || le.getMaxHealth() >= 100.0F) fuerza *= 0.3;
                le.clearFire();
            }
            if (e instanceof LivingEntity || e instanceof ItemEntity) {
                Vec3 v = e.getDeltaMovement().add(look.scale(fuerza)).add(0, 0.04, 0);
                if (v.length() > 1.5) v = v.normalize().scale(1.5);
                AspiraUtil.empujar(e, v);
            }
        }
    }

    /** Toque rápido con Soplido: dispara el último mob capturado como si fuera una bala de cañón. */
    public static boolean canon(Player player) {
        Vec3 look = player.getViewVector(1.0F);
        Entity bala = Deposito.soltar(player, AspiraUtil.boca(player).add(look.scale(0.8)), look.scale(2.0));
        if (bala == null) return false;
        Impactos.seguir(bala, Impactos.Tipo.PROYECTIL, player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    // ================================================================ TURBO

    public static void turbo(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 look = player.getViewVector(1.0F);
        // Reacción: sales disparado en dirección contraria a la boquilla
        Vec3 v = player.getDeltaMovement().scale(0.3).add(look.scale(-1.25));
        AspiraUtil.empujar(player, v);

        Vec3 boca = AspiraUtil.boca(player);
        for (int i = 0; i < 12; i++) {
            Vec3 dir = look.add((player.getRandom().nextDouble() - 0.5) * 0.4,
                    (player.getRandom().nextDouble() - 0.5) * 0.4, (player.getRandom().nextDouble() - 0.5) * 0.4);
            AspiraUtil.aire(level, boca, boca.add(dir.normalize().scale(3.5)), false);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    // ================================================================ ALQUIMISTA

    /**
     * Aspira nubes de pociones y roba efectos a quien tengas delante.
     * - Efectos POSITIVOS → reserva de la mochila (se usan solos cuando hacen falta, ver {@link Reserva}).
     * - Efectos negativos o neutros → tanque, solo para expulsarlos (toque rápido = rociar).
     */
    public static void alquimista(Player player, int usado) {
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack mochila = Deposito.mochila(player);
        if (mochila.isEmpty()) return;
        Vec3 boca = AspiraUtil.boca(player);

        // Nubes de pociones persistentes
        for (AreaEffectCloud nube : AspiraUtil.enCono(player, AreaEffectCloud.class, 10.0, 0.7)) {
            Potion pocion = nube.getPotion();
            if (pocion == Potions.EMPTY || pocion.getEffects().isEmpty()) continue;

            for (MobEffectInstance ef : pocion.getEffects()) {
                if (ef.getEffect().isBeneficial()) {
                    if (ef.getEffect().isInstantenous()) {
                        if (usado % 8 == 0) Reserva.anadir(mochila, ef.getEffect(), ef.getAmplifier(), 1);
                    } else {
                        Reserva.anadir(mochila, ef.getEffect(), ef.getAmplifier(), Math.max(ef.getDuration() / 40, 10));
                    }
                } else {
                    Deposito.anadirAlTanque(mochila, ef, 0.6F);
                }
            }

            float r = nube.getRadius() - 0.12F;
            if (r < 0.5F) nube.discard();
            else nube.setRadius(r);

            Vec3 p = nube.position().add(0, 0.3, 0);
            int color = nube.getColor();
            double cr = (color >> 16 & 255) / 255.0, cg = (color >> 8 & 255) / 255.0, cb = (color & 255) / 255.0;
            level.sendParticles(ParticleTypes.ENTITY_EFFECT, p.x, p.y, p.z, 0, cr, cg, cb, 1.0);
            AspiraUtil.chorro(level, ParticleTypes.WITCH, p, boca, 0.4);
        }

        // Robar un efecto por segundo a quien esté delante
        if (usado % 20 == 10) {
            for (LivingEntity e : AspiraUtil.enCono(player, LivingEntity.class, 6.0, 0.8)) {
                MobEffectInstance robado = e.getActiveEffects().stream()
                        .filter(i -> !i.isInfiniteDuration() && !i.isAmbient())
                        .findFirst().orElse(null);
                if (robado == null) continue;

                boolean guardado;
                if (robado.getEffect().isBeneficial()) {
                    int t = robado.getEffect().isInstantenous() ? 1 : Math.min(robado.getDuration(), 2400);
                    guardado = Reserva.anadir(mochila, robado.getEffect(), robado.getAmplifier(), t);
                } else {
                    guardado = Deposito.anadirAlTanque(mochila,
                            new MobEffectInstance(robado.getEffect(), Math.min(robado.getDuration(), 1200), robado.getAmplifier()), 20.0F);
                }
                if (!guardado) break;
                e.removeEffect(robado.getEffect());

                Vec3 c = AspiraUtil.centro(e);
                AspiraUtil.chorro(level, ParticleTypes.WITCH, c, boca, 0.5);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.2F);
                break;
            }
        }
    }

    /** Toque rápido con Alquimista: rocía el tanque como nube persistente donde mires. */
    public static boolean rociar(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        ItemStack mochila = Deposito.mochila(player);
        ItemStack tanque = Deposito.tanque(mochila);
        if (tanque.isEmpty()) {
            return false;
        }
        float carga = Math.max(10.0F, Deposito.carga(mochila));

        HitResult hit = player.pick(10.0, 1.0F, false);
        Vec3 p = hit.getLocation();

        AreaEffectCloud nube = new AreaEffectCloud(level, p.x, p.y, p.z);
        nube.setOwner(player);
        nube.setPotion(PotionUtils.getPotion(tanque));
        for (MobEffectInstance i : PotionUtils.getCustomEffects(tanque)) {
            nube.addEffect(new MobEffectInstance(i));
        }
        float radio = 1.5F + carga / 25.0F;
        int dur = 200 + (int) (carga * 4);
        nube.setRadius(radio);
        nube.setRadiusOnUse(-0.3F);
        nube.setWaitTime(5);
        nube.setDuration(dur);
        nube.setRadiusPerTick(-radio / dur);
        if (tanque.hasTag() && tanque.getTag().contains("CustomPotionColor")) {
            nube.setFixedColor(tanque.getTag().getInt("CustomPotionColor"));
        }
        level.addFreshEntity(nube);

        Vec3 boca = AspiraUtil.boca(player);
        for (int i = 0; i < 8; i++) AspiraUtil.chorro(level, ParticleTypes.WITCH, boca, p, 0.8);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);

        Deposito.vaciarTanque(mochila);
        return true;
    }
}
