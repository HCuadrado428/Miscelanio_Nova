package com.miscelanea.client.movil;

import com.miscelanea.movil.MarcoFotoBlock;
import com.miscelanea.movil.MarcoFotoBlockEntity;
import com.miscelanea.movil.MovilRed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Pinta en el marco la foto que tenga puesta. La imagen se pide igual que las del chat, así que la ve todo el mundo.
 * Se encaja dentro del hueco del marco sin deformarla: si la foto es más ancha que alta, sobran franjas arriba y
 * abajo, y al revés.
 */
public class MarcoFotoRenderer implements BlockEntityRenderer<MarcoFotoBlockEntity> {

    /** El hueco del marco, en dieciseisavos de bloque. */
    private static final float BORDE = 2.0F / 16.0F;
    /** A qué profundidad queda la foto dentro del marco. */
    private static final float FONDO = 1.6F / 16.0F;

    public MarcoFotoRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(MarcoFotoBlockEntity be, float parcial, PoseStack ps, MultiBufferSource buf,
                       int luz, int overlay) {
        int id = be.foto();
        if (id < 0) return;
        FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_ADJUNTA, id);
        if (t == null) return;

        Direction mira = be.getBlockState().getValue(MarcoFotoBlock.FACING);
        ps.pushPose();
        ps.translate(0.5F, 0.5F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-mira.toYRot()));
        ps.translate(0.0F, 0.0F, 0.5F - FONDO);

        // el hueco es cuadrado; la foto se mete dentro conservando su forma
        float hueco = 1.0F - 2 * BORDE;
        float ancho = hueco, alto = hueco;
        if (t.w >= t.h) alto = hueco * t.h / (float) t.w;
        else ancho = hueco * t.w / (float) t.h;
        float x0 = -ancho / 2, x1 = ancho / 2, y0 = -alto / 2, y1 = alto / 2;

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(t.rl));
        Matrix4f m = ps.last().pose();
        int lz = LightTexture.pack(Math.max(LightTexture.block(luz), 4), LightTexture.sky(luz));
        cara(vc, m, ps, x0, y0, x1, y1, lz, overlay);
        ps.popPose();
    }

    private void cara(VertexConsumer vc, Matrix4f m, PoseStack ps, float x0, float y0, float x1, float y1,
                      int luz, int overlay) {
        PoseStack.Pose p = ps.last();
        punto(vc, m, p, x0, y0, 0F, 1F, luz, overlay);
        punto(vc, m, p, x1, y0, 1F, 1F, luz, overlay);
        punto(vc, m, p, x1, y1, 1F, 0F, luz, overlay);
        punto(vc, m, p, x0, y1, 0F, 0F, luz, overlay);
    }

    private void punto(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float x, float y, float u, float v,
                       int luz, int overlay) {
        vc.vertex(m, x, y, 0F).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(overlay).uv2(luz).normal(p, 0F, 0F, 1F).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(MarcoFotoBlockEntity be) {
        return false;
    }
}
