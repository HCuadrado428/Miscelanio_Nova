package com.miscelanea.aspira;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Geometría común: dónde está la boca de la boquilla, qué hay dentro del cono, etc. */
public final class AspiraUtil {
    private AspiraUtil() {}

    /** Punto aproximado de la boca de la boquilla (un poco delante y debajo de los ojos). */
    public static Vec3 boca(Player player) {
        Vec3 look = player.getViewVector(1.0F);
        return player.getEyePosition().add(look.scale(0.9)).add(derecha(player).scale(0.25)).add(0, -0.3, 0);
    }

    /** Vector unitario hacia la derecha del jugador (horizontal). */
    public static Vec3 derecha(Player player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 r = look.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1.0E-4) {
            // mirando justo arriba o abajo: usar el yaw
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        return r.normalize();
    }

    public static Vec3 centro(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /**
     * Entidades de una clase dentro del cono de la boquilla.
     *
     * @param alcance distancia máxima en bloques
     * @param cosCono coseno del semiángulo del cono (0.82 ≈ 35°)
     */
    public static <T extends Entity> List<T> enCono(Player player, Class<T> clase, double alcance, double cosCono) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        return player.level().getEntitiesOfClass(clase, player.getBoundingBox().inflate(alcance), e -> {
            if (e == player || !e.isAlive() || e.isSpectator()) return false;
            if (e instanceof Player p && (p.isCreative())) return false;
            Vec3 d = centro(e).subtract(eye);
            double dist = d.length();
            if (dist > alcance || dist < 1.0E-3) return false;
            if (d.scale(1.0 / dist).dot(look) < cosCono) return false;
            return lineaDeVision(player, centro(e));
        });
    }

    public static boolean lineaDeVision(Player player, Vec3 punto) {
        Vec3 eye = player.getEyePosition();
        HitResult hit = player.level().clip(new ClipContext(eye, punto, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Partículas que viajan de "desde" hacia "hasta" (count = 0 hace que dx/dy/dz sean la velocidad). */
    public static void chorro(ServerLevel level, ParticleOptions tipo, Vec3 desde, Vec3 hasta, double velocidad) {
        Vec3 v = hasta.subtract(desde).normalize();
        level.sendParticles(tipo, desde.x, desde.y, desde.z, 0, v.x, v.y, v.z, velocidad);
    }

    /** Bocanada de humo (textura de fogata) que viaja de "desde" a "hasta" acelerando: aspiración. */
    public static void aire(ServerLevel level, Vec3 desde, Vec3 hasta, boolean aspirar) {
        Vec3 d = hasta.subtract(desde);
        level.sendParticles(aspirar ? com.miscelanea.registry.ModParticles.ASPIRACION.get()
                        : com.miscelanea.registry.ModParticles.SOPLIDO.get(),
                desde.x, desde.y, desde.z, 0, d.x, d.y, d.z, 1.0);
    }

    /** Aplica una velocidad a una entidad y fuerza a que se sincronice con el cliente (necesario en jugadores). */
    public static void empujar(Entity e, Vec3 velocidad) {
        e.setDeltaMovement(velocidad);
        e.hurtMarked = true;
        if (e instanceof LivingEntity) {
            e.fallDistance = 0.0F;
        }
    }
}
