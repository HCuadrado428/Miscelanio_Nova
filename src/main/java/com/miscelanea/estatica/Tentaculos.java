package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tentáculos de estática (Atraer, Lanzar, Estampar): el servidor mueve a la víctima con su velocidad (los jugadores la
 * reciben con hurtMarked) siguiendo el mismo reloj que el dibujo del cliente: 0-6 los tentáculos llegan, en 6 agarran y
 * luego cada poder hace lo suyo. La caída de vanilla no cuenta mientras está agarrado (el golpe de Estampar es aparte).
 */
public final class Tentaculos {
    private Tentaculos() {}

    public static final int AGARRA = 6;

    /** Duración (ticks) de cada poder (= cliente). */
    public static int duracion(Poder pd) {
        return switch (pd) {
            case ATRAER -> 22;
            case LANZAR -> 20;
            case ESTAMPAR -> 44;
            default -> 10;
        };
    }

    private static final class Agarre {
        final ServerPlayer atk;
        final LivingEntity v;
        final Poder pd;
        final double y0;
        int t;
        boolean golpe;

        Agarre(ServerPlayer atk, LivingEntity v, Poder pd) {
            this.atk = atk;
            this.v = v;
            this.pd = pd;
            this.y0 = v.getY();
        }
    }

    private static final List<Agarre> ACT = new ArrayList<>();

    public static boolean agarrado(LivingEntity e) {
        for (Agarre a : ACT) if (a.v == e) return true;
        return false;
    }

    static boolean agarrar(ServerPlayer p, LivingEntity v, Poder pd) {
        if (agarrado(v)) return false;
        for (Agarre a : ACT) if (a.atk == p) return false;
        ACT.add(new Agarre(p, v, pd));
        EstaticaRed.Tentaculo msg = new EstaticaRed.Tentaculo(p.getId(), v.getId(), pd.ordinal(), duracion(pd));
        Vec3 c = p.position().add(v.position()).scale(0.5);
        EstaticaRed.cerca(p.serverLevel(), c.x, c.y, c.z, 80, msg);
        Estatica.sonido(p.serverLevel(), p.position().add(0, 1.2, 0), EstaticaRegistro.TENTACULO.get(), 1F, 1F);
        return true;
    }

    static void soltarDe(ServerPlayer p) {
        ACT.removeIf(a -> a.atk == p || a.v == p);
    }

    static void limpiar() {
        ACT.clear();
    }

    static void tick(MinecraftServer srv) {
        if (ACT.isEmpty()) return;
        for (Iterator<Agarre> it = ACT.iterator(); it.hasNext(); ) {
            Agarre a = it.next();
            a.t++;
            LivingEntity v = a.v;
            if (!a.atk.isAlive() || !v.isAlive() || v.isRemoved() || v.level() != a.atk.level() || v.distanceToSqr(a.atk) > 40 * 40
                    || a.t > duracion(a.pd) + 30) {
                it.remove();
                continue;
            }
            if (a.t < AGARRA) continue;
            v.fallDistance = 0;
            if (a.t == AGARRA) {
                Estatica.sonido((ServerLevel) v.level(), v.position(), EstaticaRegistro.CHISPA_SONIDO.get(), 0.9F, 0.7F);
                if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(10, true, 0));
            }
            boolean fin = switch (a.pd) {
                case ATRAER -> atraer(a);
                case LANZAR -> lanzar(a);
                case ESTAMPAR -> estampar(a);
                default -> true;
            };
            v.hurtMarked = true;
            if (fin) it.remove();
        }
    }

    private static Vec3 frente(ServerPlayer p) {
        Vec3 m = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        return m.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : m.normalize();
    }

    private static void hacia(LivingEntity v, Vec3 dest, double k, double max) {
        Vec3 d = dest.subtract(v.position()).scale(k);
        if (d.length() > max) d = d.normalize().scale(max);
        v.setDeltaMovement(d);
    }

    private static boolean atraer(Agarre a) {
        Vec3 dest = a.atk.position().add(frente(a.atk).scale(1.4));
        hacia(a.v, dest, 0.32, 1.3);
        if (a.t >= duracion(a.pd) - 4 || a.v.position().distanceToSqr(dest) < 0.3) {
            a.v.setDeltaMovement(a.v.getDeltaMovement().scale(0.2));
            a.v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2), a.atk);
            return true;
        }
        return false;
    }

    private static boolean lanzar(Agarre a) {
        if (a.t < 11) {
            // lo levanta un poco y lo acerca, cogiendo impulso
            Vec3 dest = a.atk.position().add(frente(a.atk).scale(1.8)).add(0, 1.2, 0);
            hacia(a.v, dest, 0.25, 0.8);
            return false;
        }
        Vec3 m = a.atk.getLookAngle();
        double f = ConfigServidor.estaticaLanzarFuerza();
        a.v.setDeltaMovement(m.x * f, Math.max(0.45, m.y * f + 0.55), m.z * f);
        a.v.hasImpulse = true;
        return true;
    }

    private static boolean estampar(Agarre a) {
        LivingEntity v = a.v;
        double arriba = a.y0 + ConfigServidor.estaticaEstamparAltura();
        if (a.t < 24) {
            Vec3 d = v.getDeltaMovement();
            v.setDeltaMovement(d.x * 0.5, Mth.clamp((arriba - v.getY()) * 0.28, -0.6, 0.9), d.z * 0.5);
            return false;
        }
        if (a.t < 28) {
            v.setDeltaMovement(0, 0.02, 0);   // arriba, un instante quieto
            return false;
        }
        if (!a.golpe) {
            v.setDeltaMovement(0, -2.6, 0);
            a.golpe = true;
            return false;
        }
        if (v.onGround() || a.t > duracion(a.pd) + 10) {
            ServerLevel l = (ServerLevel) v.level();
            v.fallDistance = 0;
            v.hurt(l.damageSources().playerAttack(a.atk), ConfigServidor.estaticaEstamparDano());
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3), a.atk);
            Vec3 p = v.position();
            BlockState s = l.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
            if (!s.isAir()) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.x, p.y + 0.1, p.z, 40, 0.6, 0.1, 0.6, 0.15);
            l.sendParticles(EstaticaRegistro.CHISPA.get(), p.x, p.y + 0.3, p.z, 30, 0.7, 0.3, 0.7, 0.0);
            EstaticaRed.cerca(l, p.x, p.y, p.z, 32, EstaticaRed.Fx.en(EstaticaRed.Fx.GOLPE_SUELO, v.getId(), p, 0));
            Estatica.sonido(l, p, EstaticaRegistro.GOLPE.get(), 1.2F, 0.9F);
            return true;
        }
        v.setDeltaMovement(0, -2.6, 0);
        return false;
    }
}
