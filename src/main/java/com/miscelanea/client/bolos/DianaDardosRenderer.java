package com.miscelanea.client.bolos;

import com.miscelanea.client.feria.FeriaPiezas;
import com.miscelanea.dardos.DianaDardosBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Los dardos clavados del turno y una pizarrita con lo que queda. */
public class DianaDardosRenderer implements BlockEntityRenderer<DianaDardosBlockEntity> {
    private final Font font;

    public DianaDardosRenderer(BlockEntityRendererProvider.Context c) {
        font = c.getFont();
    }

    @Override
    public void render(DianaDardosBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        var vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-be.facing().toYRot()));
        for (int i = 0; i < be.dardos && i < 3; i++) {
            ps.pushPose();
            ps.translate(be.du[i], be.dv[i], -0.375);
            ps.mulPose(Axis.XP.rotationDegrees(-8 + i * 5));
            ps.mulPose(Axis.YP.rotationDegrees(-6 + i * 6));
            ps.scale(0.55F, 0.55F, 0.55F);
            ps.translate(-0.5, -0.5, 0);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("dardo"), 1, 1, 1, luz);
            ps.popPose();
        }
        // pizarrita encima
        ps.translate(0, 0.62, -0.38);
        FeriaPiezas.caja(ps, vc, "negro", 0, 0, 0.0, 0.7, 0.2, 0.04, luz);
        ps.translate(0, 0, 0.03);
        ps.scale(0.0075F, -0.0075F, 0.0075F);
        // con dos jugadores, el que tira lleva la flechita
        String s;
        if (be.jugadores <= 0) s = "301";
        else if (be.jugadores == 1) s = be.nom[0] + ": " + be.puntos[0] + (be.ultimo.isEmpty() ? "" : "  (" + be.ultimo + ")");
        else {
            int toca = be.dardos >= 3 && !be.fin ? (be.actual + 1) % 2 : be.actual;
            s = (toca == 0 ? "> " : "") + be.nom[0] + " " + be.puntos[0] + "   " + (toca == 1 ? "> " : "") + be.nom[1] + " " + be.puntos[1];
        }
        float esc = Math.min(1, 86F / Math.max(1, font.width(s)));
        ps.scale(esc, esc, esc);
        font.drawInBatch(s, -font.width(s) / 2F, -4, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        ps.popPose();
    }
}
