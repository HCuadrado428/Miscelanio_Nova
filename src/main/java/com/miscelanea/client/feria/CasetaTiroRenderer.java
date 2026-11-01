package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static com.miscelanea.feria.CasetaTiroBlockEntity.*;

/** La caseta: lonas a rayas, toldo con bombillas, cartel con puntos y tiempo, fila de patos y fila de dianas. */
public class CasetaTiroRenderer implements BlockEntityRenderer<CasetaTiroBlockEntity> {
    public static final ResourceLocation[] PELUCHE = new ResourceLocation[4];

    static {
        for (int i = 0; i < 4; i++) PELUCHE[i] = new ResourceLocation(Miscelanea.MOD_ID, "block/peluche" + i);
    }

    private final Font font;

    public CasetaTiroRenderer(BlockEntityRendererProvider.Context c) {
        font = c.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(CasetaTiroBlockEntity be) {
        return true;
    }

    @Override
    public void render(CasetaTiroBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        long ahoraL = be.getLevel().getGameTime();
        float t = ahoraL + pt;
        boolean activa = be.activa(ahoraL);
        int brillo = Math.max(luz & 0xFFFF, 0xB0) | (luz & 0xFFFF0000);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(giro(be.facing())));
        // estructura
        for (int lado = -1; lado <= 1; lado += 2) {
            FeriaPiezas.caja(ps, vc, "metal_rojo", lado * 1.75, 1.7, 0.45, 0.14, 3.4, 0.14, brillo);
            FeriaPiezas.caja(ps, vc, "metal_rojo", lado * 1.75, 1.7, 3.05, 0.14, 3.4, 0.14, brillo);
            FeriaPiezas.caja(ps, vc, "lona_roja", lado * 1.75, 1.5, 1.75, 0.06, 3.0, 2.6, brillo);
            // estante de premios
            FeriaPiezas.caja(ps, vc, "madera_a", lado * 1.5, 2.55, 1.0, 0.4, 0.06, 0.9, brillo);
            for (int k = 0; k < 2; k++) {
                ps.pushPose();
                ps.translate(lado * 1.5, 2.58, 0.8 + k * 0.45);
                ps.mulPose(Axis.YP.rotationDegrees(lado * -70));
                ps.scale(0.55F, 0.55F, 0.55F);
                ps.translate(-0.5, 0, -0.5);
                FeriaPiezas.modelo(ps, vc, PELUCHE[(k + (lado > 0 ? 2 : 0)) % 4], 1, 1, 1, brillo);
                ps.popPose();
            }
        }
        for (int i = 0; i < 7; i++) {
            double x = -1.75 + 0.25 + i * 0.5;
            String c = i % 2 == 0 ? "lona_roja" : "lona_blanca";
            FeriaPiezas.caja(ps, vc, c, x, 1.6, 3.05, 0.5, 3.2, 0.06, brillo);
            FeriaPiezas.caja(ps, vc, c, x, 3.38, 1.4, 0.5, 0.08, 3.4, brillo);
            FeriaPiezas.caja(ps, vc, c, x, 3.15, -0.3, 0.5, 0.42, 0.06, brillo);
            FeriaPiezas.bombilla(ps, vc, x, 2.9, -0.34, 0.14F, FeriaPiezas.luz(i, t, activa));
        }
        // filas de blancos
        FeriaPiezas.caja(ps, vc, "madera_b", 0, PATO_Y - 0.05, PATO_Z, ANCHO, 0.1, 0.25, brillo);
        FeriaPiezas.caja(ps, vc, "agua", 0, PATO_Y + 0.07, PATO_Z - 0.2, ANCHO, 0.24, 0.05, brillo);
        FeriaPiezas.caja(ps, vc, "metal_blanco", 0, DIANA_Y, DIANA_Z + 0.06, ANCHO, 0.06, 0.06, brillo);
        for (int k = 0; k < BLANCOS; k++) {
            double x = CasetaTiroBlockEntity.x(k, t);
            if (Math.abs(x) > VISIBLE) continue;
            float caida = t - be.caido[k];
            if (caida >= 8 && caida < VUELVE) continue;
            float giro = caida < 8 ? Math.min(1, caida / 5) * 85 : 0;
            ps.pushPose();
            if (k < PATOS) {
                ps.translate(x, PATO_Y, PATO_Z);
                ps.mulPose(Axis.XP.rotationDegrees(giro));
                ps.translate(-0.5, 0, -0.5);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl(dorado(k, ahoraL) ? "pato_oro" : "pato"), 1, 1, 1, brillo);
            } else {
                ps.translate(x, DIANA_Y, DIANA_Z);
                ps.mulPose(Axis.XP.rotationDegrees(giro));
                ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.08F + k) * 20));
                ps.translate(-0.5, 0, -0.5);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("diana"), 1, 1, 1, brillo);
            }
            ps.popPose();
        }
        // cartel
        FeriaPiezas.caja(ps, vc, "madera_a", 0, 3.8, -0.32, 2.2, 0.62, 0.08, brillo);
        FeriaPiezas.caja(ps, vc, "dorado", 0, 4.13, -0.32, 2.3, 0.05, 0.1, brillo);
        FeriaPiezas.caja(ps, vc, "dorado", 0, 3.47, -0.32, 2.3, 0.05, 0.1, brillo);
        ps.pushPose();
        ps.translate(0, 3.8, -0.37);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(0.018F, -0.018F, 0.018F);
        String titulo = I18n.get("miscelanea.tiro.cartel");
        String linea = activa ? I18n.get("miscelanea.tiro.marcador", be.puntos, Math.max(0, (DURA - (ahoraL - be.inicio)) / 20))
                : I18n.get("miscelanea.tiro.record", be.record);
        texto(ps, buf, titulo, -12, 0xFFF2C14E, 1.4F);
        texto(ps, buf, linea, 4, activa ? 0xFFFFFFFF : 0xFFE8D8B0, 1F);
        ps.popPose();
        ps.popPose();
    }

    private void texto(PoseStack ps, MultiBufferSource buf, String s, float y, int color, float esc) {
        ps.pushPose();
        ps.scale(esc, esc, esc);
        font.drawInBatch(s, -font.width(s) / 2F, y / esc, color, true, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        ps.popPose();
    }
}
