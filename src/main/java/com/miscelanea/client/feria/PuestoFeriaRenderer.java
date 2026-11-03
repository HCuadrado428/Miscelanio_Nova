package com.miscelanea.client.feria;

import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.miscelanea.feria.PuestoFeriaBlock;
import com.miscelanea.feria.PuestoFeriaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * Lo que se mueve en los puestos: patos que dan vueltas en el estanque (con su tablilla de premio al pescarlos), el
 * bombo y la pantalla de dígitos de la tómbola, los carteles de tejado y las ristras de bombillas.
 */
public class PuestoFeriaRenderer implements BlockEntityRenderer<PuestoFeriaBlockEntity> {
    public PuestoFeriaRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public boolean shouldRenderOffScreen(PuestoFeriaBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(PuestoFeriaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        long ahora = be.getLevel().getGameTime();
        double t = ahora + pt;
        float tf = (float) t;
        int brillo = Math.max(luz & 0xFFFF, 0xB0) | (luz & 0xFFFF0000);
        switch (be.tipo()) {
            case PuestoFeriaBlock.PATITOS -> patitos(be, ps, vc, t, brillo);
            case PuestoFeriaBlock.TOMBOLA -> tombola(be, ps, vc, t, brillo, luz);
            default -> {
                // churrería: cartel en el tejado y bombillas en el faldón
                ps.pushPose();
                ps.translate(0.5, 0, 0.5);
                ps.mulPose(Axis.YP.rotation(CasetaTiroBlockEntity.giro(be.facing())));
                PuestosPiezas.copete(ps, vc, PuestosPiezas.COPETE_CHURROS, 0, 1.94, -0.5, 1.15F, brillo);
                PuestosPiezas.ristra(ps, vc, 1.45, 1.87, -0.79, -1.45, 1.87, -0.79, 14, 0.075F, tf, PuestosPiezas.REPOSO, 0);
                ps.popPose();
            }
        }
    }

    private void patitos(PuestoFeriaBlockEntity be, PoseStack ps, VertexConsumer vc, double t, int brillo) {
        float tf = (float) t;
        long ahora = be.getLevel().getGameTime();
        // ¿alguien acaba de pescar un premio gordo? entonces las luces celebran
        boolean fiesta = false;
        for (int i = 0; i < PuestoFeriaBlockEntity.PATOS; i++)
            if (ahora - be.pescado[i] < 60 && be.premioPato[i] >= 3) fiesta = true;
        int modo = fiesta ? PuestosPiezas.PREMIO : be.activo ? PuestosPiezas.JUEGO : PuestosPiezas.REPOSO;
        for (int i = 0; i < PuestoFeriaBlockEntity.PATOS; i++) {
            double[] q = PuestoFeriaBlockEntity.pato(i, t);
            double dt = t - be.pescado[i];
            double sube = dt < 120 ? (dt < 15 ? dt / 15 * 0.9 : dt < 90 ? 0.9 : 0.9 * (1 - (dt - 90) / 30)) : 0;
            ps.pushPose();
            ps.translate(0.5 + q[0], q[1] + sube, 0.5 + q[2]);
            ps.mulPose(Axis.YP.rotation((float) (-q[3] + Math.PI)));
            if (sube > 0) ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(dt * 0.4) * 15));
            else ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(tf * 0.2F + i * 1.3F) * 5));
            if (sube > 0.3) {
                // la tablilla con el número del premio, colgada debajo del pato (se ve por los dos lados)
                FeriaPiezas.caja(ps, vc, "lona_blanca", 0, -0.12, 0, 0.2, 0.17, 0.02, brillo);
                String n = String.valueOf(be.premioPato[i] + 1);
                int col = be.premioPato[i] >= 3 ? 0xFFC020 : 0xE02020;
                PuestosPiezas.led(ps, vc, n, 0, -0.12, -0.012, 0.15, col, false);
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(180));
                PuestosPiezas.led(ps, vc, n, 0, -0.12, -0.012, 0.15, col, false);
                ps.popPose();
            }
            ps.scale(0.5F, 0.5F, 0.5F);
            ps.translate(-0.5, 0, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl(i % 5 == 0 ? "pato_oro" : "pato"), 1, 1, 1, brillo);
            ps.popPose();
            if (sube > 0.3)
                FeriaPiezas.barra(ps, vc, "negro", 0.5 + q[0], q[1] + sube + 0.25, 0.5 + q[2], 0.5 + q[0], q[1] + sube + 1.2, 0.5 + q[2], 0.01F, 0.01F, brillo);
        }
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(CasetaTiroBlockEntity.giro(be.facing())));
        PuestosPiezas.copete(ps, vc, PuestosPiezas.COPETE_PATITOS, 0, 1.96, -1.3, 1.6F, brillo);
        // bombillas por los cuatro lados del toldo (una sola ristra que da la vuelta)
        double e = 1.53, y = 1.865;
        PuestosPiezas.ristra(ps, vc, 1.4, y, -e, -1.4, y, -e, 12, 0.08F, tf, modo, 0);
        PuestosPiezas.ristra(ps, vc, -e, y, -1.4, -e, y, 1.4, 12, 0.08F, tf, modo, 12);
        PuestosPiezas.ristra(ps, vc, -1.4, y, e, 1.4, y, e, 12, 0.08F, tf, modo, 24);
        PuestosPiezas.ristra(ps, vc, e, y, 1.4, e, y, -1.4, 12, 0.08F, tf, modo, 36);
        // peluches colgados en las esquinas
        for (int k = 0; k < 4; k++) {
            double x = (k % 2 == 0 ? -1 : 1) * 1.25, z = (k < 2 ? -1 : 1) * 1.25;
            PuestosPiezas.pelucheColgado(ps, vc, k, x, 1.86, z, 0.22, tf, k * 1.7F, 0.38F, brillo);
        }
        ps.popPose();
    }

    private void tombola(PuestoFeriaBlockEntity be, PoseStack ps, VertexConsumer vc, double t, int brillo, int luz) {
        float tf = (float) t;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(CasetaTiroBlockEntity.giro(be.facing())));
        double dt = be.activo ? t - be.inicio : 999;
        int n = be.numero;
        int tier = n == 100 ? 4 : n > 90 ? 3 : n > 70 ? 2 : n > 40 ? 1 : 0;
        boolean fiesta = be.activo && dt >= 60 && tier >= 2;
        int modo = fiesta ? PuestosPiezas.PREMIO : be.activo ? PuestosPiezas.JUEGO : PuestosPiezas.REPOSO;
        // en reposo el bombo se mece despacio; al jugar gira cada vez más deprisa
        float giro = dt < 55 ? (float) (dt * dt * 0.12) : (float) Math.sin(t * 0.03) * 10;
        // bombo: jaula de alambre que gira con bolas dentro
        ps.pushPose();
        ps.translate(0, 1.35, 0.05);
        ps.mulPose(Axis.XP.rotationDegrees(giro));
        for (int k = 0; k < 12; k++) {
            double a0 = k * Math.PI / 6, a1 = (k + 1) * Math.PI / 6, r = 0.3;
            for (double x : new double[]{-0.25, 0, 0.25})
                FeriaPiezas.barra(ps, vc, x == 0 ? "cromo" : "dorado", x, Math.cos(a0) * r, Math.sin(a0) * r, x, Math.cos(a1) * r, Math.sin(a1) * r, x == 0 ? 0.015F : 0.035F, x == 0 ? 0.015F : 0.035F, luz);
            FeriaPiezas.barra(ps, vc, "cromo", -0.25, Math.cos(a0) * r, Math.sin(a0) * r, 0.25, Math.cos(a0) * r, Math.sin(a0) * r, 0.015F, 0.015F, luz);
        }
        // tapas con radios, eje y manivela
        for (double x : new double[]{-0.26, 0.26})
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 4;
                FeriaPiezas.barra(ps, vc, "dorado", x, Math.cos(a) * 0.3, Math.sin(a) * 0.3, x, -Math.cos(a) * 0.3, -Math.sin(a) * 0.3, 0.02F, 0.02F, luz);
            }
        FeriaPiezas.barra(ps, vc, "cromo", -0.36, 0, 0, 0.42, 0, 0, 0.04F, 0.04F, luz);
        FeriaPiezas.barra(ps, vc, "metal_rojo", 0.42, 0, 0, 0.42, 0.14, 0, 0.035F, 0.035F, luz);
        FeriaPiezas.barra(ps, vc, "madera_b", 0.42, 0.14, 0, 0.5, 0.14, 0, 0.04F, 0.04F, luz);
        ps.popPose();
        for (int k = 0; k < 12; k++) {
            double a = k * 2.4 + (dt < 55 ? dt * 0.4 : 0), rr = 0.12 + (k % 3) * 0.05;
            FeriaPiezas.bombilla(ps, vc, (k % 5 - 2) * 0.08, 1.3 + Math.cos(a) * rr * (dt < 55 ? 1 : 0.5) - (dt < 55 ? 0 : 0.12), 0.05 + Math.sin(a) * rr,
                    0.08F, new int[]{0xFF4040, 0xFFE040, 0x40A0FF, 0x60E060, 0xFFFFFF}[k % 5]);
        }
        FeriaPiezas.barra(ps, vc, "metal_rojo", -0.32, 1.0, 0.05, -0.32, 1.35, 0.05, 0.05F, 0.05F, luz);
        FeriaPiezas.barra(ps, vc, "metal_rojo", 0.32, 1.0, 0.05, 0.32, 1.35, 0.05, 0.05F, 0.05F, luz);
        // número en la pantalla de dígitos del panel: en reposo una raya que corre, girando números al azar
        // y al final el número que ha salido (parpadea si trae premio)
        String txt;
        if (!be.activo) {
            int p = (int) (t / 6) % 4;
            txt = p == 0 ? "-  " : p == 1 ? " - " : p == 2 ? "  -" : "   ";
        } else if (dt < 55) txt = String.format("%3d", 1 + Math.floorMod((int) (t / 2) * 37 + 11, 100));
        else txt = fiesta && ((int) (t / 3) & 1) == 0 ? "   " : String.format("%3d", n);
        PuestosPiezas.led(ps, vc, txt, 0, 1.81, -0.086, 0.24, fiesta ? 0xFFD040 : 0xFF4020, true);
        // cartel del tejado y bombillas del faldón
        PuestosPiezas.copete(ps, vc, PuestosPiezas.COPETE_TOMBOLA, 0, 1.995, -0.42, 1F, brillo);
        PuestosPiezas.ristra(ps, vc, 0.47, 1.91, -0.58, -0.47, 1.91, -0.58, 8, 0.07F, tf, modo, 0);
        ps.popPose();
    }
}
