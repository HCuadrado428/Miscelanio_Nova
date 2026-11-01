package com.miscelanea.client.tanques;

import com.miscelanea.Miscelanea;
import com.miscelanea.bichos.BichoItem;
import com.miscelanea.bichos.Especie;
import com.miscelanea.tanques.TanqueBlock;
import com.miscelanea.tanques.TanqueBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Peces nadando, bichos volando o andando y burbujas subiendo dentro de acuarios y terrarios. */
public class TanqueRenderer implements BlockEntityRenderer<TanqueBlockEntity> {
    private static final ResourceLocation BURBUJA = new ResourceLocation("textures/particle/bubble.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/bicho_luciernaga_glow.png");

    public TanqueRenderer(BlockEntityRendererProvider.Context c) {}

    private static ResourceLocation tex(Especie e) {
        return new ResourceLocation(Miscelanea.MOD_ID, "textures/item/" + e.item() + ".png");
    }

    @Override
    public void render(TanqueBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        if (be.getLevel() == null) return;
        boolean acuario = be.getBlockState().getBlock() instanceof TanqueBlock tb && tb.acuario;
        float t = be.getLevel().getGameTime() + pt;
        long sem = be.getBlockPos().asLong();
        float fase0 = (sem % 1000) * 0.37F;
        for (int i = 0; i < be.animales.size(); i++) {
            ItemStack st = be.animales.get(i);
            if (!(st.getItem() instanceof BichoItem bi)) continue;
            float fase = fase0 + i * 2.13F;
            if (acuario) pez(bi.especie, BichoItem.cm(st), t, fase, i, ps, buf, luz);
            else bicho(bi.especie, t, fase, ps, buf, luz);
        }
        if (acuario) burbujas(t, fase0, ps, buf, luz);
    }

    private void pez(Especie e, int cm, float t, float fase, int i, PoseStack ps, MultiBufferSource buf, int luz) {
        float dir = i % 2 == 0 ? 1 : -1;
        float th = t * 0.022F * dir + fase;
        float rx = 0.27F, rz = 0.24F;
        float x = 0.5F + rx * Mth.cos(th), z = 0.5F + rz * Mth.sin(th * 1.0F);
        float y = 0.47F + 0.13F * Mth.sin(t * 0.021F + fase * 1.7F) + (i - 1.5F) * 0.04F;
        float dx = -rx * Mth.sin(th) * dir, dz = rz * Mth.cos(th) * dir;
        float s = 0.26F + Math.min(1F, cm / 140F) * 0.16F;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(-dz, dx)));
        ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.45F + fase) * 9F));      // coleteo
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex(e)));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        // icono mirando a la izquierda: u=0 (cabeza) en +x
        v(vc, m, n, s / 2, s / 2, 0, 0, 0, luz);
        v(vc, m, n, -s / 2, s / 2, 0, 1, 0, luz);
        v(vc, m, n, -s / 2, -s / 2, 0, 1, 1, luz);
        v(vc, m, n, s / 2, -s / 2, 0, 0, 1, luz);
        ps.popPose();
    }

    private void bicho(Especie e, float t, float fase, PoseStack ps, MultiBufferSource buf, int luz) {
        boolean vuela = e.vuela();
        float x, y, z, dx, dz;
        if (vuela) {
            float a = t * 0.035F + fase;
            x = 0.5F + 0.28F * Mth.sin(a);
            z = 0.5F + 0.28F * Mth.cos(a * 0.77F + fase);
            y = 0.58F + 0.17F * Mth.sin(a * 1.3F + fase * 2);
            dx = 0.28F * Mth.cos(a);
            dz = -0.28F * 0.77F * Mth.sin(a * 0.77F + fase);
        } else {
            float a = t * 0.012F + fase;
            float pausa = Mth.sin(t * 0.03F + fase) > -0.2F ? 1 : 0.2F;
            x = 0.5F + 0.3F * Mth.sin(a);
            z = 0.5F + 0.3F * Mth.sin(a * 1.31F + fase * 1.7F);
            y = 3.2F / 16;
            dx = Mth.cos(a) * pausa;
            dz = 1.31F * Mth.cos(a * 1.31F + fase * 1.7F) * pausa;
        }
        float s = e.tam * 0.6F;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        if (vuela) ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.2F) * 8));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex(e)));
        if (vuela) {
            float aleteo = switch (e.tipo) {
                case ZUMBA -> Mth.sin(t * 3.1F) * 18F;
                case BRILLA -> Mth.sin(t * 1.4F) * 35F;
                default -> Mth.sin(t * 0.9F) * 55F + 10F;
            };
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
        if (e == Especie.LUCIERNAGA) {
            float brillo = 0.55F + 0.45F * Mth.sin(t * 0.25F + fase);
            VertexConsumer g = buf.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float h = s * 0.65F;
            g.vertex(m, -h, 0.002F, h).color(1F, 1F, 1F, brillo).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
            g.vertex(m, h, 0.002F, h).color(1F, 1F, 1F, brillo).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
            g.vertex(m, h, 0.002F, -h).color(1F, 1F, 1F, brillo).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
            g.vertex(m, -h, 0.002F, -h).color(1F, 1F, 1F, brillo).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
        }
        ps.popPose();
    }

    /** Media mitad del icono tumbada (x de 0 a ±s/2); la cabeza (arriba del icono) hacia +z. */
    private static void mitad(VertexConsumer vc, PoseStack ps, float s, int lado, int luz) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float x1 = lado * s / 2, u1 = lado < 0 ? 0F : 1F;
        h(vc, m, n, 0, s / 2, 0.5F, 0, luz);
        h(vc, m, n, x1, s / 2, u1, 0, luz);
        h(vc, m, n, x1, -s / 2, u1, 1, luz);
        h(vc, m, n, 0, -s / 2, 0.5F, 1, luz);
    }

    private void burbujas(float t, float fase0, PoseStack ps, MultiBufferSource buf, int luz) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(BURBUJA));
        var cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (int k = 0; k < 5; k++) {
            float ciclo = (t * 0.009F + k * 0.21F + fase0 * 0.1F) % 1F;
            float y = 0.22F + ciclo * 0.6F;
            float x = 0.22F + ((k * 0.37F + fase0 * 0.05F) % 0.56F) + Mth.sin(t * 0.1F + k) * 0.02F;
            float z = 0.25F + ((k * 0.53F + fase0 * 0.03F) % 0.5F);
            float s = 0.025F + 0.012F * (k % 3);
            ps.pushPose();
            ps.translate(x, y, z);
            ps.mulPose(cam);
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float a = ciclo > 0.9F ? (1 - ciclo) * 10 : 1;
            vc.vertex(m, -s, -s, 0).color(1F, 1F, 1F, 0.8F * a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
            vc.vertex(m, s, -s, 0).color(1F, 1F, 1F, 0.8F * a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
            vc.vertex(m, s, s, 0).color(1F, 1F, 1F, 0.8F * a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
            vc.vertex(m, -s, s, 0).color(1F, 1F, 1F, 0.8F * a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
            ps.popPose();
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv, int luz) {
        vc.vertex(m, x, y, z).color(1F, 1F, 1F, 1F).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 0, 1).endVertex();
    }

    private static void h(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float vv, int luz) {
        vc.vertex(m, x, 0, z).color(1F, 1F, 1F, 1F).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }
}
