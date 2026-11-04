package com.miscelanea.client.bromas;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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

/**
 * El cartel de "PATÉAME" pegado en la espalda (un papel con letras a mano, algo torcido, con un trozo de cinta). Va con
 * el cuerpo (se agacha y se gira con él). En primera persona no se ve (las capas no se dibujan), solo en F5 y los demás.
 */
public class CartelLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/cartel_pateame.png");
    /** Medio ancho y media altura del papel (en píxeles del modelo); la textura es de 64x40. */
    private static final float MEDIO_W = 3.7F, MEDIO_H = 2.3F;

    public CartelLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre) {
        super(padre);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmount, float pt,
                       float edad, float cabeza, float pitch) {
        if (!BromasCliente.tieneCartel(p.getId()) || p.isInvisible()) return;
        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        // espalda: z = +2 px (+ la capa de la chaqueta); un poco por debajo de los hombros; torcido
        ps.translate(0.2F / 16F, 4.6F / 16F, 2.32F / 16F);
        ps.mulPose(Axis.ZP.rotationDegrees(-8F));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float w = MEDIO_W / 16F, h = MEDIO_H / 16F;
        // vista desde detrás: +X del modelo es la izquierda de quien mira, -Y es arriba
        v(vc, m, n, w, -h, 0F, 0F, luz);
        v(vc, m, n, w, h, 0F, 1F, luz);
        v(vc, m, n, -w, h, 1F, 1F, luz);
        v(vc, m, n, -w, -h, 1F, 0F, luz);
        ps.popPose();
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv, int luz) {
        vc.vertex(m, x, y, 0F).color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz)
                .normal(n, 0F, 0F, 1F).endVertex();
    }
}
