package com.miscelanea.client.rave;

import com.miscelanea.camaras.Montaje;
import com.miscelanea.rave.PantallaVideoBlock;
import com.miscelanea.rave.PantallaVideoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * La pantalla de vídeo: solo dibuja el panel de abajo a la izquierda, sobre toda la pantalla. El vídeo se ajusta sin
 * deformarse (con bandas negras si no tiene la misma forma) y a toda luz; sin vídeo, un aviso de cómo poner uno.
 */
public class PantallaVideoRenderer implements BlockEntityRenderer<PantallaVideoBlockEntity> {
    private static final double MARCO = 0.6, Z = 12.98;

    public PantallaVideoRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRenderOffScreen(PantallaVideoBlockEntity be) {
        return true;
    }

    private static double[] pt(Direction d, double x, double y, double z) {
        double[] r = Montaje.mapa(d, x, y, z - 0.02);
        return new double[]{r[0] / 16, r[1] / 16, r[2] / 16};
    }

    @Override
    public void render(PantallaVideoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        Direction d = be.getBlockState().getValue(PantallaVideoBlock.FACING);
        int[] g = PantallaVideoBlock.grupo(be.getLevel(), be.getBlockPos(), d);
        if (g[0] != 0 || g[2] != 0) return;
        int w = 1 + g[1], h = 1 + g[3];
        double xIzq = 16 - MARCO, xDer = 16 - 16 * w + MARCO, yAb = MARCO, yAr = 16 * h - MARCO;
        VideosPantalla.Imagen im = VideosPantalla.imagen(be, pt);
        if (im == null) {
            // sin vídeo: aviso en el centro
            Font f = Minecraft.getInstance().font;
            Component t = Component.translatable(be.dueno() == null ? "rave.miscelanea.pantalla.vacia" : "rave.miscelanea.pantalla.cargando");
            double ancho = xIzq - xDer, porPx = Math.min((yAr - yAb) / 24.0, ancho / (f.width(t) + 8));
            double[] o = pt(d, (xIzq + xDer) / 2, (yAb + yAr) / 2, Z), ox = pt(d, (xIzq + xDer) / 2 - 1, (yAb + yAr) / 2, Z), oy = pt(d, (xIzq + xDer) / 2, (yAb + yAr) / 2 - 1, Z);
            float k = (float) (porPx / 16);
            Matrix4f m = new Matrix4f(ps.last().pose()).mul(new Matrix4f(
                    (float) ((ox[0] - o[0]) * porPx), (float) ((ox[1] - o[1]) * porPx), (float) ((ox[2] - o[2]) * porPx), 0,
                    (float) ((oy[0] - o[0]) * porPx), (float) ((oy[1] - o[1]) * porPx), (float) ((oy[2] - o[2]) * porPx), 0,
                    -d.getStepX() * k, -d.getStepY() * k, -d.getStepZ() * k, 0,
                    (float) (o[0] + d.getStepX() * 0.004), (float) (o[1] + d.getStepY() * 0.004), (float) (o[2] + d.getStepZ() * 0.004), 1));
            f.drawInBatch(t, -f.width(t) / 2F, -4, 0xFFB8B8C8, false, m, buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            return;
        }
        // ajustar sin deformar: bandas negras a los lados o arriba y abajo
        double ancho = xIzq - xDer, alto = yAr - yAb, a = ancho / alto, av = (double) im.w / Math.max(1, im.h);
        if (a > av) {
            double nw = alto * av, c = (xIzq + xDer) / 2;
            xIzq = c + nw / 2;
            xDer = c - nw / 2;
        } else {
            double nh = ancho / av, c = (yAb + yAr) / 2;
            yAb = c - nh / 2;
            yAr = c + nh / 2;
        }
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(im.rl));
        double[][] p = {pt(d, xDer, yAb, Z), pt(d, xIzq, yAb, Z), pt(d, xIzq, yAr, Z), pt(d, xDer, yAr, Z)};
        float[][] uv = {{1, 1}, {0, 1}, {0, 0}, {1, 0}};
        var m = ps.last().pose();
        var n = ps.last().normal();
        for (int i = 0; i < 4; i++)
            vc.vertex(m, (float) p[i][0], (float) p[i][1], (float) p[i][2]).color(255, 255, 255, 255).uv(uv[i][0], uv[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
    }
}
