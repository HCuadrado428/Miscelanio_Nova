package com.miscelanea.client.tinta;

import com.miscelanea.Miscelanea;
import com.miscelanea.tinta.TintaProyectil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Gota de tinta: una mancha que mira a la cámara, teñida del color, estirada en la dirección del vuelo. */
public class TintaProyectilRenderer extends EntityRenderer<TintaProyectil> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tinta.png");

    public TintaProyectilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(TintaProyectil e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        int c = e.color();
        float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
        float s = 0.22F * e.tam();
        ps.pushPose();
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partial) * 25));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TEX));
        int v = e.getId() & 3;
        float u0 = v / 4F, u1 = (v + 1) / 4F;
        int l = LightTexture.FULL_BRIGHT;
        vc.vertex(m, -s, -s, 0).color(r, g, b, 1).uv(u0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, -s, 0).color(r, g, b, 1).uv(u1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, s, 0).color(r, g, b, 1).uv(u1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, -s, s, 0).color(r, g, b, 1).uv(u0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(TintaProyectil e) {
        return TEX;
    }
}
