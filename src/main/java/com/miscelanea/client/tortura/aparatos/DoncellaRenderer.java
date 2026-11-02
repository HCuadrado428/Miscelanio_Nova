package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.DoncellaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** Las dos puertas de la doncella, que giran sobre sus bisagras (el resto es el modelo del bloque). */
public class DoncellaRenderer implements BlockEntityRenderer<DoncellaBlockEntity> {
    public DoncellaRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(DoncellaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        float k = Mth.lerp(pt, be.visO, be.vis);
        float ang = (1F - k) * ModelosAparatos.PUERTA_ABIERTA;
        ps.pushPose();
        ModelosAparatos.orientar(ps, be.frente());
        ps.pushPose();
        ModelosAparatos.girarY(ps, ModelosAparatos.BISAGRA_IZQ_X, ModelosAparatos.BISAGRA_Z, ang);
        ModelosAparatos.dibujar(ps, buf, ModelosAparatos.DONCELLA_PUERTA_IZQ, luz);
        ps.popPose();
        ps.pushPose();
        ModelosAparatos.girarY(ps, ModelosAparatos.BISAGRA_DER_X, ModelosAparatos.BISAGRA_Z, -ang);
        ModelosAparatos.dibujar(ps, buf, ModelosAparatos.DONCELLA_PUERTA_DER, luz);
        ps.popPose();
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DoncellaBlockEntity be) {
        return true;
    }
}
