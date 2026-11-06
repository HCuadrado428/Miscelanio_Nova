package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Los poderes de la rueda, en el servidor (todo se vuelve a comprobar aquí: efecto, configuración, enfriamiento, distancias). */
public final class Poderes {
    private Poderes() {}

    public static void usar(ServerPlayer p, Poder pd, BlockPos pos, int objetivo) {
        if (!Poder.RUEDA.contains(pd) || pd == Poder.IMITAR || !Estatica.es(p) || !p.isAlive() || Estatica.enCinematica(p) || PantallasServidor.dentro(p)) return;
        if (!pd.activo()) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.desactivado"));
            return;
        }
        if (!Estatica.listo(p, pd)) return;
        if (pd == Poder.PARPADEO && cargas(p) <= 0) {
            long falta = p.getPersistentData().getLong(CARGA_T) - p.level().getGameTime();
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.espera", Component.translatable(pd.clave()), String.format("%.1f", Math.max(0, falta) / 20F)));
            return;
        }
        if (pd == Poder.SOBRECARGA && Senal.de(p) < Senal.MAX) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.sobrecarga_falta", Math.round(Senal.de(p))));
            return;
        }
        if (Tentaculos.sosteniendo(p)) return;
        Estatica.despertar(p);
        boolean hecho = switch (pd) {
            case PARPADEO -> parpadeo(p);
            case SOBRECARGA -> sobrecarga(p);
            case PANTALLA -> PantallasServidor.entrar(p, pos);
            case CANAL -> PoderesNuevos.canal(p, objetivo);
            case TENTACULOS -> tentaculos(p, objetivo);
            case PAUSA -> PoderesNuevos.pausa(p, objetivo);
            case REBOBINAR -> PoderesNuevos.rebobinar(p);
            case SIN_SENAL -> PoderesNuevos.sinSenal(p);
            case GRABACION -> PoderesNuevos.grabacion(p);
            default -> false;
        };
        if (hecho) Estatica.gastar(p, pd);
        if (hecho && pd == Poder.PARPADEO) gastarCarga(p);
    }

    // ------------------------------------------------------------------ cargas del parpadeo
    private static final String CARGAS = "MiscEstCargas", CARGA_T = "MiscEstCargaT";

    /** Cargas de parpadeo que le quedan (se recarga una cada 'parpadeo_enfriamiento_segundos'). */
    public static int cargas(ServerPlayer p) {
        var d = p.getPersistentData();
        int max = ConfigServidor.estaticaParpadeoCargas();
        long t = p.level().getGameTime(), enf = Math.max(1, ConfigServidor.estaticaParpadeoEnfriamiento());
        int c = d.contains(CARGAS) ? Math.min(max, d.getInt(CARGAS)) : max;
        long nx = d.getLong(CARGA_T);
        if (nx > t + enf * (long) max) nx = t + enf;      // por si cambió el reloj del mundo
        while (c < max && nx <= t) {
            c++;
            nx += enf;
        }
        if (c >= max) nx = 0;
        d.putInt(CARGAS, c);
        d.putLong(CARGA_T, nx);
        return c;
    }

    private static void gastarCarga(ServerPlayer p) {
        int c = cargas(p);
        var d = p.getPersistentData();
        d.putInt(CARGAS, Math.max(0, c - 1));
        if (d.getLong(CARGA_T) == 0) d.putLong(CARGA_T, p.level().getGameTime() + Math.max(1, ConfigServidor.estaticaParpadeoEnfriamiento()));
    }

    // ------------------------------------------------------------------ parpadeo
    private static boolean parpadeo(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 mira = p.getLookAngle();
        Vec3 dir = new Vec3(mira.x, Math.max(-0.45, Math.min(0.6, mira.y)), mira.z);
        Vec3 desde = p.position();
        Vec3 dest = Estatica.libre(l, p, desde, dir, ConfigServidor.estaticaParpadeoDistancia());
        if (dest.distanceToSqr(desde) < 1.0) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.no_cabe"));
            return false;
        }
        // cadena: el tercer parpadeo seguido (en 2 s) hace el doble de daño
        var dat = p.getPersistentData();
        long ahora = l.getGameTime();
        int cadena = ahora - dat.getLong("MiscEstCadenaT") <= 40 ? dat.getInt("MiscEstCadena") + 1 : 1;
        boolean fuerte = cadena >= 3;
        dat.putInt("MiscEstCadena", fuerte ? 0 : cadena);
        dat.putLong("MiscEstCadenaT", ahora);
        float danoPaso = ConfigServidor.estaticaParpadeoDano() * (fuerte ? 2F : 1F);
        // quien esté en el camino recibe la descarga y sale despedido
        Vec3 paso = dest.subtract(desde);
        AABB caja = p.getBoundingBox().minmax(p.getBoundingBox().move(paso)).inflate(0.4);
        for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, caja, x -> Estatica.objetivoValido(p, x))) {
            Vec3 c = v.getBoundingBox().getCenter();
            if (v.getBoundingBox().inflate(0.6).clip(desde.add(0, 0.9, 0), dest.add(0, 0.9, 0)).isEmpty()
                    && v.getBoundingBox().inflate(0.6).clip(desde.add(0, 0.2, 0), dest.add(0, 0.2, 0)).isEmpty()) continue;
            if (Estatica.golpear(p, v, danoPaso)) {
                Estatica.interferir(p, v, 1);
                Vec3 lado = c.subtract(desde);
                Vec3 perp = lado.subtract(paso.normalize().scale(lado.dot(paso.normalize())));
                Estatica.empujar(v, paso.normalize().scale(0.5).add(perp.lengthSqr() < 1.0E-4 ? Vec3.ZERO : perp.normalize()), ConfigServidor.estaticaParpadeoEmpuje(), 0.35);
                if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(14, false, 0));
            }
            EstaticaRed.cerca(l, c.x, c.y, c.z, 48, EstaticaRed.Fx.en(EstaticaRed.Fx.CHISPAZO, v.getId(), c, fuerte ? 1 : 0));
        }
        // la silueta que deja atrás se queda un segundo y revienta
        float ecoDano = ConfigServidor.estaticaEcoDano();
        if (ecoDano > 0) {
            EstaticaRed.cerca(l, desde.x, desde.y, desde.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ECO, p.getId(), desde, Math.round(p.yBodyRot)));
            Estatica.despues(20, () -> {
                if (!p.isAlive() || p.level() != l) return;
                Vec3 ce = desde.add(0, 1, 0);
                EstaticaRed.cerca(l, ce.x, ce.y, ce.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, p.getId(), ce, 1));
                Estatica.sonido(l, ce, EstaticaRegistro.ESTALLIDO.get(), 0.9F, 1.4F);
                for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, new AABB(ce, ce).inflate(2.5), x -> Estatica.objetivoValido(p, x))) {
                    v.invulnerableTime = 0;
                    if (Estatica.golpear(p, v, ecoDano)) {
                        Estatica.interferir(p, v, 1);
                        Estatica.empujar(v, v.position().subtract(desde), 0.7, 0.35);
                    }
                }
            }, ahora);
        }
        if (fuerte) {
            Vec3 cd = dest.add(0, 1, 0);
            EstaticaRed.cerca(l, cd.x, cd.y, cd.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.CHISPAZO, p.getId(), cd, 1));
        }
        Estatica.mover(p, dest, p.getYRot(), p.getXRot());
        p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3));
        p.hurtMarked = true;
        EstaticaRed.cerca(l, desde.x, desde.y, desde.z, 64, new EstaticaRed.Fx(EstaticaRed.Fx.ESTELA, p.getId(), desde.x, desde.y, desde.z, dest.x, dest.y, dest.z, Math.round(p.yBodyRot)));
        Estatica.sonido(l, desde, EstaticaRegistro.PARPADEO.get(), 1F, 1F);
        Estatica.sonido(l, dest, EstaticaRegistro.CRUJIDO.get(), 0.6F, 1.2F);
        return true;
    }

    // ------------------------------------------------------------------ tentáculos
    private static boolean tentaculos(ServerPlayer p, int pista) {
        Poder pd = Poder.TENTACULOS;
        double alc = ConfigServidor.estaticaTentaculosAlcance();
        LivingEntity v = Estatica.mirado(p, alc, pista);
        if (v == null || !Estatica.agarrable(p, v)) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.sin_objetivo"));
            return false;
        }
        List<LivingEntity> vs = new ArrayList<>();
        vs.add(v);
        int n = 1;
        if (n > 1) {
            // los de alrededor del objetivo (a la vista y al alcance)
            ServerLevel l = p.serverLevel();
            Vec3 ojo = p.getEyePosition();
            List<LivingEntity> otros = l.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(6), x -> x != v && Estatica.agarrable(p, x)
                    && !Tentaculos.agarrado(x) && x.distanceTo(p) <= alc + 1 && l.clip(new ClipContext(ojo, x.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS);
            otros.sort(Comparator.comparingDouble(x -> x.distanceToSqr(v)));
            for (int i = 0; i < otros.size() && vs.size() < n; i++) vs.add(otros.get(i));
        }
        return Tentaculos.agarrar(p, vs, pd);
    }

    // ------------------------------------------------------------------ sobrecarga
    private static boolean sobrecarga(ServerPlayer p) {
        if (!Senal.gastar(p, Senal.MAX)) return false;
        ServerLevel l = p.serverLevel();
        double r = ConfigServidor.estaticaSobrecargaRadio();
        Vec3 c = p.position().add(0, 0.9, 0);
        int n = 0;
        for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), x -> Estatica.objetivoValido(p, x))) {
            Vec3 vc = v.getBoundingBox().getCenter();
            double d = vc.distanceTo(c);
            if (d > r + v.getBbWidth() * 0.5) continue;
            // no atraviesa paredes (se prueba a los ojos y al centro)
            if (l.clip(new ClipContext(c, vc, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS
                    && l.clip(new ClipContext(p.getEyePosition(), v.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS)
                continue;
            float k = (float) (1 - 0.5 * Math.min(1, d / r));
            if (Estatica.golpear(p, v, ConfigServidor.estaticaSobrecargaDano() * k)) n++;
            Estatica.efecto(v, MobEffects.BLINDNESS, ConfigServidor.estaticaSobrecargaCegueraTicks(), 0, p);
            Estatica.interferir(p, v, 3);
            Estatica.efecto(v, MobEffects.DARKNESS, 120, 0, p);
            Estatica.empujar(v, vc.subtract(c), ConfigServidor.estaticaSobrecargaEmpuje() * (0.6 + 0.4 * k), 0.55 + 0.45 * k);
            if (v instanceof ServerPlayer vp) {
                EstaticaRed.a(vp, new EstaticaRed.Nieve(30, true, ConfigServidor.estaticaToqueCorromperTicks()));
                EstaticaRed.a(vp, new EstaticaRed.Pantallazo(EstaticaRed.Pantallazo.SIN_SENAL, 80, 1));
            }
        }
        EstaticaRed.cerca(l, c.x, c.y, c.z, 96, EstaticaRed.Fx.en(EstaticaRed.Fx.NOVA, p.getId(), c, (int) Math.round(r * 10)));
        Estatica.sonido(l, c, EstaticaRegistro.ESTALLIDO.get(), 2F, 0.55F);
        Estatica.sonido(l, c, EstaticaRegistro.GOLPE.get(), 1.4F, 0.7F);
        Estatica.sonido(l, c, EstaticaRegistro.CHISPA_SONIDO.get(), 1.5F, 0.6F);
        Luces.apagarCerca(p, (int) Math.min(14, Math.round(r)));
        // réplica: una segunda onda más pequeña un instante después
        Estatica.despues(12, () -> {
            if (!p.isAlive() || p.level() != l) return;
            Vec3 c2 = p.position().add(0, 0.9, 0);
            EstaticaRed.cerca(l, c2.x, c2.y, c2.z, 96, EstaticaRed.Fx.en(EstaticaRed.Fx.NOVA, p.getId(), c2, (int) Math.round(r * 6)));
            Estatica.sonido(l, c2, EstaticaRegistro.ESTALLIDO.get(), 1.4F, 0.8F);
            for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r * 0.6), x -> Estatica.objetivoValido(p, x))) {
                v.invulnerableTime = 0;
                if (Estatica.golpear(p, v, ConfigServidor.estaticaSobrecargaDano() * 0.35F)) Estatica.empujar(v, v.position().subtract(c2), 0.8, 0.5);
            }
        }, l.getGameTime());
        return true;
    }

    // ------------------------------------------------------------------ imitar voces
    public static void imitar(ServerPlayer p, BlockPos pos, byte[] datos) {
        if (!Estatica.es(p) || !Poder.IMITAR.activo() || datos.length < 800 || datos.length > EstaticaRed.MAX_GRABACION || Estatica.enCinematica(p)) return;
        ServerLevel l = p.serverLevel();
        if (!l.isLoaded(pos) || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 32 * 32) return;
        if (!Estatica.listo(p, Poder.IMITAR)) return;
        Estatica.gastar(p, Poder.IMITAR);
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        EstaticaRed.cerca(l, x, y, z, 40, new EstaticaRed.Imitar(x, y, z, datos));
        // y también por las teles y recreativas de alrededor (hasta 3)
        int n = 0;
        for (BlockPos bp : PantallasServidor.buscar(l, pos, 16)) {
            if (n++ >= 3) break;
            double sx = bp.getX() + 0.5, sy = bp.getY() + 1.0, sz = bp.getZ() + 0.5;
            EstaticaRed.cerca(l, sx, sy, sz, 40, new EstaticaRed.Imitar(sx, sy, sz, datos));
        }
    }
}
