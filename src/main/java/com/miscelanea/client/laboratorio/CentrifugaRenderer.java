package com.miscelanea.client.laboratorio;

import com.miscelanea.client.tortura.aparatos.ModelosAparatos;
import com.miscelanea.laboratorio.CentrifugaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** El brazo de la centrífuga, que gira sobre el eje del bloque (el modelo suelto tiene su origen en el eje). */
public class CentrifugaRenderer implements BlockEntityRenderer<CentrifugaBlockEntity> {
    public CentrifugaRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(CentrifugaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        float a = Mth.rotLerp(pt, be.anguloAnt, be.angulo());
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(a));
        ModelosAparatos.dibujar(ps, buf, LabClienteMod.CENTRIFUGA_BRAZO, luz);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CentrifugaBlockEntity be) {
        return true;
    }
}
