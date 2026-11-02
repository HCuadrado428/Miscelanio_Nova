package com.miscelanea.client.feria;

import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.miscelanea.feria.PuestoFeriaBlock;
import com.miscelanea.feria.PuestoFeriaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

/** Patos que dan vueltas en el estanque y el bombo de la tómbola con su número. */
public class PuestoFeriaRenderer implements BlockEntityRenderer<PuestoFeriaBlockEntity> {
    private final Font font;

    public PuestoFeriaRenderer(BlockEntityRendererProvider.Context c) {
        font = c.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(PuestoFeriaBlockEntity be) {
        return true;
    }

    @Override
    public void render(PuestoFeriaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        long ahora = be.getLevel().getGameTime();
        double t = ahora + pt;
        int brillo = Math.max(luz & 0xFFFF, 0xB0) | (luz & 0xFFFF0000);
        if (be.tipo() == PuestoFeriaBlock.PATITOS) {
            for (int i = 0; i < PuestoFeriaBlockEntity.PATOS; i++) {
                double[] q = PuestoFeriaBlockEntity.pato(i, t);
                double dt = t - be.pescado[i];
                double sube = dt < 120 ? (dt < 15 ? dt / 15 * 0.9 : dt < 90 ? 0.9 : 0.9 * (1 - (dt - 90) / 30)) : 0;
                ps.pushPose();
                ps.translate(0.5 + q[0], q[1] + sube, 0.5 + q[2]);
                ps.mulPose(Axis.YP.rotation((float) (-q[3] + Math.PI)));
                if (sube > 0) ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(dt * 0.4) * 15));
                ps.scale(0.5F, 0.5F, 0.5F);
                ps.translate(-0.5, 0, -0.5);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl(i % 5 == 0 ? "pato_oro" : "pato"), 1, 1, 1, brillo);
                ps.popPose();
                if (sube > 0.3) {
                    ps.pushPose();
                    ps.translate(0.5 + q[0], q[1] + sube - 0.08, 0.5 + q[2]);
                    ps.mulPose(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
                    ps.scale(-0.02F, -0.02F, 0.02F);
                    String n = String.valueOf(be.premioPato[i] + 1);
                    font.drawInBatch(n, -font.width(n) / 2F, 0, 0xFFFFE070, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0xA0000000, 0xF000F0);
                    ps.popPose();
                    FeriaPiezas.barra(ps, vc, "negro", 0.5 + q[0], q[1] + sube + 0.25, 0.5 + q[2], 0.5 + q[0], q[1] + sube + 1.2, 0.5 + q[2], 0.01F, 0.01F, luz);
                }
            }
            return;
        }
        if (be.tipo() != PuestoFeriaBlock.TOMBOLA) return;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(CasetaTiroBlockEntity.giro(be.facing())));
        double dt = be.activo ? t - be.inicio : 999;
        float giro = dt < 55 ? (float) (dt * dt * 0.12) : 0;
        // bombo: jaula de alambre que gira con bolas dentro
        ps.pushPose();
        ps.translate(0, 1.35, 0.05);
        ps.mulPose(Axis.XP.rotationDegrees(giro));
        for (int k = 0; k < 8; k++) {
            double a0 = k * Math.PI / 4, a1 = (k + 1) * Math.PI / 4, r = 0.3;
            for (double x : new double[]{-0.25, 0.25})
                FeriaPiezas.barra(ps, vc, "dorado", x, Math.cos(a0) * r, Math.sin(a0) * r, x, Math.cos(a1) * r, Math.sin(a1) * r, 0.03F, 0.03F, luz);
            FeriaPiezas.barra(ps, vc, "cromo", -0.25, Math.cos(a0) * r, Math.sin(a0) * r, 0.25, Math.cos(a0) * r, Math.sin(a0) * r, 0.02F, 0.02F, luz);
        }
        ps.popPose();
        for (int k = 0; k < 12; k++) {
            double a = k * 2.4 + (dt < 55 ? dt * 0.4 : 0), rr = 0.12 + (k % 3) * 0.05;
            FeriaPiezas.bombilla(ps, vc, (k % 5 - 2) * 0.08, 1.3 + Math.cos(a) * rr * (dt < 55 ? 1 : 0.5) - (dt < 55 ? 0 : 0.12), 0.05 + Math.sin(a) * rr,
                    0.08F, new int[]{0xFF4040, 0xFFE040, 0x40A0FF, 0x60E060, 0xFFFFFF}[k % 5]);
        }
        FeriaPiezas.barra(ps, vc, "metal_rojo", -0.32, 1.0, 0.05, -0.32, 1.35, 0.05, 0.05F, 0.05F, luz);
        FeriaPiezas.barra(ps, vc, "metal_rojo", 0.32, 1.0, 0.05, 0.32, 1.35, 0.05, 0.05F, 0.05F, luz);
        // número que sale
        String txt = be.activo && dt >= 55 ? String.valueOf(be.numero) : be.activo ? "..." : I18n.get("miscelanea.tombola.cartel");
        ps.pushPose();
        ps.translate(0, 1.95, -0.1);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(0.02F, -0.02F, 0.02F);
        float e = Math.min(1.6F, 46F / Math.max(1, font.width(txt)));
        ps.scale(e, e, e);
        int col = be.activo && dt >= 55 ? ((int) (t / 4) % 2 == 0 ? 0xFFFFE070 : 0xFFFF6040) : 0xFFFFFFFF;
        font.drawInBatch(txt, -font.width(txt) / 2F, 0, col, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0xC0400000, 0xF000F0);
        ps.popPose();
        for (int k = 0; k < 8; k++) FeriaPiezas.bombilla(ps, vc, -0.42 + k * 0.12, 1.86, -0.12, 0.045F, FeriaPiezas.luz(k, (float) t, be.activo));
        ps.popPose();
        Mth.sin(0);
    }
}
