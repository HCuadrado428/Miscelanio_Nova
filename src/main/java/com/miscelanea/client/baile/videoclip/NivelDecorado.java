package com.miscelanea.client.baile.videoclip;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Mundo de mentira con solo los bloques del decorado, para teselarlos una vez (con oclusión ambiental y caras
 * escondidas, como un chunk de verdad) sin tocar el mundo real. Luz fija: la del decorado, sin cielo.
 */
final class NivelDecorado implements BlockAndTintGetter {
    private final Map<BlockPos, BlockState> bloques;
    private final int luz;
    private final LevelLightEngine motor;
    @Nullable
    private final Biome bioma;

    NivelDecorado(Map<BlockPos, BlockState> bloques, int luz) {
        this.bloques = bloques;
        this.luz = luz;
        BlockGetter yo = this;
        this.motor = new LevelLightEngine(new LightChunkGetter() {
            @Nullable
            @Override
            public LightChunk getChunkForLighting(int x, int z) {
                return null;
            }

            @Override
            public BlockGetter getLevel() {
                return yo;
            }
        }, false, false);
        Biome b = null;
        try {
            var mc = Minecraft.getInstance();
            if (mc.level != null) b = mc.level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        } catch (RuntimeException ignored) {
        }
        this.bioma = b;
    }

    @Override
    public float getShade(Direction d, boolean sombra) {
        if (!sombra) return 1F;
        return switch (d) {
            case DOWN -> 0.5F;
            case UP -> 1F;
            case NORTH, SOUTH -> 0.8F;
            default -> 0.6F;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return motor;
    }

    @Override
    public int getBrightness(LightLayer capa, BlockPos p) {
        return capa == LightLayer.BLOCK ? luz : 0;
    }

    @Override
    public int getRawBrightness(BlockPos p, int oscurecer) {
        return luz;
    }

    @Override
    public boolean canSeeSky(BlockPos p) {
        return false;
    }

    @Override
    public int getBlockTint(BlockPos p, ColorResolver r) {
        return bioma == null ? 0x48B518 : r.getColor(bioma, p.getX(), p.getZ());
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos p) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos p) {
        BlockState s = bloques.get(p);
        return s == null ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public FluidState getFluidState(BlockPos p) {
        return getBlockState(p).getFluidState();
    }

    @Override
    public int getHeight() {
        return 128;
    }

    @Override
    public int getMinBuildHeight() {
        return -64;
    }
}
