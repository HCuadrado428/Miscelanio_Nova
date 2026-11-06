package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
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
        /** Posición en el grupo (Atraer agarra a varios: cada uno llega a un sitio distinto delante de él). */
        final int i;
        int t;
        boolean golpe;

        Agarre(ServerPlayer atk, LivingEntity v, Poder pd, int i) {
            this.atk = atk;
            this.v = v;
            this.pd = pd;
            this.y0 = v.getY();
            this.i = i;
        }
    }

    /** Lanzados: hacen daño al chocar contra una pared o el suelo. */
    private static final class Vuelo {
        final ServerPlayer atk;
        final LivingEntity v;
        int t;

        Vuelo(ServerPlayer atk, LivingEntity v) {
            this.atk = atk;
            this.v = v;
        }
    }

    private static final List<Agarre> ACT = new ArrayList<>();
    private static final List<Vuelo> VUELOS = new ArrayList<>();

    public static boolean agarrado(LivingEntity e) {
        for (Agarre a : ACT) if (a.v == e) return true;
        return false;
    }

    static boolean agarrar(ServerPlayer p, List<LivingEntity> vs, Poder pd) {
        for (Agarre a : ACT) if (a.atk == p) return false;
        int i = 0;
        for (LivingEntity v : vs) {
            if (agarrado(v)) continue;
            ACT.add(new Agarre(p, v, pd, i++));
            EstaticaRed.Tentaculo msg = new EstaticaRed.Tentaculo(p.getId(), v.getId(), pd.ordinal(), duracion(pd));
            Vec3 c = p.position().add(v.position()).scale(0.5);
            EstaticaRed.cerca(p.serverLevel(), c.x, c.y, c.z, 80, msg);
        }
        if (i == 0) return false;
        Estatica.sonido(p.serverLevel(), p.position().add(0, 1.2, 0), EstaticaRegistro.TENTACULO.get(), 1F, 1F);
        return true;
    }

    static void soltarDe(ServerPlayer p) {
        ACT.removeIf(a -> a.atk == p || a.v == p);
        VUELOS.removeIf(a -> a.atk == p || a.v == p);
    }

    static void limpiar() {
        ACT.clear();
        VUELOS.clear();
    }

    static void tick(MinecraftServer srv) {
        tickVuelos();
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
        Vec3 fr = frente(a.atk), lado = new Vec3(-fr.z, 0, fr.x);
        // el primero justo delante; los demás, a los lados
        int k = a.i == 0 ? 0 : (a.i % 2 == 1 ? 1 : -1) * ((a.i + 1) / 2);
        Vec3 dest = a.atk.position().add(fr.scale(1.4 + a.v.getBbWidth() * 0.5 + Math.abs(k) * 0.3)).add(lado.scale(k * (1.0 + a.v.getBbWidth())));
        hacia(a.v, dest, 0.32, 1.3);
        if (a.t >= duracion(a.pd) - 4 || a.v.position().distanceToSqr(dest) < 0.3) {
            a.v.setDeltaMovement(a.v.getDeltaMovement().scale(0.2));
            Estatica.interferir(a.atk, a.v, 1);
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
        double k = Math.max(0.15, 1 - a.v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.6);
        a.v.setDeltaMovement(m.x * f * k, Math.max(0.45, m.y * f + 0.55) * k, m.z * f * k);
        a.v.hasImpulse = true;
        VUELOS.removeIf(x -> x.v == a.v);
        VUELOS.add(new Vuelo(a.atk, a.v));
        return true;
    }

    private static void tickVuelos() {
        if (VUELOS.isEmpty()) return;
        for (Iterator<Vuelo> it = VUELOS.iterator(); it.hasNext(); ) {
            Vuelo x = it.next();
            x.t++;
            LivingEntity v = x.v;
            if (!v.isAlive() || v.isRemoved() || !x.atk.isAlive() || v.level() != x.atk.level() || x.t > 60) {
                it.remove();
                continue;
            }
            if (x.t < 3) continue;
            boolean pared = v.horizontalCollision, suelo = v.onGround() && x.t > 5;
            if (!pared && !suelo) continue;
            it.remove();
            ServerLevel l = (ServerLevel) v.level();
            // más daño cuanto más deprisa iba (el suelo, solo si cae de lo alto)
            double vel = v.getDeltaMovement().length();
            float d = ConfigServidor.estaticaLanzarImpacto() * (pared ? 1F : (float) Mth.clamp(0.4 + vel, 0.4, 1.0));
            if (d > 0 && Estatica.golpear(x.atk, v, d)) Estatica.interferir(x.atk, v, 1);
            Vec3 p = v.position();
            EstaticaRed.cerca(l, p.x, p.y, p.z, 32, EstaticaRed.Fx.en(EstaticaRed.Fx.GOLPE_SUELO, v.getId(), p, 0));
            Estatica.sonido(l, p, EstaticaRegistro.GOLPE.get(), 1F, 1.1F);
        }
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
            float dano = ConfigServidor.estaticaEstamparDano();
            Estatica.golpear(a.atk, v, dano);
            Estatica.interferir(a.atk, v, 2);
            Vec3 p = v.position();
            // la onda del golpe: los de alrededor reciben la mitad y saltan por los aires
            double r = ConfigServidor.estaticaEstamparOnda();
            if (r > 0) {
                for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(r, 1.5, r),
                        x -> x != v && Estatica.objetivoValido(a.atk, x) && x.distanceTo(v) <= r + 0.5)) {
                    if (Estatica.golpear(a.atk, o, dano * 0.5F)) Estatica.interferir(a.atk, o, 1);
                    Estatica.empujar(o, o.position().subtract(p), 0.7, 0.6);
                }
            }
            EstaticaRed.cerca(l, p.x, p.y, p.z, 48, EstaticaRed.Fx.en(EstaticaRed.Fx.GOLPE_SUELO, v.getId(), p, (int) Math.round(r * 10)));
            Estatica.sonido(l, p, EstaticaRegistro.GOLPE.get(), 1.2F, 0.9F);
            return true;
        }
        v.setDeltaMovement(0, -2.6, 0);
        return false;
    }
}
