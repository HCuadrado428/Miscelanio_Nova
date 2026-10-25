package com.miscelanea.client.tinta;

import com.miscelanea.Miscelanea;
import com.miscelanea.tinta.ArmaTinta;
import com.miscelanea.tinta.Calamar;
import com.miscelanea.tinta.Deposito;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Depósito de tinta a la espalda (como en Splatoon): bombona transparente que se ve llena según la tinta que queda. */
public class DepositoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/deposito.png");

    public DepositoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float ls, float la, float partial,
                       float edad, float yawCabeza, float pitch) {
        if (ArmaTinta.enMano(p).isEmpty() || Calamar.es(p) || p.isInvisible()) return;
        int c = ArmaTinta.colorJugador(p);
        float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
        float fill = Deposito.nivel(p) / Deposito.MAX;
        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        // tinta dentro (sube y baja con el nivel)
        float fondo = 10.5F, techo = 2.5F;
        float top = fondo - (fondo - techo) * fill;
        if (fill > 0.01F) caja(vc, m, n, -2.6F, top, 2.6F, 2.6F, fondo, 5.4F, 0.5F, 0, 1, 0.5F, r, g, b, luz);
        // tapa, base, correas y marco metálico
        caja(vc, m, n, -3.2F, 1.4F, 2.1F, 3.2F, 2.6F, 5.9F, 0, 0, 0.5F, 0.5F, 1, 1, 1, luz);
        caja(vc, m, n, -3.2F, 10.4F, 2.1F, 3.2F, 11.6F, 5.9F, 0, 0, 0.5F, 0.5F, 1, 1, 1, luz);
        caja(vc, m, n, -1.0F, 0.6F, 3.2F, 1.0F, 1.4F, 4.8F, 0, 0, 0.5F, 0.5F, 1, 1, 1, luz);   // boquilla
        for (float x : new float[]{-3.2F, 2.6F}) caja(vc, m, n, x, 2.6F, 5.3F, x + 0.6F, 10.4F, 5.9F, 0, 0, 0.5F, 0.5F, 1, 1, 1, luz);
        for (float x : new float[]{-2.6F, 1.8F}) caja(vc, m, n, x, 0.0F, -2.1F, x + 0.8F, 6.0F, 2.1F, 0, 0.5F, 0.5F, 1, 1, 1, 1, luz);   // correas
        // cristal (sobre la tinta)
        RenderType cristal = RenderType.entityTranslucent(TEX);
        caja(buf.getBuffer(cristal), m, n, -2.9F, 2.6F, 2.3F, 2.9F, 10.4F, 5.7F, 0.5F, 0.5F, 1, 1, 1, 1, 1, luz);
        ps.popPose();
    }

    /** Caja en coordenadas del cuerpo (px), con la misma región de textura en todas las caras. */
    static void caja(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float z0, float x1, float y1, float z1,
                     float u0, float v0, float u1, float v1, float r, float g, float b, int luz) {
        float a = 1 / 16F;
        x0 *= a; y0 *= a; z0 *= a; x1 *= a; y1 *= a; z1 *= a;
        float[][][] caras = {
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {0, 0, -1}},
                {{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}, {0, 0, 1}},
                {{x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}, {1, 0, 0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}, {0, -1, 0}},
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}, {0, 1, 0}}};
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (float[][] f : caras) {
            for (int k = 0; k < 4; k++) {
                vc.vertex(m, f[k][0], f[k][1], f[k][2]).color(r, g, b, 1).uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(luz).normal(n, f[4][0], f[4][1], f[4][2]).endVertex();
            }
        }
    }
}
