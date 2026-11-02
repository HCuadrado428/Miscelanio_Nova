package com.miscelanea.client.torneo;

import com.miscelanea.torneo.TorneoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/** Lo que se lee en el tablón: título, inscritos o cruces pendientes, y el campeón. */
public class TorneoRenderer implements BlockEntityRenderer<TorneoBlockEntity> {
    private final Font font;

    public TorneoRenderer(BlockEntityRendererProvider.Context c) {
        font = c.getFont();
    }

    @Override
    public void render(TorneoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        List<String> l = new ArrayList<>();
        List<Integer> c = new ArrayList<>();
        l.add(be.titulo.isBlank() ? I18n.get("miscelanea.torneo.titulo") : be.titulo);
        c.add(0xFF7A1A10);
        if (be.estado == TorneoBlockEntity.INSCRIPCION) {
            l.add(I18n.get("miscelanea.torneo.inscritos", be.nombres.size(), TorneoBlockEntity.MAX));
            c.add(0xFF203040);
            for (int i = 0; i < Math.min(7, be.nombres.size()); i++) {
                l.add(be.nombres.get(i));
                c.add(0xFF202020);
            }
        } else if (be.estado == TorneoBlockEntity.TERMINADO && be.campeon >= 0) {
            l.add(I18n.get("miscelanea.torneo.campeon_col"));
            c.add(0xFF203040);
            l.add("★ " + be.nombres.get(be.campeon) + " ★");
            c.add(0xFFB07A00);
        } else {
            for (int r = 0; r + 1 < be.rondas.size() && l.size() < 8; r++) {
                int[] a = be.rondas.get(r), sig = be.rondas.get(r + 1);
                for (int m = 0; m < sig.length && l.size() < 8; m++)
                    if (sig[m] == -2 && a[2 * m] >= 0 && a[2 * m + 1] >= 0) {
                        l.add(be.nombres.get(a[2 * m]) + " vs " + be.nombres.get(a[2 * m + 1]));
                        c.add(0xFF202020);
                    }
            }
        }
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-be.facing().toYRot()));
        ps.translate(0, 0.36, -0.37);
        ps.scale(0.0075F, -0.0075F, 0.0075F);
        for (int i = 0; i < l.size(); i++) {
            String s = l.get(i);
            float esc = Math.min(i == 0 ? 1.3F : 1F, 100F / Math.max(1, font.width(s)));
            ps.pushPose();
            ps.translate(0, i == 0 ? 0 : 14 + (i - 1) * 11, 0);
            ps.scale(esc, esc, esc);
            font.drawInBatch(s, -font.width(s) / 2F, 0, c.get(i), false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, luz);
            ps.popPose();
        }
        ps.popPose();
    }
}
