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

    /** El hueco negro del marco (la textura deja 2 de sus 16 píxeles de borde a cada lado, y el marco mide 14/16): 10,5/16 de bloque. */
    private static final float HUECO = 10.5F / 16.0F * 0.97F;
    /** Dónde está la cara delantera del marco en el modelo del bloque (mirando al norte, de z = 14/16 a 16/16), y lo que se adelanta la foto. */
    private static final float CARA_Z = 14.0F / 16.0F;
    private static final float ADELANTE = 0.003F;

    public MarcoFotoRenderer(BlockEntityRendererProvider.Context c) {}

    /** El giro del modelo del bloque en su blockstate (el mismo para el norte, 0, que para los demás). */
    private static int giro(Direction d) {
        return switch (d) {
            case SOUTH -> 180;
            case WEST -> 270;
            case EAST -> 90;
            default -> 0;
        };
    }

    @Override
    public void render(MarcoFotoBlockEntity be, float parcial, PoseStack ps, MultiBufferSource buf,
                       int luz, int overlay) {
        int id = be.foto();
        if (id < 0) return;
        FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_ADJUNTA, id);
        if (t == null) return;

        Direction mira = be.getBlockState().getValue(MarcoFotoBlock.FACING);
        ps.pushPose();
        // el mismo giro que el blockstate (en el sentido de las agujas del reloj, visto desde arriba); después se dibuja
        // como si el marco mirara al norte: el cuadro queda en la cara de atrás del bloque y la foto, delante de él
        ps.translate(0.5F, 0.5F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-giro(mira)));
        ps.translate(-0.5F, -0.5F, -0.5F);

        // el hueco es cuadrado; la foto se mete dentro conservando su forma
        float ancho = HUECO, alto = HUECO;
        if (t.w >= t.h) alto = HUECO * t.h / (float) t.w;
        else ancho = HUECO * t.w / (float) t.h;
        // visto de frente (desde el norte hacia el sur), la izquierda del que mira es el +X
        float xi = 0.5F + ancho / 2, xd = 0.5F - ancho / 2, y0 = 0.5F - alto / 2, y1 = 0.5F + alto / 2;
        float z = CARA_Z - ADELANTE;

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(t.rl));
        Matrix4f m = ps.last().pose();
        org.joml.Matrix3f n = ps.last().normal();
        int lz = LightTexture.pack(Math.max(LightTexture.block(luz), 4), LightTexture.sky(luz));
        punto(vc, m, n, xi, y0, z, 0F, 1F, lz, overlay);
        punto(vc, m, n, xd, y0, z, 1F, 1F, lz, overlay);
        punto(vc, m, n, xd, y1, z, 1F, 0F, lz, overlay);
        punto(vc, m, n, xi, y1, z, 0F, 0F, lz, overlay);
        ps.popPose();
    }

    private void punto(VertexConsumer vc, Matrix4f m, org.joml.Matrix3f n, float x, float y, float z, float u, float v,
                       int luz, int overlay) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(overlay).uv2(luz).normal(n, 0F, 0F, -1F).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(MarcoFotoBlockEntity be) {
        return false;
    }
}
