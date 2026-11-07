package com.miscelanea.client.baile.videoclip;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Weapon of Choice: el vestíbulo de un hotel elegante. Suelo de mármol a cuadros con alfombra roja por el centro,
 * paredes de madera oscura y cuarzo, columnas, galería con barandilla a los dos lados, escaleras mecánicas que suben a
 * la galería, ascensores con marco dorado, sofás, plantas y lámparas de araña. Se repite cada 16 bloques hacia
 * delante: cuando el protagonista se pavonea, el vestíbulo pasa por debajo de sus pies sin acabarse nunca (los
 * extremos se pierden en la penumbra), y en el vuelo todo baja y lo ves planear entre las lámparas.
 */
final class DecoradoHotel extends Decorado {
    static final int ANCHO = 12, ALTO = 16, Z0 = -48, Z1 = 64;

    @Override
    int luz() {
        return 15;
    }

    @Override
    float[] niebla() {
        return new float[]{0.16F, 0.11F, 0.07F};
    }

    @Override
    float cerca() {
        return 14;
    }

    @Override
    float lejos() {
        return 36;
    }

    @Override
    float periodo() {
        return 16;
    }

    @Override
    boolean viaja() {
        return true;
    }

    private static BlockState valla(Block b) {
        return b.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
    }

    private static BlockState escalon(Block b, Direction d) {
        return b.defaultBlockState().setValue(StairBlock.FACING, d);
    }

    @Override
    void construir() {
        BlockState barandilla = valla(Blocks.DARK_OAK_FENCE), mesa = Blocks.DARK_OAK_FENCE.defaultBlockState();
        BlockState farol = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState lampara = Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
        for (int z = Z0; z <= Z1; z++) {
            int zm = Math.floorMod(z, 16), z8 = Math.floorMod(z, 8);
            for (int x = -ANCHO; x <= ANCHO; x++) {
                int ax = Math.abs(x);
                // extremos: se pierden en la niebla
                if (z == Z0 || z == Z1) {
                    for (int y = -1; y <= ALTO; y++) pon(x, y, z, Blocks.BLACK_CONCRETE);
                    continue;
                }
                if (ax == ANCHO) {
                    // paredes: zócalo de madera con pilastras, banda de cuarzo labrado, cuarzo arriba y ventanales
                    for (int y = -1; y <= ALTO; y++) {
                        Block b;
                        if (y <= 3) b = y >= 0 && z8 == 0 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_PLANKS;
                        else if (y == 4) b = Blocks.CHISELED_QUARTZ_BLOCK;
                        else if (y >= 9 && y <= 13 && z8 >= 2 && z8 <= 5) {
                            b = Blocks.GLASS;
                            pon(x + Integer.signum(x), y, z, Blocks.WHITE_CONCRETE);    // luz de la calle tras el cristal
                        } else if (y == 8 || y == 14) b = Blocks.DARK_OAK_PLANKS;
                        else b = Blocks.QUARTZ_BRICKS;
                        pon(x, y, z, b);
                    }
                    continue;
                }
                // suelo: alfombra roja con remate, mármol crema y rojizo en cuadros de 2x2
                Block s;
                if (ax <= 2) s = Blocks.RED_WOOL;
                else if (ax == 3) s = Blocks.SMOOTH_SANDSTONE;
                else s = ((Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1) == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.POLISHED_GRANITE;
                pon(x, -1, z, s);
                // techo artesonado
                pon(x, ALTO, z, Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.SMOOTH_QUARTZ);
                // galería a los dos lados (en el lado izquierdo, solo donde no están las escaleras mecánicas)
                boolean galeria = ax >= 9 && (x > 0 || zm >= 7);
                if (galeria) {
                    pon(x, 7, z, Blocks.DARK_OAK_PLANKS);
                    if (ax == 9) pon(x, 8, z, barandilla);
                }
            }
            // columnas en el borde de la galería
            if (z8 == 0 && z != Z0 && z != Z1)
                for (int x : new int[]{-8, 8}) {
                    pon(x, 0, z, Blocks.CHISELED_QUARTZ_BLOCK);
                    for (int y = 1; y < ALTO - 1; y++) pon(x, y, z, Blocks.QUARTZ_PILLAR);
                    pon(x, ALTO - 1, z, Blocks.CHISELED_QUARTZ_BLOCK);
                }
            if (z == Z0 || z == Z1) continue;
            // escaleras mecánicas (lado -x): suben hacia delante hasta la galería
            if (zm <= 6) {
                for (int x = -11; x <= -10; x++) {
                    for (int y = 0; y < zm; y++) pon(x, y, z, Blocks.POLISHED_DEEPSLATE);
                    pon(x, zm, z, escalon(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH));
                }
                for (int y = 0; y <= zm; y++) pon(-9, y, z, y == zm ? Blocks.SMOOTH_STONE : Blocks.POLISHED_DEEPSLATE);
                pon(-9, zm + 1, z, Blocks.IRON_BARS.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true));
            }
            // ascensores (lado +x): puertas de acero con marco dorado y piloto encendido
            for (int base : new int[]{1, 9}) {
                int d = zm - base;
                if (d < 0 || d > 3) continue;
                for (int y = 0; y <= 3; y++) {
                    boolean marco = d == 0 || d == 3 || y == 3;
                    pon(ANCHO, y, z, marco ? Blocks.GOLD_BLOCK : Blocks.IRON_BLOCK);
                }
                if (d == 1 || d == 2) pon(ANCHO, 4, z, lampara);
            }
            // sofás contra la pared con mesita, macetas y el mostrador de recepción
            if (zm >= 5 && zm <= 7) pon(11, 0, z, escalon(Blocks.CRIMSON_STAIRS, Direction.EAST));
            if (zm == 6) {
                pon(10, 0, z, mesa);
                pon(10, 1, z, Blocks.POTTED_RED_TULIP);
            }
            if (zm >= 10 && zm <= 12) pon(-11, 0, z, escalon(Blocks.CRIMSON_STAIRS, Direction.WEST));
            if (zm == 11) {
                pon(-10, 0, z, mesa);
                pon(-10, 1, z, Blocks.POTTED_WHITE_TULIP);
            }
            if (zm == 15) {
                pon(11, 0, z, Blocks.POTTED_BAMBOO);
                pon(-11, 0, z, Blocks.POTTED_BAMBOO);
            }
            if (zm == 4) {
                pon(11, 8, z, Blocks.POTTED_FLOWERING_AZALEA);
                pon(-11, 8, z, Blocks.POTTED_AZALEA);
            }
            if (zm >= 12 && zm <= 14) {
                pon(9, 0, z, Blocks.STRIPPED_DARK_OAK_WOOD);
                pon(9, 1, z, Blocks.SMOOTH_QUARTZ_SLAB);
            }
            // lámpara de araña en el centro
            if (zm == 8) {
                for (int y = 12; y < ALTO; y++) pon(0, y, z, Blocks.CHAIN);
                pon(0, 11, z, Blocks.GOLD_BLOCK);
                pon(0, 10, z, farol);
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    pon(o[0], 11, z + o[1], Blocks.CHAIN);
                    pon(o[0], 10, z + o[1], farol);
                }
            }
        }
    }

    @Override
    void luces(Matrix4f m, VertexConsumer brillo, VertexConsumer opaco, Momento t) {
        float lat = 0.85F + 0.15F * t.golpe();
        for (int z = Z0 + 8; z < Z1; z += 16) {
            // brillo cálido de las lámparas de araña
            halo(brillo, m, 0, 10.6F, z, 1.8F, 0xFFD890, 0.16F * lat);
        }
        // rayos de sol de los ventanales: una lámina desde el borde de arriba de cada ventana hasta el suelo
        for (int z = Z0 + 8; z < Z1 - 8; z += 8)
            for (int sg : new int[]{-1, 1}) {
                float xw = sg * (ANCHO - 0.52F), xs = sg * (ANCHO - 8.0F), z0 = z + 1.5F, z1 = z + 5.5F, a = 0.06F;
                v(brillo, m, xw, 13.5F, z0, 0xFFF0D0, a);
                v(brillo, m, xw, 13.5F, z1, 0xFFF0D0, a);
                v(brillo, m, xs, 0.03F, z1 + 2.5F, 0xFFF0D0, 0);
                v(brillo, m, xs, 0.03F, z0 + 2.5F, 0xFFF0D0, 0);
            }
        // pilotos de los ascensores
        for (int z = Z0; z < Z1; z++) {
            int zm = Math.floorMod(z, 16);
            if (zm == 2 || zm == 10) paredX(brillo, m, ANCHO - 0.52F, 4.0F, z - 0.5F, 5.0F, z + 1.5F, 0xFFB040, 0.10F + 0.08F * t.golpe());
        }
    }
}
