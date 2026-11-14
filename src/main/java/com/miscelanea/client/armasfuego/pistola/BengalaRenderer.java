package com.miscelanea.client.armasfuego.pistola;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.pistola.BengalaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * La bengala: un brillo rojo (aditivo, sin sombras) mirando a la cámara. De lejos no encoge por debajo de un tamaño
 * fijo en pantalla, para que se vea en el cielo desde lejos como una bengala de verdad.
 */
public class BengalaRenderer extends EntityRenderer<BengalaEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/revolver_bengala.png");

    public BengalaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(BengalaEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        float b = e.brillo(pt);
        if (b <= 0.01F) return;
        double d = Math.sqrt(entityRenderDispatcher.distanceToSqr(e));
        // parpadeo de llama y tamaño mínimo en pantalla
        float parpadeo = 0.85F + 0.15F * (float) Math.sin((e.tickCount + pt) * 1.7F) * (float) Math.sin((e.tickCount + pt) * 0.63F + 1);
        float tam = (float) Math.max(0.9, d * 0.022) * parpadeo * (0.4F + 0.6F * b);
        ps.pushPose();
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(tam, tam, tam);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(TEX));
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        int a = (int) (255 * b);
        quad(vc, m, n, a);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int a) {
        v(vc, m, n, -0.5F, -0.5F, 0, 1, a);
        v(vc, m, n, 0.5F, -0.5F, 1, 1, a);
        v(vc, m, n, 0.5F, 0.5F, 1, 0, a);
        v(vc, m, n, -0.5F, 0.5F, 0, 0, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float w, int a) {
        vc.vertex(m, x, y, 0).color(a, a, a, 255).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0, 1, 0).endVertex();
    }

    @Override
    protected int getBlockLightLevel(BengalaEntity e, BlockPos pos) {
        return 15;
    }

    @Override
    public boolean shouldRender(BengalaEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z)) && f.isVisible(e.getBoundingBox().inflate(Math.max(1.0, Math.sqrt(e.distanceToSqr(x, y, z)) * 0.02)));
    }

    @Override
    public ResourceLocation getTextureLocation(BengalaEntity e) {
        return TEX;
    }
}
