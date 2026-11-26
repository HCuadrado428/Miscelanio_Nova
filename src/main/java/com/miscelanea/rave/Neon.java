package com.miscelanea.rave;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Colores de neón (más vivos que los del tinte normal: el negro es "luz negra", violeta) y giro de los modelos que miran hacia
 * arriba (tubos y paneles) a la cara en que se ponen, igual que el blockstate (x = 90 de lado, x = 180 en el techo).
 */
public final class Neon {
    private Neon() {}

    private static final int[] RGB = new int[16];

    static {
        put(DyeColor.WHITE, 0xF4F8FF);
        put(DyeColor.ORANGE, 0xFF8A1E);
        put(DyeColor.MAGENTA, 0xFF3CF0);
        put(DyeColor.LIGHT_BLUE, 0x4FD8FF);
        put(DyeColor.YELLOW, 0xFFF23A);
        put(DyeColor.LIME, 0x7CFF3A);
        put(DyeColor.PINK, 0xFF7AC8);
        put(DyeColor.GRAY, 0xA0A8B8);
        put(DyeColor.LIGHT_GRAY, 0xD0D8E8);
        put(DyeColor.CYAN, 0x2CF6E8);
        put(DyeColor.PURPLE, 0xB04DFF);
        put(DyeColor.BLUE, 0x3D6BFF);
        put(DyeColor.BROWN, 0xD08A4A);
        put(DyeColor.GREEN, 0x2DFF6A);
        put(DyeColor.RED, 0xFF2E3C);
        put(DyeColor.BLACK, 0x7A3AFF);
    }

    private static void put(DyeColor c, int rgb) {
        RGB[c.getId()] = rgb;
    }

    public static int rgb(DyeColor c) {
        return RGB[c.getId()];
    }

    /** Color de un objeto: el de su BlockStateTag (bloques) o su etiqueta "color" (barras luminosas); blanco si no tiene. */
    public static DyeColor de(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null) return DyeColor.WHITE;
        if (t.contains("color")) return DyeColor.byName(t.getString("color"), DyeColor.WHITE);
        if (t.getCompound("BlockStateTag").contains("color")) return DyeColor.byName(t.getCompound("BlockStateTag").getString("color"), DyeColor.WHITE);
        return DyeColor.WHITE;
    }

    /** Punto (0..16) del modelo que mira hacia arriba → bloque puesto mirando a {@code d}. */
    public static double[] rotar(Direction d, double x, double y, double z) {
        if (d == Direction.UP) return new double[]{x, y, z};
        if (d == Direction.DOWN) return new double[]{x, 16 - y, 16 - z};
        double X = x, Y = z, Z = 16 - y;          // x = 90: lo de arriba pasa al norte
        return switch (d) {
            case SOUTH -> new double[]{16 - X, Y, 16 - Z};
            case EAST -> new double[]{16 - Z, Y, X};
            case WEST -> new double[]{Z, Y, 16 - X};
            default -> new double[]{X, Y, Z};
        };
    }

    public static VoxelShape forma(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] a = rotar(d, x0, y0, z0), b = rotar(d, x1, y1, z1);
        return Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }
}
