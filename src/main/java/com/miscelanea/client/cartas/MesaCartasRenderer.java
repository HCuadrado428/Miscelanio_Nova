package com.miscelanea.client.cartas;

import com.miscelanea.cartas.MesaCartasBlockEntity;
import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Cartas sobre el tapete: el mazo con el triunfo, las de la baza en juego y las manos boca abajo. */
public class MesaCartasRenderer implements BlockEntityRenderer<MesaCartasBlockEntity> {
    static final float W = 0.15F, H = 0.225F, Y = 14.05F / 16;

    public MesaCartasRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(MesaCartasBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(CartasScreen.ATLAS));
        ps.pushPose();
        ps.translate(0.5, Y, 0.5);
        ps.mulPose(Axis.YP.rotation(CasetaTiroBlockEntity.giro(be.facing())));
        int mazo = be.mazo.size();
        if (be.triunfo >= 0 && mazo > 0) carta(ps, vc, be.triunfo, -0.3F, 0.002F, 0.02F, 90, luz);
        for (int i = 0; i < Math.min(8, Math.max(0, mazo - 1)); i++) carta(ps, vc, -1, -0.3F, 0.004F + i * 0.004F, 0, 0, luz);
        for (int s = 0; s < 2; s++) {
            float z = s == 0 ? -0.36F : 0.36F;
            for (int i = 0; i < be.enMano[s]; i++) carta(ps, vc, -1, -0.12F + i * 0.12F, 0.003F + i * 0.001F, z, s == 0 ? 0 : 180, luz);
            if (be.mesa[s] >= 0) carta(ps, vc, be.mesa[s], 0.1F, 0.006F + s * 0.002F, s == 0 ? -0.08F : 0.08F, s == 0 ? 8 : 172, luz);
        }
        ps.popPose();
    }

    private void carta(PoseStack ps, VertexConsumer vc, int c, float x, float y, float z, float giro, int luz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        float u0 = (c < 0 ? 0 : (c % 10) * 40) / 512F, v0 = (c < 0 ? 4 * 60 : (c / 10) * 60) / 512F, u1 = u0 + 40 / 512F, v1 = v0 + 60 / 512F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float hw = W / 2, hh = H / 2;
        v(vc, m, n, -hw, 0, hh, u1, v0, luz);
        v(vc, m, n, hw, 0, hh, u0, v0, luz);
        v(vc, m, n, hw, 0, -hh, u0, v1, luz);
        v(vc, m, n, -hw, 0, -hh, u1, v1, luz);
        ps.popPose();
    }

    private void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int luz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }
}
