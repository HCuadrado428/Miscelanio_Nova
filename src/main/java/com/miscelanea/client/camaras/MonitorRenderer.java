package com.miscelanea.client.camaras;

import com.miscelanea.camaras.MonitorBlock;
import com.miscelanea.camaras.MonitorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * La pared de monitores: solo dibuja el de abajo a la izquierda, y lo hace sobre toda la pared (la imagen de un trozo a otro
 * sin cortes; el marco de acero queda por fuera). Como dibuja fuera de su bloque, se dibuja aunque él no esté a la vista.
 */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    /** Marco (en píxeles del modelo) y plano de la pantalla (el frente del panel está en z = 13). */
    private static final double MARCO = 1.0, Z = 12.98;

    public MonitorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public boolean shouldRenderOffScreen(MonitorBlockEntity be) {
        return true;
    }

    @Override
    public void render(MonitorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        Direction d = be.getBlockState().getValue(MonitorBlock.FACING);
        int[] g = MonitorBlock.grupo(be.getLevel(), be.getBlockPos(), d);
        if (g[0] != 0 || g[2] != 0) return;          // no es el que manda
        int w = 1 + g[1], h = 1 + g[3];
        // en el modelo mirando al norte la x crece hacia la izquierda de quien mira: los de la derecha están en x negativas
        Pantalla.dibujar(ps, buf, d, be.getBlockPos(), be.canal(), 16 - MARCO, 16 - 16 * w + MARCO, MARCO, 16 * h - MARCO, Z);
    }
}
