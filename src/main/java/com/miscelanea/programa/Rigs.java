package com.miscelanea.programa;

import com.miscelanea.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Los rigs de The Outlast Trials (se usan con la tecla G y se recargan solos):
 * Stun (se lanza y aturde), Blind (mina de humo que ciega), Heal (gas que cura) y X-Ray (ves a los enemigos a través de las paredes).
 */
public final class Rigs {
    public enum Rig {
        STUN(75 * 20), CIEGO(60 * 20), CURA(90 * 20), RAYOS(50 * 20);

        public final int recarga;

        Rig(int recarga) {
            this.recarga = recarga;
        }

        public static Rig de(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    private Rigs() {}

    public static void usar(ServerPlayer p) {
        if (!Prueba.dentro(p)) return;
        Jugadores.Estado e = Jugadores.de(p);
        if (e.caido > 0) return;
        if (e.rigOff > 0 || e.rigCd > 0) {
            p.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.2F);
            return;
        }
        ServerLevel lvl = (ServerLevel) p.level();
        switch (e.rig) {
            case STUN -> {
                RigProyectil r = new RigProyectil(lvl, p, new ItemStack(ModItems.RIG_STUN.get()), false);
                r.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, 1.2F, 0.5F);
                lvl.addFreshEntity(r);
                lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.5F);
            }
            case CIEGO -> {
                RigProyectil r = new RigProyectil(lvl, p, new ItemStack(ModItems.RIG_CIEGO.get()), true);
                r.setPos(p.getX(), p.getY() + 0.05, p.getZ());
                lvl.addFreshEntity(r);
                lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 0.7F);
            }
            case CURA -> {
                lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
                lvl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 60, 1.8, 0.8, 1.8, 0.02);
                lvl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 40, 1.5, 0.4, 1.5, 0.01);
                for (Player o : lvl.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(4.0))) {
                    o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 2, false, true, true));
                }
            }
            case RAYOS -> {
                lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.8F);
                for (Mob m : lvl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40.0))) {
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                }
            }
        }
        e.rigCd = e.rig.recarga;
    }

    public static void recargar(ServerPlayer p) {
        Jugadores.de(p).rigCd = 0;
        p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
    }

    /** La porra de Coyle desactiva el rig 30 s. */
    public static void desactivar(ServerPlayer p, int ticks) {
        Jugadores.de(p).rigOff = Math.max(Jugadores.de(p).rigOff, ticks);
    }

    /** Aturdir: el enemigo se para, pierde el objetivo y no ve durante un rato. */
    public static void aturdir(LivingEntity le, int ticks) {
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false));
        if (le instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
    }

    public static void cegar(LivingEntity le, int ticks) {
        le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false));
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2, false, false));
        if (le instanceof Mob m) m.setTarget(null);
    }

    static Vec3 centro(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() / 2, 0);
    }
}
