package com.miscelanea.client.combate;

import com.miscelanea.Miscelanea;
import com.miscelanea.llave.HechizoEntity;
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

/** Hechizos: una bola brillante (Piro) o una esquirla de hielo (Hielo) que mira a la cámara, a plena luz. */
public class HechizoRenderer extends EntityRenderer<HechizoEntity> {
    private static final ResourceLocation PIRO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/hechizo_piro.png");
    private static final ResourceLocation HIELO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/hechizo_hielo.png");

    public HechizoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(HechizoEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.translate(0, 0.15, 0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partial) * (e.tipo() == HechizoEntity.PIRO ? 20 : 8)));
        float s = e.tipo() == HechizoEntity.PIRO ? 0.45F : 0.32F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(e)));
        int l = LightTexture.FULL_BRIGHT;
        vc.vertex(m, -s, -s, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, -s, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, s, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, -s, s, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(HechizoEntity e) {
        return e.tipo() == HechizoEntity.PIRO ? PIRO : HIELO;
    }
}
