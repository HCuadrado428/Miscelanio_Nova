package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Los poderes de la rueda, en el servidor (todo se vuelve a comprobar aquí: efecto, configuración, enfriamiento, distancias). */
public final class Poderes {
    private Poderes() {}

    public static void usar(ServerPlayer p, Poder pd, BlockPos pos, int objetivo) {
        if (pd == Poder.NINGUNO || pd == Poder.IMITAR || !Estatica.es(p) || !p.isAlive() || Estatica.enCinematica(p) || PantallasServidor.dentro(p)) return;
        if (!pd.activo()) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.desactivado"));
            return;
        }
        if (!Estatica.listo(p, pd)) return;
        Estatica.despertar(p);
        boolean hecho = switch (pd) {
            case PARPADEO -> parpadeo(p);
            case PANTALLA -> PantallasServidor.entrar(p, pos);
            case ARRASTRAR -> arrastrar(p, objetivo);
            case ATRAER, LANZAR, ESTAMPAR -> tentaculos(p, pd, objetivo);
            default -> false;
        };
        if (hecho) Estatica.gastar(p, pd);
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
        Estatica.mover(p, dest, p.getYRot(), p.getXRot());
        p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3));
        p.hurtMarked = true;
        EstaticaRed.cerca(l, desde.x, desde.y, desde.z, 64, new EstaticaRed.Fx(EstaticaRed.Fx.ESTELA, p.getId(), desde.x, desde.y, desde.z, dest.x, dest.y, dest.z, Math.round(p.yBodyRot)));
        Estatica.sonido(l, desde, EstaticaRegistro.PARPADEO.get(), 1F, 1F);
        Estatica.sonido(l, dest, EstaticaRegistro.CRUJIDO.get(), 0.6F, 1.2F);
        return true;
    }

    // ------------------------------------------------------------------ arrastrar a la estática
    private static boolean arrastrar(ServerPlayer p, int pista) {
        LivingEntity v = Estatica.mirado(p, ConfigServidor.estaticaArrastrarAlcance(), pista);
        if (v == null) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.sin_objetivo"));
            return false;
        }
        ServerLevel l = p.serverLevel();
        long t = l.getGameTime();
        Vec3 a = p.position(), b = v.position();
        // los dos se deshacen en estática
        for (var e : new LivingEntity[]{p, v}) {
            Vec3 c = e.getBoundingBox().getCenter();
            EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.OCULTAR, e.getId(), c, 9));
            EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, e.getId(), c, 1));
        }
        Estatica.sonido(l, a, EstaticaRegistro.ESTALLIDO.get(), 0.8F, 1.3F);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 6, false, false, false), p);
        Estatica.despues(8, () -> {
            if (!p.isAlive() || !v.isAlive() || v.level() != l || p.level() != l) return;
            Vec3 mira = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
            Vec3 dest = Estatica.libre(l, p, p.position(), mira, ConfigServidor.estaticaArrastrarDistancia());
            Vec3 lado = new Vec3(-mira.z, 0, mira.x);
            lado = lado.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : lado.normalize();
            Vec3 dv = dest.add(lado.scale(1.1));
            if (!Estatica.cabe(l, v, dv)) dv = dest.add(lado.scale(-1.1));
            if (!Estatica.cabe(l, v, dv)) dv = dest.add(mira.normalize().scale(1.2));
            if (!Estatica.cabe(l, v, dv)) dv = dest;
            Estatica.mover(p, dest, p.getYRot(), p.getXRot());
            Estatica.mover(v, dv, v.getYRot(), v.getXRot());
            v.setDeltaMovement(Vec3.ZERO);
            v.hurtMarked = true;
            // aturdido al aparecer
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 4), p);
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), p);
            v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 1), p);
            for (var e : new LivingEntity[]{p, v}) {
                Vec3 c = e.getBoundingBox().getCenter();
                EstaticaRed.cerca(l, c.x, c.y, c.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, e.getId(), c, 1));
            }
            if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(25, false, 0));
            Estatica.sonido(l, dest, EstaticaRegistro.ESTALLIDO.get(), 0.9F, 0.9F);
        }, t);
        return true;
    }

    // ------------------------------------------------------------------ tentáculos
    private static boolean tentaculos(ServerPlayer p, Poder pd, int pista) {
        LivingEntity v = Estatica.mirado(p, ConfigServidor.estaticaTentaculosAlcance(), pista);
        if (v == null) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.sin_objetivo"));
            return false;
        }
        return Tentaculos.agarrar(p, v, pd);
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
    }
}
