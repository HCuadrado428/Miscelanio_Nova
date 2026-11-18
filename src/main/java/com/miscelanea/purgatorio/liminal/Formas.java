package com.miscelanea.purgatorio.liminal;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Formas de bloque dibujadas mirando al norte y giradas a cada lado (como los modelos con su giro en el estado). */
final class Formas {
    private Formas() {}

    /** Cajas en px (x0, y0, z0, x1, y1, z1, ...) mirando al norte -> las cuatro formas (norte, sur, oeste, este). */
    static VoxelShape[] cuatro(double... c) {
        VoxelShape[] out = new VoxelShape[4];
        Direction[] ds = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int k = 0; k < 4; k++) {
            VoxelShape s = Shapes.empty();
            for (int i = 0; i + 5 < c.length; i += 6) s = Shapes.or(s, girar(c[i], c[i + 1], c[i + 2], c[i + 3], c[i + 4], c[i + 5], ds[k]));
            out[k] = s.optimize();
        }
        return out;
    }

    static int indice(Direction d) {
        return switch (d) {
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /** Como el giro "y" de los estados de bloque: este = 90, sur = 180, oeste = 270 (en el sentido de las agujas del reloj visto desde arriba). */
    static VoxelShape girar(double x0, double y0, double z0, double x1, double y1, double z1, Direction d) {
        return switch (d) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }
}
