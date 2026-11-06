package com.miscelanea.laser;

import com.miscelanea.ConfigServidor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Lo común del puntero láser (cliente y servidor): colores y el rayo, con el mismo temblor de pulso en todos lados. */
public final class Laser {
    private Laser() {}

    public static final String COLOR = "ColorLaser";
    /** Rojo, verde, azul, morado (RGB). */
    public static final int[] COLORES = {0xFF2A1E, 0x3BFF3B, 0x3E7BFF, 0xC348FF};
    public static final String[] NOMBRES = {"rojo", "verde", "azul", "morado"};

    public static int color(ItemStack s) {
        return s.hasTag() ? Mth.clamp(s.getTag().getInt(COLOR), 0, COLORES.length - 1) : 0;
    }

    public static int rgb(ItemStack s) {
        return COLORES[color(s)];
    }

    /** Índice de color para un tinte, o -1 si ese tinte no vale. */
    public static int colorDeTinte(DyeColor d) {
        return switch (d) {
            case RED -> 0;
            case LIME, GREEN -> 1;
            case BLUE, LIGHT_BLUE, CYAN -> 2;
            case PURPLE, MAGENTA -> 3;
            default -> -1;
        };
    }

    /** Quien está apuntando con el puntero (manteniendo el clic derecho). */
    public static boolean apuntando(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().getItem() instanceof PunteroLaserItem;
    }

    public static double alcance() {
        return ConfigServidor.laserAlcance();
    }

    /** Dirección del rayo: la mirada más un temblor pequeño de la mano (igual en servidor y clientes). */
    public static Vec3 direccion(LivingEntity e, float pt) {
        double t = e.level().getGameTime() + pt;
        int id = e.getId();
        float jx = (float) (0.11 * (Math.sin(t * 0.53 + id) + 0.6 * Math.sin(t * 1.37 + id * 2.1) + 0.3 * Math.sin(t * 3.1 + id * 0.7)));
        float jy = (float) (0.11 * (Math.sin(t * 0.47 + id * 1.3) + 0.6 * Math.sin(t * 1.61 + id * 0.4) + 0.3 * Math.sin(t * 2.9 + id * 1.9)));
        return Vec3.directionFromRotation(e.getViewXRot(pt) + jx, e.getViewYRot(pt) + jy);
    }

    /** Dónde acaba el rayo. */
    public record Impacto(Vec3 desde, Vec3 punto, @Nullable Vec3 normal, @Nullable Entity entidad) {
        public boolean toca() {
            return normal != null || entidad != null;
        }
    }

    public static Impacto rayo(LivingEntity e, float pt) {
        Level l = e.level();
        Vec3 ojo = e.getEyePosition(pt);
        Vec3 dir = direccion(e, pt);
        Vec3 fin = ojo.add(dir.scale(alcance()));
        BlockHitResult bh = l.clip(new ClipContext(ojo, fin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
        Vec3 normal = null;
        if (bh.getType() != HitResult.Type.MISS) {
            fin = bh.getLocation();
            normal = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
        }
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(e, ojo, fin, new AABB(ojo, fin).inflate(1.0),
                x -> x != e && !x.isSpectator() && x.isPickable() && !x.isInvisible(), ojo.distanceToSqr(fin));
        if (eh != null) return new Impacto(ojo, eh.getLocation(), null, eh.getEntity());
        return new Impacto(ojo, fin, normal, null);
    }
}
