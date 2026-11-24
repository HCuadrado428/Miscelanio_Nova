package com.miscelanea.camaras;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cómo se cuelgan las cámaras y las teles. Los modelos están dibujados mirando al norte (el frente en z = 0 y la pared en
 * z = 16); aquí se pasa un punto de ese modelo (en 0..16) a la dirección en que está puesto el bloque, con el mismo giro que
 * pone el blockstate (y = 90 por cuarto de vuelta, x = 90 mirando abajo). Lo usan las formas y el dibujado de la pantalla.
 */
public final class Montaje {
    private Montaje() {}

    /** Hacia dónde mira el frente: los cuatro lados de una pared o hacia abajo (techo). Nunca hacia arriba. */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", d -> d != Direction.UP);

    /** Punto (x, y, z) del modelo mirando al norte → punto del bloque puesto mirando a {@code d}. */
    public static double[] mapa(Direction d, double x, double y, double z) {
        return switch (d) {
            case SOUTH -> new double[]{16 - x, y, 16 - z};
            case EAST -> new double[]{16 - z, y, x};
            case WEST -> new double[]{z, y, 16 - x};
            case UP -> new double[]{x, 16 - z, y};
            case DOWN -> new double[]{x, z, 16 - y};
            default -> new double[]{x, y, z};
        };
    }

    /** Caja del modelo mirando al norte → forma del bloque puesto mirando a {@code d}. */
    public static VoxelShape forma(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] a = mapa(d, x0, y0, z0), b = mapa(d, x1, y1, z1);
        return Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }
}
