package com.miscelanea.mesas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Paso entre coordenadas de la mesa (u a lo largo, v a lo ancho, en bloques desde la esquina de la parte principal) y el mundo. */
public final class MesaGeo {
    private MesaGeo() {}

    /** Punto local (relativo a la esquina del bloque principal) de (u, v) a la altura y. */
    public static Vec3 local(Direction f, double u, double v, double y) {
        Direction der = f.getClockWise();
        return new Vec3(0.5 + der.getStepX() * (u - 0.5) + f.getStepX() * (v - 0.5), y, 0.5 + der.getStepZ() * (u - 0.5) + f.getStepZ() * (v - 0.5));
    }

    /** {u, v} de un punto del mundo. */
    public static double[] campo(BlockPos principal, Direction f, Vec3 w) {
        Direction der = f.getClockWise();
        double dx = w.x - principal.getX() - 0.5, dz = w.z - principal.getZ() - 0.5;
        return new double[]{dx * der.getStepX() + dz * der.getStepZ() + 0.5, dx * f.getStepX() + dz * f.getStepZ() + 0.5};
    }

    /** Giro (radianes, sobre Y) que lleva el eje +X local a la dirección (du, dv) de la mesa. */
    public static float giro(Direction f, double du, double dv) {
        Direction der = f.getClockWise();
        double dx = der.getStepX() * du + f.getStepX() * dv, dz = der.getStepZ() * du + f.getStepZ() * dv;
        return (float) Math.atan2(-dz, dx);
    }
}
