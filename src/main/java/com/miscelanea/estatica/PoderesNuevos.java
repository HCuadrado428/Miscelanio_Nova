package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Poderes de terror analógico: Canal perdido (vacío de estática), Pausa (congelar y acumular daño), Rebobinar (volver
 * 4 s atrás), Sin señal (zona de ruido) y Grabación (clon que repite tus últimos 3 s).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PoderesNuevos {
    private PoderesNuevos() {}

    // ================================================================== Canal perdido
    private static final class EnCanal {
        final ServerPlayer atk;
        final LivingEntity v;
        final Vec3 pos;
        final long fin;

        EnCanal(ServerPlayer atk, LivingEntity v, Vec3 pos, long fin) {
            this.atk = atk;
            this.v = v;
            this.pos = pos;
            this.fin = fin;
        }
    }

    private static final List<EnCanal> CANALES = new ArrayList<>();

    public static boolean enCanal(LivingEntity e) {
        for (EnCanal c : CANALES) if (c.v == e) return true;
        return false;
    }

    static boolean canal(ServerPlayer p, int pista) {
        LivingEntity v = Estatica.mirado(p, ConfigServidor.estaticaArrastrarAlcance(), pista);
        if (v == null || !Estatica.agarrable(p, v) || enCanal(v) || pausado(v)) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.sin_objetivo"));
            return false;
        }
        if (Math.max(v.getBbWidth(), v.getBbHeight()) > ConfigServidor.estaticaArrastrarTamano()) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.demasiado_grande"));
            return false;
        }
        if (!Senal.gastar(p, Poder.CANAL.coste())) return false;
        ServerLevel l = p.serverLevel();
        int dur = ConfigServidor.estaticaCanalTicks();
        Vec3 c = v.getBoundingBox().getCenter();
        // desaparece para todos (y en su pantalla, el vacío)
        EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.OCULTAR, v.getId(), c, dur));
        EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, v.getId(), c, 1));
        Vec3 pc = p.getBoundingBox().getCenter();
        EstaticaRed.cerca(l, pc.x, pc.y, pc.z, 64, new EstaticaRed.Fx(EstaticaRed.Fx.ARCO, p.getId(), pc.x, pc.y, pc.z, c.x, c.y, c.z, 0));
        if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Pantallazo(EstaticaRed.Pantallazo.VACIO, dur, Estatica.semilla(p)));
        Estatica.sonido(l, c, EstaticaRegistro.ESTALLIDO.get(), 1F, 0.6F);
        Estatica.sonido(l, c, EstaticaRegistro.PANTALLA.get(), 0.8F, 0.5F);
        CANALES.add(new EnCanal(p, v, v.position(), l.getGameTime() + dur));
        return true;
    }

    private static void tickCanales(long t) {
        for (Iterator<EnCanal> it = CANALES.iterator(); it.hasNext(); ) {
            EnCanal c = it.next();
            LivingEntity v = c.v;
            if (!v.isAlive() || v.isRemoved()) {
                it.remove();
                continue;
            }
            boolean fin = t >= c.fin || !c.atk.isAlive() || c.atk.level() != v.level();
            if (!fin) {
                // quieto donde desapareció (no se le ve) y sin que nada le mueva
                v.setDeltaMovement(Vec3.ZERO);
                v.fallDistance = 0;
                if (v.position().distanceToSqr(c.pos) > 0.09) Estatica.mover(v, c.pos, v.getYRot(), v.getXRot());
                if (v instanceof Mob m) {
                    m.getNavigation().stop();
                    m.setTarget(null);
                }
                v.hurtMarked = true;
                if (v instanceof ServerPlayer vp && (c.fin - t) % 23 == 0)
                    vp.playNotifySound(EstaticaRegistro.CRUJIDO.get(), SoundSource.HOSTILE, 0.9F, 0.4F + vp.getRandom().nextFloat() * 0.3F);
                continue;
            }
            it.remove();
            escupir(c);
        }
    }

    private static void escupir(EnCanal c) {
        LivingEntity v = c.v;
        ServerLevel l = (ServerLevel) v.level();
        // lo escupe en otro sitio, a 6-14 bloques, en una dirección al azar donde quepa
        Vec3 dest = c.pos;
        for (int i = 0; i < 10; i++) {
            double a = v.getRandom().nextDouble() * Mth.TWO_PI;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 q = Estatica.libre(l, v, c.pos, d, 6 + v.getRandom().nextDouble() * 8);
            if (q.distanceToSqr(c.pos) > 9) {
                dest = q;
                break;
            }
        }
        Estatica.mover(v, dest, v.getYRot() + 180F, 20F);
        v.setDeltaMovement(0, 0.3, 0);
        v.hurtMarked = true;
        EstaticaRed.cerca(l, dest.x, dest.y, dest.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.OCULTAR, v.getId(), dest, 0));
        Vec3 cc = dest.add(0, v.getBbHeight() * 0.5, 0);
        EstaticaRed.cerca(l, cc.x, cc.y, cc.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, v.getId(), cc, 1));
        Estatica.sonido(l, cc, EstaticaRegistro.ESTALLIDO.get(), 1.1F, 0.8F);
        if (c.atk.isAlive() && c.atk.level() == l) {
            Estatica.golpear(c.atk, v, ConfigServidor.estaticaCanalDano());
            Estatica.interferir(c.atk, v, 2);
            Estatica.efecto(v, MobEffects.CONFUSION, 140, 0, c.atk);
            Estatica.efecto(v, MobEffects.DARKNESS, 60, 0, c.atk);
        }
        if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(30, false, ConfigServidor.estaticaToqueCorromperTicks()));
    }

    // ================================================================== Pausa
    private static final class Pausado {
        final ServerPlayer atk;
        final LivingEntity v;
        final Vec3 pos;
        final float yaw, pitch;
        final long fin;
        float acum;

        Pausado(ServerPlayer atk, LivingEntity v, long fin) {
            this.atk = atk;
            this.v = v;
            this.pos = v.position();
            this.yaw = v.getYRot();
            this.pitch = v.getXRot();
            this.fin = fin;
        }
    }

    private static final List<Pausado> PAUSAS = new ArrayList<>();

    public static boolean pausado(LivingEntity e) {
        for (Pausado x : PAUSAS) if (x.v == e) return true;
        return false;
    }

    static boolean pausa(ServerPlayer p, int pista) {
        LivingEntity v = Estatica.mirado(p, ConfigServidor.estaticaTentaculosAlcance(), pista);
        if (v == null || !Estatica.agarrable(p, v) || pausado(v) || enCanal(v)) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.sin_objetivo"));
            return false;
        }
        if (!Senal.gastar(p, Poder.PAUSA.coste())) return false;
        ServerLevel l = p.serverLevel();
        int dur = Math.max(10, Math.round(ConfigServidor.estaticaPausaTicks() * Estatica.factor(v)));
        PAUSAS.add(new Pausado(p, v, l.getGameTime() + dur));
        // congelado: sin fuerza para hacer daño ni minar (no se toca el NoAI: si se guardara así, se quedaría quieto para siempre)
        Estatica.efecto(v, MobEffects.WEAKNESS, dur, 127, p);
        Estatica.efecto(v, MobEffects.DIG_SLOWDOWN, dur, 4, p);
        Estatica.efecto(v, MobEffects.MOVEMENT_SLOWDOWN, dur, 9, p);
        Vec3 c = v.getBoundingBox().getCenter();
        EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.PAUSA, v.getId(), c, dur));
        if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Pantallazo(EstaticaRed.Pantallazo.PAUSA, dur, 0));
        Estatica.sonido(l, c, EstaticaRegistro.CRUJIDO.get(), 1.2F, 0.5F);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
        return true;
    }

    private static void tickPausas(long t) {
        for (Iterator<Pausado> it = PAUSAS.iterator(); it.hasNext(); ) {
            Pausado x = it.next();
            LivingEntity v = x.v;
            if (!v.isAlive() || v.isRemoved()) {
                it.remove();
                continue;
            }
            if (t < x.fin && x.atk.level() == v.level()) {
                if (v instanceof Mob m) {
                    m.getNavigation().stop();
                    m.setTarget(null);
                }
                v.setDeltaMovement(Vec3.ZERO);
                v.fallDistance = 0;
                if (v.position().distanceToSqr(x.pos) > 0.04) Estatica.mover(v, x.pos, x.yaw, x.pitch);
                v.hurtMarked = true;
                continue;
            }
            it.remove();
            v.removeEffect(MobEffects.WEAKNESS);
            v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            v.removeEffect(MobEffects.DIG_SLOWDOWN);
            ServerLevel l = (ServerLevel) v.level();
            Vec3 c = v.getBoundingBox().getCenter();
            EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.PLAY, v.getId(), c, Math.round(x.acum)));
            Estatica.sonido(l, c, EstaticaRegistro.ESTALLIDO.get(), 1F, 1.3F);
            if (x.acum > 0 && x.atk.isAlive()) {
                // todo lo que recibió mientras estaba en pausa, de golpe (y algo más)
                v.invulnerableTime = 0;
                float d = Math.min(120F, x.acum * ConfigServidor.estaticaPausaMult());
                Estatica.golpear(x.atk, v, d);
                Estatica.empujar(v, c.subtract(x.atk.position()), 0.6 + Math.min(1.5, d / 20.0), 0.4);
                Estatica.sonido(l, c, EstaticaRegistro.GOLPE.get(), 1.2F, 1.0F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void golpeEnPausa(LivingAttackEvent e) {
        if (PAUSAS.isEmpty() || e.getEntity().level().isClientSide) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        for (Pausado x : PAUSAS) {
            if (x.v != e.getEntity()) continue;
            x.acum += e.getAmount();
            e.setCanceled(true);
            Vec3 c = x.v.getBoundingBox().getCenter();
            EstaticaRed.cerca((ServerLevel) x.v.level(), c.x, c.y, c.z, 32, EstaticaRed.Fx.en(EstaticaRed.Fx.CHISPAZO, x.v.getId(), c, 0));
            return;
        }
    }

    // ================================================================== Rebobinar
    private static final Set<java.util.UUID> REBOBINANDO = new HashSet<>();

    public static boolean rebobinando(ServerPlayer p) {
        return REBOBINANDO.contains(p.getUUID());
    }

    static boolean rebobinar(ServerPlayer p) {
        Senal.Paso[] h = Senal.historial(p);
        int i0 = 0;
        while (i0 < h.length && h[i0].dim() != p.level().dimension()) i0++;
        if (h.length - i0 < 20) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.rebobinar_no"));
            return false;
        }
        if (!Senal.gastar(p, Poder.REBOBINAR.coste())) return false;
        ServerLevel l = p.serverLevel();
        long t = l.getGameTime();
        final int pasos = 12;
        final Senal.Paso[] hh = h;
        final int ini = i0;
        Vec3 desde = p.position();
        float vidaVieja = h[i0].vida();
        REBOBINANDO.add(p.getUUID());
        EstaticaRed.a(p, new EstaticaRed.Pantallazo(EstaticaRed.Pantallazo.REBOBINAR, pasos + 4, 0));
        Estatica.sonido(l, desde, EstaticaRegistro.PANTALLA.get(), 1F, 1.8F);
        for (int k = 1; k <= pasos; k++) {
            final int kk = k;
            Estatica.despues(k, () -> {
                if (!p.isAlive() || p.level() != l) {
                    REBOBINANDO.remove(p.getUUID());
                    return;
                }
                // del presente hacia atrás, por el mismo camino
                int idx = hh.length - 1 - Math.round((hh.length - 1 - ini) * (kk / (float) pasos));
                Senal.Paso s = hh[Mth.clamp(idx, ini, hh.length - 1)];
                Vec3 ant = p.position();
                if (Estatica.cabe(l, p, s.pos())) Estatica.mover(p, s.pos(), s.yaw(), s.pitch());
                p.setDeltaMovement(Vec3.ZERO);
                p.hurtMarked = true;
                EstaticaRed.cerca(l, ant.x, ant.y, ant.z, 64, new EstaticaRed.Fx(EstaticaRed.Fx.ESTELA, p.getId(), ant.x, ant.y, ant.z,
                        p.getX(), p.getY(), p.getZ(), Math.round(p.getYRot())));
                if (kk == pasos) {
                    REBOBINANDO.remove(p.getUUID());
                    if (vidaVieja > p.getHealth()) p.setHealth(Math.min(p.getMaxHealth(), vidaVieja));
                    p.clearFire();
                    Estatica.sonido(l, p.position(), EstaticaRegistro.PARPADEO.get(), 1F, 0.7F);
                    Vec3 c = p.getBoundingBox().getCenter();
                    EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, p.getId(), c, 1));
                }
            }, t);
        }
        return true;
    }

    // ================================================================== Sin señal
    private record Zona(ServerPlayer p, ServerLevel l, Vec3 c, double r, long fin) {}

    private static final List<Zona> ZONAS = new ArrayList<>();

    static boolean sinSenal(ServerPlayer p) {
        if (!Senal.gastar(p, Poder.SIN_SENAL.coste())) return false;
        ServerLevel l = p.serverLevel();
        double r = ConfigServidor.estaticaSinSenalRadio();
        int dur = ConfigServidor.estaticaSinSenalTicks();
        Vec3 c = p.position();
        ZONAS.removeIf(z -> z.p == p);
        ZONAS.add(new Zona(p, l, c, r, l.getGameTime() + dur));
        EstaticaRed.cerca(l, c.x, c.y, c.z, 96, new EstaticaRed.Fx(EstaticaRed.Fx.ZONA, p.getId(), c.x, c.y, c.z, dur, 0, 0, (int) Math.round(r * 10)));
        Estatica.sonido(l, c, EstaticaRegistro.APAGON.get(), 1.5F, 0.5F);
        Estatica.sonido(l, c, EstaticaRegistro.ESTALLIDO.get(), 1.2F, 0.5F);
        Luces.apagarCerca(p);
        return true;
    }

    private static boolean dentro(Zona z, LivingEntity e) {
        return e.level() == z.l && e.position().distanceToSqr(z.c) <= z.r * z.r && Math.abs(e.getY() - z.c.y) < z.r;
    }

    /** Multiplicador de daño del hombre de estática dentro de su zona de Sin señal. */
    static float bono(ServerPlayer p) {
        for (Zona z : ZONAS) if (z.p == p && dentro(z, p)) return 1F + ConfigServidor.estaticaSinSenalBono();
        return 1F;
    }

    private static void tickZonas(long t) {
        for (Iterator<Zona> it = ZONAS.iterator(); it.hasNext(); ) {
            Zona z = it.next();
            if (t >= z.fin || !z.p.isAlive() || z.p.isRemoved() || !Estatica.es(z.p)) {
                it.remove();
                continue;
            }
            if (t % 5 != 0) continue;
            boolean el = dentro(z, z.p);
            if (el) Estatica.efecto(z.p, MobEffects.INVISIBILITY, 12, 0, z.p);
            AABB caja = new AABB(z.c, z.c).inflate(z.r);
            for (LivingEntity v : z.l.getEntitiesOfClass(LivingEntity.class, caja, x -> Estatica.objetivoValido(z.p, x) && dentro(z, x))) {
                Estatica.efecto(v, MobEffects.MOVEMENT_SLOWDOWN, 12, 0, z.p);
                if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Pantallazo(EstaticaRed.Pantallazo.SIN_SENAL, 12, 0));
                // los bichos pierden la pista del hombre de estática mientras esté dentro
                if (el && v instanceof Mob m && m.getTarget() == z.p && v.getRandom().nextInt(3) == 0) m.setTarget(null);
            }
            if (t % 40 == 0) Luces.apagarCerca(z.p);
        }
    }

    // ================================================================== Grabación
    private static final class ClonS {
        final ServerPlayer p;
        final ServerLevel l;
        final Senal.Paso[] pasos;
        final long t0;
        final Set<Integer> tocados = new HashSet<>();

        ClonS(ServerPlayer p, ServerLevel l, Senal.Paso[] pasos, long t0) {
            this.p = p;
            this.l = l;
            this.pasos = pasos;
            this.t0 = t0;
        }
    }

    private static final List<ClonS> CLONES = new ArrayList<>();

    static boolean grabacion(ServerPlayer p) {
        Senal.Paso[] h = Senal.historial(p);
        int n = Math.min(60, h.length);
        List<Senal.Paso> l0 = new ArrayList<>();
        for (int i = h.length - n; i < h.length; i++) if (h[i].dim() == p.level().dimension()) l0.add(h[i]);
        if (l0.size() < 20) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.rebobinar_no"));
            return false;
        }
        if (!Senal.gastar(p, Poder.GRABACION.coste())) return false;
        ServerLevel l = p.serverLevel();
        Senal.Paso[] ps = l0.toArray(new Senal.Paso[0]);
        float[] x = new float[ps.length], y = new float[ps.length], z = new float[ps.length], yaw = new float[ps.length];
        boolean[] g = new boolean[ps.length];
        for (int i = 0; i < ps.length; i++) {
            x[i] = (float) ps[i].pos().x;
            y[i] = (float) ps[i].pos().y;
            z[i] = (float) ps[i].pos().z;
            yaw[i] = ps[i].yaw();
            g[i] = ps[i].golpe();
        }
        Vec3 c = ps[0].pos();
        EstaticaRed.cerca(l, c.x, c.y, c.z, 96, new EstaticaRed.Clon(p.getId(), Estatica.semilla(p), x, y, z, yaw, g));
        Estatica.sonido(l, c, EstaticaRegistro.PANTALLA.get(), 1F, 0.6F);
        CLONES.add(new ClonS(p, l, ps, l.getGameTime()));
        return true;
    }

    private static void tickClones(long t) {
        for (Iterator<ClonS> it = CLONES.iterator(); it.hasNext(); ) {
            ClonS cl = it.next();
            int i = (int) (t - cl.t0);
            if (!cl.p.isAlive() || cl.p.level() != cl.l || i >= cl.pasos.length) {
                it.remove();
                if (cl.p.isAlive() && cl.p.level() == cl.l) {
                    // al acabar, el clon revienta
                    Vec3 c = cl.pasos[cl.pasos.length - 1].pos().add(0, 1, 0);
                    EstaticaRed.cerca(cl.l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, cl.p.getId(), c, 1));
                    Estatica.sonido(cl.l, c, EstaticaRegistro.ESTALLIDO.get(), 1F, 1.1F);
                    for (LivingEntity v : cl.l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(2.5), x -> Estatica.objetivoValido(cl.p, x)))
                        if (Estatica.golpear(cl.p, v, ConfigServidor.estaticaGrabacionDano() * 0.7F)) Estatica.interferir(cl.p, v, 1);
                }
                continue;
            }
            Senal.Paso s = cl.pasos[i];
            AABB cuerpo = new AABB(s.pos().x - 0.4, s.pos().y, s.pos().z - 0.4, s.pos().x + 0.4, s.pos().y + 1.8, s.pos().z + 0.4).inflate(0.3);
            float d = ConfigServidor.estaticaGrabacionDano();
            for (LivingEntity v : cl.l.getEntitiesOfClass(LivingEntity.class, cuerpo, x -> Estatica.objetivoValido(cl.p, x))) {
                if (!cl.tocados.add(v.getId())) continue;
                if (Estatica.golpear(cl.p, v, d)) {
                    Estatica.interferir(cl.p, v, 1);
                    Estatica.empujar(v, v.position().subtract(s.pos()), 0.6, 0.3);
                    if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(12, false, 0));
                }
            }
            if (s.golpe()) {
                // repite el puñetazo: delante, a un par de bloques
                float yw = s.yaw() * Mth.DEG_TO_RAD;
                Vec3 fr = new Vec3(-Mth.sin(yw), 0, Mth.cos(yw));
                Vec3 punto = s.pos().add(0, 1.2, 0).add(fr.scale(1.4));
                for (LivingEntity v : cl.l.getEntitiesOfClass(LivingEntity.class, new AABB(punto, punto).inflate(1.3), x -> Estatica.objetivoValido(cl.p, x))) {
                    v.invulnerableTime = 0;
                    if (Estatica.golpear(cl.p, v, d)) {
                        Estatica.interferir(cl.p, v, 1);
                        Estatica.empujar(v, fr, 0.8, 0.3);
                    }
                }
                EstaticaRed.cerca(cl.l, punto.x, punto.y, punto.z, 48, EstaticaRed.Fx.en(EstaticaRed.Fx.CHISPAZO, cl.p.getId(), punto, 1));
                Estatica.sonido(cl.l, punto, EstaticaRegistro.CHISPA_SONIDO.get(), 0.9F, 0.8F);
            }
        }
    }

    // ================================================================== ticks y limpieza
    static void tick(MinecraftServer srv) {
        long t = srv.overworld().getGameTime();
        if (!CANALES.isEmpty()) tickCanales(t);
        if (!PAUSAS.isEmpty()) tickPausas(t);
        if (!ZONAS.isEmpty()) tickZonas(t);
        if (!CLONES.isEmpty()) tickClones(t);
    }

    /** Al irse o morir el jugador: suelta a sus víctimas y quita lo suyo. */
    static void soltarDe(ServerPlayer p) {
        for (Iterator<EnCanal> it = CANALES.iterator(); it.hasNext(); ) {
            EnCanal c = it.next();
            if (c.v == p) it.remove();
            else if (c.atk == p) {
                it.remove();
                escupir(c);
            }
        }
        for (Iterator<Pausado> it = PAUSAS.iterator(); it.hasNext(); ) {
            Pausado x = it.next();
            if (x.atk == p || x.v == p) it.remove();
        }
        ZONAS.removeIf(z -> z.p == p);
        CLONES.removeIf(c -> c.p == p);
        REBOBINANDO.remove(p.getUUID());
    }

    static void limpiar() {
        CANALES.clear();
        PAUSAS.clear();
        ZONAS.clear();
        CLONES.clear();
        REBOBINANDO.clear();
    }
}
