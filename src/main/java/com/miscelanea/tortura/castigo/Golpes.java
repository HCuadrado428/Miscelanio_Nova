package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.heridas.Herida;
import com.miscelanea.tortura.heridas.Parte;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Dónde ha dado un impacto en el cuerpo (pieza del modelo, cara del cubo y posición dentro de la cara, 0..255).
 * Es el mismo reparto que usan las heridas (Heridas.desdePunto, que es privado del paquete), copiado aquí para no tocarlo.
 */
public final class Golpes {
    private Golpes() {}

    public record Golpe(Parte parte, int cara, int fx, int fy) {}

    static int caraDesde(LivingEntity p, Vec3 desde, Parte parte) {
        double yaw = Math.toRadians(p.yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), derX = -Math.cos(yaw), derZ = -Math.sin(yaw);
        Vec3 dir = desde.subtract(p.position());
        double df = dir.x * fwdX + dir.z * fwdZ, dl = dir.x * derX + dir.z * derZ;
        int cara;
        if (Math.abs(df) >= Math.abs(dl)) cara = df >= 0 ? Herida.FRENTE : Herida.ESPALDA;
        else cara = dl >= 0 ? Herida.DERECHA : Herida.IZQUIERDA;
        if (parte.esExtremidad() && ((cara == Herida.DERECHA && !parte.derecha()) || (cara == Herida.IZQUIERDA && parte.derecha())))
            cara = Herida.FRENTE;
        return cara;
    }

    /** A partir del punto de impacto (en el mundo) y de dónde venía. */
    public static Golpe desdePunto(LivingEntity p, Vec3 punto, Vec3 desde) {
        RandomSource r = p.getRandom();
        double H = Math.max(0.1, p.getBbHeight());
        double medio = Math.max(0.1, p.getBbWidth() * 0.5);
        double yaw = Math.toRadians(p.yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), derX = -Math.cos(yaw), derZ = -Math.sin(yaw);
        Vec3 rel = punto.subtract(p.position());
        double lx = Mth.clamp((rel.x * derX + rel.z * derZ) / medio, -1, 1);
        double lf = Mth.clamp((rel.x * fwdX + rel.z * fwdZ) / medio, -1, 1);
        double h = Mth.clamp(rel.y / H, 0, 1);
        if (H < 1.0) {
            Parte parte = Parte.V[r.nextInt(Parte.V.length)];
            return new Golpe(parte, caraDesde(p, desde, parte), r.nextInt(256), r.nextInt(256));
        }
        Parte parte;
        double fy;
        if (h < 0.39) {
            parte = lx > 0 ? Parte.PIERNA_DER : lx < 0 ? Parte.PIERNA_IZQ : (r.nextBoolean() ? Parte.PIERNA_DER : Parte.PIERNA_IZQ);
            fy = 1 - h / 0.39;
        } else if (h < 0.76) {
            parte = Math.abs(lx) > 0.55 ? (lx > 0 ? Parte.BRAZO_DER : Parte.BRAZO_IZQ) : Parte.TORSO;
            fy = (0.76 - h) / 0.37;
        } else {
            parte = Parte.CABEZA;
            fy = (1 - h) / 0.24;
        }
        int cara = caraDesde(p, desde, parte);
        double fx;
        if (parte.esExtremidad()) fx = r.nextDouble();
        else if (cara == Herida.FRENTE) fx = 0.5 - lx / 1.1;
        else if (cara == Herida.ESPALDA) fx = 0.5 + lx / 1.1;
        else if (cara == Herida.DERECHA) fx = 0.5 + lf * 0.5;
        else fx = 0.5 - lf * 0.5;
        return new Golpe(parte, cara, (int) (Mth.clamp(fx, 0, 1) * 255), (int) (Mth.clamp(fy, 0, 1) * 255));
    }

    /** ¿Corta el segmento a-b a la esfera (c, r)? Devuelve la fracción del segmento (0..1) o -1. */
    public static double esfera(Vec3 a, Vec3 b, Vec3 c, double r) {
        Vec3 d = b.subtract(a);
        double l2 = d.lengthSqr();
        double t = l2 < 1.0E-9 ? 0 : Mth.clamp(c.subtract(a).dot(d) / l2, 0, 1);
        Vec3 q = a.add(d.scale(t));
        return q.distanceToSqr(c) <= r * r ? t : -1;
    }
}
