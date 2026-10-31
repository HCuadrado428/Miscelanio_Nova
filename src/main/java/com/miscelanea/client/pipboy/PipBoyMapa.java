package com.miscelanea.client.pipboy;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Mapa de la zona para el Pip-Boy: vista cenital con los colores de mapa de cada bloque, pasada a fósforo verde. */
public final class PipBoyMapa {
    public static final int N = 128;
    public static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "pipboy_mapa");
    private static DynamicTexture textura;

    private PipBoyMapa() {}

    public static void actualizar(ClientLevel level, int cx, int cz, int escala) {
        if (textura == null) {
            textura = new DynamicTexture(N, N, true);
            Minecraft.getInstance().getTextureManager().register(TEX, textura);
        }
        NativeImage img = textura.getPixels();
        if (img == null) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int[] alturaAnterior = new int[N];
        for (int j = -1; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int x = cx + (i - N / 2) * escala, z = cz + (j - N / 2) * escala;
                int y;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    if (j >= 0) img.setPixelRGBA(i, j, abgr(4, 14, 6));
                    if (j >= -1) alturaAnterior[i] = Integer.MIN_VALUE;
                    continue;
                }
                y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (j < 0) {
                    alturaAnterior[i] = y;
                    continue;
                }
                m.set(x, y, z);
                BlockState st = level.getBlockState(m);
                MapColor col = st.getMapColor(level, m);
                int rgb = col == MapColor.NONE ? 0 : col.col;
                float lum = (((rgb >> 16) & 255) * 0.3F + ((rgb >> 8) & 255) * 0.59F + (rgb & 255) * 0.11F) / 255F;
                lum = 0.25F + lum * 0.75F;
                int ant = alturaAnterior[i];
                if (ant != Integer.MIN_VALUE) {
                    if (y > ant) lum *= 1.18F;
                    else if (y < ant) lum *= 0.72F;
                }
                if (col == MapColor.WATER) lum = 0.28F + 0.06F * (((x + z) & 3) == 0 ? 1 : 0);
                // líneas de rejilla cada 16 bloques (chunks), muy suaves
                if (Math.floorMod(x, 16) < escala || Math.floorMod(z, 16) < escala) lum *= 0.85F;
                lum = Math.min(1, lum);
                img.setPixelRGBA(i, j, abgr((int) (lum * 70), (int) (30 + lum * 225), (int) (lum * 95)));
                alturaAnterior[i] = y;
            }
        }
        textura.upload();
    }

    private static int abgr(int r, int g, int b) {
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
