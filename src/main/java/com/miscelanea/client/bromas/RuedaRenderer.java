package com.miscelanea.client.bromas;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.aparatos.RuedaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Dibuja la rueda de Catalina entera (el bloque es invisible): el soporte quieto y la rueda girando con el ángulo que
 * simula el cliente ({@link RuedaBlockEntity#anguloVis}). La víctima gira con ella en RuedaCliente (mismo ángulo y eje).
 */
public class RuedaRenderer implements BlockEntityRenderer<RuedaBlockEntity> {
    public static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/rueda_catalina.png");
    private final ModelPart rueda, soporte;
    private final ModelPart[] correas = new ModelPart[4];

    public RuedaRenderer(BlockEntityRendererProvider.Context c) {
        ModelPart raiz = RuedaModelo.crear();
        rueda = raiz.getChild("rueda");
        soporte = raiz.getChild("soporte");
        for (int i = 0; i < 4; i++) correas[i] = rueda.getChild("correa_" + i);
    }

    @Override
    public void render(RuedaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        ps.pushPose();
        // del espacio del bloque al del modelo (que mira al norte), con el origen en el centro del suelo
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180F - be.frente().toYRot()));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(TEX));
        soporte.render(ps, vc, luz, OverlayTexture.NO_OVERLAY);
        // girar sobre el frente (-Z del modelo) un ángulo θ es girar sobre +Z un ángulo -θ
        rueda.zRot = -be.anguloVis(pt) * Mth.DEG_TO_RAD;
        int c = be.ocupado() ? be.correas() : RuedaBlockEntity.TODAS;
        for (int i = 0; i < 4; i++) correas[i].visible = (c & (1 << i)) != 0;
        rueda.render(ps, vc, luz, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RuedaBlockEntity be) {
        return true;
    }
}
