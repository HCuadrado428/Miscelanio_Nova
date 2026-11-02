package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.InmersionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

import static com.miscelanea.client.tortura.aparatos.PenduloInmersionClienteMod.INMERSION_BRAZO;
import static com.miscelanea.client.tortura.aparatos.PenduloInmersionClienteMod.INMERSION_SILLA;

/**
 * La viga basculante (gira sobre el eje del poste) y la silla colgada de su punta, siempre derecha. Se dibuja entre los
 * dos valores anteriores del brazo (ver InmersionBlockEntity.tickVisual) para ir a la par que la víctima.
 */
public class InmersionRenderer implements BlockEntityRenderer<InmersionBlockEntity> {
    public InmersionRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(InmersionBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        float b = Mth.lerp(pt, be.brazoO2, be.brazoO1);
        float a = InmersionBlockEntity.angulo(b);
        float[] pin = InmersionBlockEntity.pasador(b);
        ps.pushPose();
        ModelosAparatos.orientar(ps, be.frente());
        // viga: su modelo tiene el eje en (8, 18, -2); se lleva 10 px atrás y se gira (positivo = la silla arriba)
        ps.pushPose();
        ModelosAparatos.girarX(ps, InmersionBlockEntity.EJE_Y, InmersionBlockEntity.EJE_Z, -a);
        ps.translate(0, 0, 10F / 16F);
        ModelosAparatos.dibujar(ps, buf, INMERSION_BRAZO, luz);
        ps.popPose();
        // silla: su modelo tiene el pasador en (8, 30, 8)
        ps.pushPose();
        ps.translate(0, (pin[0] - 30F) / 16F, (pin[1] - 8F) / 16F);
        ModelosAparatos.dibujar(ps, buf, INMERSION_SILLA, luz);
        ps.popPose();
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(InmersionBlockEntity be) {
        return true;
    }
}
