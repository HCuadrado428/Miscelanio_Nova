package com.miscelanea.client.rave;

import com.miscelanea.camaras.Montaje;
import com.miscelanea.rave.EstroboBlock;
import com.miscelanea.rave.EstroboBlockEntity;
import com.miscelanea.rave.LucesRave;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/** El cristal del estroboscopio se pone blanco en cada destello (el destello en pantalla es {@link EstroboDestello}). */
public class EstroboRenderer implements BlockEntityRenderer<EstroboBlockEntity> {
    public EstroboRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(EstroboBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        var s = be.getBlockState();
        if (be.getLevel() == null || !s.getValue(LucesRave.ENCENDIDA)) return;
        if (!LucesRave.destello(be.getLevel().getGameTime(), s.getValue(LucesRave.MODO), be.getBlockPos())) return;
        Direction d = s.getValue(EstroboBlock.FACING);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(Luces.MANCHA));
        var m = ps.last().pose();
        var n = ps.last().normal();
        // el cristal del frente (en el modelo mirando al norte: x 2..14, y 5.5..12.5, z = 3.9) y un halo más grande delante
        float[][] caras = {{2, 14, 5.5F, 12.5F, 3.9F, 1F}, {-7, 23, -2, 20, 2.5F, 0.6F}};
        for (float[] c : caras) {
            double[][] q = {Montaje.mapa(d, c[0], c[2], c[4]), Montaje.mapa(d, c[1], c[2], c[4]), Montaje.mapa(d, c[1], c[3], c[4]), Montaje.mapa(d, c[0], c[3], c[4])};
            float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
            for (int k = 0; k < 2; k++)
                for (int i = 0; i < 4; i++) {
                    int j = k == 0 ? i : 3 - i;
                    vc.vertex(m, (float) q[j][0] / 16F, (float) q[j][1] / 16F, (float) q[j][2] / 16F).color(c[5], c[5], c[5], 1F).uv(uv[j][0], uv[j][1])
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, d.getStepX(), 0, d.getStepZ()).endVertex();
                }
        }
    }
}
