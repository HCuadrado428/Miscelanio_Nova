package com.miscelanea.tirolina;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Forma del cable entre dos postes: recta que cuelga en el centro (parábola). La usan la física y el dibujo. */
public final class Cable {
    private Cable() {}

    public static Vec3 anclaje(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 30.4 / 16, p.getZ() + 0.5);
    }

    public static double caida(Vec3 a, Vec3 b) {
        return Math.min(3.0, a.distanceTo(b) * 0.045);
    }

    public static Vec3 punto(Vec3 a, Vec3 b, double t) {
        double s = caida(a, b);
        return a.lerp(b, t).subtract(0, s * 4 * t * (1 - t), 0);
    }

    public static Vec3 tangente(Vec3 a, Vec3 b, double t) {
        double s = caida(a, b);
        return b.subtract(a).subtract(0, s * 4 * (1 - 2 * t), 0);
    }
}
