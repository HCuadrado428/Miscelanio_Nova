package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.PenduloBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

import static com.miscelanea.client.tortura.aparatos.PenduloInmersionClienteMod.*;

/**
 * El péndulo: la parte de arriba del pórtico (32 px sobre el bloque), la rueda del trinquete (gira al bajar), la vara
 * (se estira con la bajada), la cuchilla oscilando, la palanca de marcha, la manivela y, con alguien atado, las cuatro
 * correas (enteras o rotas). Medidas en {@link PenduloBlockEntity} (las mismas que scratchpad/gen_tortura4a.py).
 */
public class PenduloRenderer implements BlockEntityRenderer<PenduloBlockEntity> {
    public PenduloRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(PenduloBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        float baj = Mth.lerp(pt, be.bajadaVisO, be.bajadaVis);
        float ang = Mth.lerp(pt, be.angVisO, be.angVis);
        // cinemática de muerte: la última pasada, a cámara lenta
        float lenta = com.miscelanea.client.tortura.muertes.MuertesCliente.canal(be.victimaId(), "pendulo", pt);
        if (!Float.isNaN(lenta)) ang = lenta;
        float L = PenduloBlockEntity.longitud(baj);
        ps.pushPose();
        ModelosAparatos.orientar(ps, be.frente());
        // pórtico de arriba
        ps.pushPose();
        ps.translate(0, 2, 0);
        ModelosAparatos.dibujar(ps, buf, PENDULO_ALTO, luz);
        ps.popPose();
        // rueda del trinquete: centro (8, 8) en su modelo; en el mundo, en el eje (8, 58)
        ps.pushPose();
        ps.translate(0, (PenduloBlockEntity.PIVOTE_Y - 8F) / 16F, 0);
        ModelosAparatos.girarZ(ps, 8F, 8F, -baj * 360F * 2.5F);
        ModelosAparatos.dibujar(ps, buf, PENDULO_TRINQUETE, luz);
        ps.popPose();
        // vara y cuchilla, colgando del eje y girando con la oscilación
        ps.pushPose();
        ps.translate(PenduloBlockEntity.PIVOTE_X / 16F, PenduloBlockEntity.PIVOTE_Y / 16F, PenduloBlockEntity.PIVOTE_Z / 16F);
        ps.mulPose(Axis.ZP.rotationDegrees(ang));
        ps.pushPose();
        ps.scale(1F, L / 16F, 1F);
        ps.translate(-8F / 16F, -32F / 16F, -PenduloBlockEntity.PIVOTE_Z / 16F);
        ModelosAparatos.dibujar(ps, buf, PENDULO_VARA, luz);
        ps.popPose();
        ps.translate(-8F / 16F, -(L + PenduloBlockEntity.HUB_Y) / 16F, -PenduloBlockEntity.PIVOTE_Z / 16F);
        ModelosAparatos.dibujar(ps, buf, PENDULO_CUCHILLA, luz);
        ps.popPose();
        // palanca: atrás (parada) / adelante (en marcha)
        ps.pushPose();
        ModelosAparatos.girarX(ps, PenduloBlockEntity.PALANCA_Y, PenduloBlockEntity.PALANCA_Z, Mth.lerp(Mth.lerp(pt, be.palancaVisO, be.palancaVis), 35F, -35F));
        ModelosAparatos.dibujar(ps, buf, PENDULO_PALANCA, luz);
        ps.popPose();
        // manivela
        ps.pushPose();
        ModelosAparatos.girarX(ps, PenduloBlockEntity.MANIVELA_Y, PenduloBlockEntity.MANIVELA_Z, Mth.lerp(pt, be.manivelaVisO, be.manivelaVis));
        ModelosAparatos.dibujar(ps, buf, PENDULO_MANIVELA, luz);
        ps.popPose();
        // correas (solo con alguien atado)
        if (be.ocupado()) {
            int c = be.correas();
            for (int i = 0; i < 4; i++) {
                float[] k = PenduloBlockEntity.CORREAS[i];
                ps.pushPose();
                ps.translate(k[0] / 16F, 0, k[1] / 16F);
                ps.scale(k[2], 1F, 1F);
                ps.translate(-0.5F, 0, -0.5F);
                ModelosAparatos.dibujar(ps, buf, (c & (1 << i)) != 0 ? PENDULO_CORREA : PENDULO_CORREA_ROTA, luz);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PenduloBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
