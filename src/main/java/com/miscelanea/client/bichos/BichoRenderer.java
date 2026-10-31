package com.miscelanea.client.bichos;

import com.miscelanea.Miscelanea;
import com.miscelanea.bichos.BichoEntity;
import com.miscelanea.bichos.Especie;
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
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Los bichos se dibujan con su icono visto desde arriba: los voladores con las dos mitades (alas) batiendo,
 * los de suelo tumbados. La luciérnaga lleva una luz que palpita.
 */
public class BichoRenderer extends EntityRenderer<BichoEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/bicho_luciernaga_glow.png");

    public BichoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.1F;
    }

    @Override
    public ResourceLocation getTextureLocation(BichoEntity e) {
        return new ResourceLocation(Miscelanea.MOD_ID, "textures/item/" + e.especie().item() + ".png");
    }

    @Override
    public void render(BichoEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        Especie sp = e.especie();
        float t = e.tickCount + partial;
        float s = sp.tam;
        ps.pushPose();
        ps.translate(0, sp.vuela() ? 0.15 : 0.02, 0);
        ps.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, e.yRotO, e.getYRot())));
        if (sp.vuela()) ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.2F) * 8));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e)));
        if (sp.vuela()) {
            float aleteo = switch (sp.tipo) {
                case ZUMBA -> Mth.sin(t * 3.1F) * 18F;
                case BRILLA -> Mth.sin(t * 1.4F) * 35F;
                default -> Mth.sin(t * 0.9F) * 55F + 10F;
            };
            // ala izquierda (mitad izquierda del icono) y derecha, con bisagra en el cuerpo
            for (int lado = -1; lado <= 1; lado += 2) {
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(lado * aleteo));
                mitad(vc, ps, s, lado, luz);
                ps.popPose();
            }
        } else {
            mitad(vc, ps, s, -1, luz);
            mitad(vc, ps, s, 1, luz);
        }
        if (sp == Especie.LUCIERNAGA) {
            float brillo = 0.55F + 0.45F * Mth.sin(t * 0.25F + e.getId());
            VertexConsumer g = buf.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
            quad(g, ps, -s / 2 * 1.3F, s / 2 * 1.3F, -s / 2 * 1.3F, s / 2 * 1.3F, 0, 1, LightTexture.FULL_BRIGHT, brillo, 0.002F);
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, luz);
    }

    /** Media mitad del icono como un cuadrado tumbado (x de 0 a ±s/2). La cabeza (arriba del icono) va hacia delante (+z). */
    private static void mitad(VertexConsumer vc, PoseStack ps, float s, int lado, int luz) {
        float x0 = 0, x1 = lado * s / 2;
        float u0 = 0.5F, u1 = lado < 0 ? 0F : 1F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float z0 = s / 2, z1 = -s / 2;   // v=0 (cabeza) delante
        punto(vc, m, n, x0, z0, u0, 0, luz, 1);
        punto(vc, m, n, x1, z0, u1, 0, luz, 1);
        punto(vc, m, n, x1, z1, u1, 1, luz, 1);
        punto(vc, m, n, x0, z1, u0, 1, luz, 1);
    }

    private static void quad(VertexConsumer vc, PoseStack ps, float xa, float xb, float za, float zb, float u0, float u1, int luz, float a, float y) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        vc.vertex(m, xa, y, zb).color(1F, 1F, 1F, a).uv(u0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, xb, y, zb).color(1F, 1F, 1F, a).uv(u1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, xb, y, za).color(1F, 1F, 1F, a).uv(u1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, xa, y, za).color(1F, 1F, 1F, a).uv(u0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }

    private static void punto(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v, int luz, float a) {
        vc.vertex(m, x, 0, z).color(1F, 1F, 1F, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }
}
