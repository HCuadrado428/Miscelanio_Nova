package com.miscelanea.client.torre;

import com.miscelanea.client.feria.FeriaPiezas;
import com.miscelanea.torre.TorreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

import java.util.Random;

import static com.miscelanea.torre.TorreBlockEntity.*;

/** La torre (con su tembleque tras cada pieza) o, si se ha caído, las piezas volando y desparramadas. */
public class TorreRenderer implements BlockEntityRenderer<TorreBlockEntity> {
    public TorreRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public boolean shouldRenderOffScreen(TorreBlockEntity be) {
        return true;
    }

    @Override
    public void render(TorreBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        float t = be.getLevel().getGameTime() + pt;
        ps.pushPose();
        ps.translate(0.5, BASE, 0.5);
        if (be.derrumbe >= 0) {
            derrumbe(be, ps, vc, t - be.derrumbe, luz);
            ps.popPose();
            return;
        }
        float dt = t - be.movido;
        if (dt < 40) {
            float a = (0.6F + be.riesgo * 5) * (1 - dt / 40) * Mth.sin(dt * 0.9F);
            ps.mulPose(Axis.ZP.rotationDegrees(a));
            ps.mulPose(Axis.XP.rotationDegrees(a * 0.6F));
        }
        for (int n = 0; n < be.niveles.size(); n++) {
            int m = be.niveles.get(n);
            for (int k = 0; k < 3; k++) {
                if ((m & (1 << k)) == 0) continue;
                float off = (k - 1) * ANCHO;
                pieza(ps, vc, n % 2 == 0 ? 0 : off, n * ALTO + ALTO / 2, n % 2 == 0 ? off : 0, n % 2 == 1, 0, 0, (n * 3 + k) % 3, luz);
            }
        }
        ps.popPose();
    }

    private void pieza(PoseStack ps, VertexConsumer vc, double x, double y, double z, boolean girada, float rotY, float rotX, int tono, int luz) {
        ps.pushPose();
        ps.translate(x, y, z);
        if (girada) ps.mulPose(Axis.YP.rotationDegrees(90));
        ps.mulPose(Axis.YP.rotationDegrees(rotY));
        ps.mulPose(Axis.XP.rotationDegrees(rotX));
        ps.scale(LARGO - 0.004F, (ALTO - 0.003F) * 16, (ANCHO - 0.004F) * 16);
        ps.translate(-0.5, -0.5, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.barra("torre" + tono), 1, 1, 1, luz);
        ps.popPose();
    }

    private void derrumbe(TorreBlockEntity be, PoseStack ps, VertexConsumer vc, float t, int luz) {
        Random r = new Random(be.semilla);
        int total = be.niveles.size();
        double g = 0.03;
        for (int n = 0; n < total; n++) {
            int m = be.niveles.get(n);
            float h = (float) n / Math.max(1, total);
            for (int k = 0; k < 3; k++) {
                double ang = r.nextDouble() * Math.PI * 2, vel = (0.015 + r.nextDouble() * 0.04) * (0.3 + h * 1.4);
                double vy = r.nextDouble() * 0.03 * h, giro = (r.nextDouble() - 0.5) * 30, vuelta = (r.nextDouble() - 0.5) * 25;
                if ((m & (1 << k)) == 0) continue;
                float off = (k - 1) * ANCHO;
                double x0 = n % 2 == 0 ? 0 : off, z0 = n % 2 == 0 ? off : 0, y0 = n * ALTO + ALTO / 2;
                float tt = Math.max(0, t - (1 - h) * 4);
                // vuelo hasta tocar la mesa (o el suelo si sale de ella)
                double x = x0 + Math.cos(ang) * vel * tt, z = z0 + Math.sin(ang) * vel * tt;
                double suelo = (Math.abs(x) < 0.36 && Math.abs(z) < 0.36) ? ALTO / 2 + (n % 4) * 0.012 : -BASE + ALTO / 2;
                double y = y0 + vy * tt - 0.5 * g * tt * tt;
                double tSuelo = (vy + Math.sqrt(vy * vy + 2 * g * Math.max(0, y0 - suelo))) / g;
                float rY, rX;
                if (y <= suelo) {
                    y = suelo;
                    x = x0 + Math.cos(ang) * vel * tSuelo;
                    z = z0 + Math.sin(ang) * vel * tSuelo;
                    if (!(Math.abs(x) < 0.36 && Math.abs(z) < 0.36) && suelo > 0) y = -BASE + ALTO / 2;
                    rY = (float) (giro * tSuelo);
                    rX = 0;
                } else {
                    rY = (float) (giro * tt);
                    rX = (float) (vuelta * tt);
                }
                pieza(ps, vc, x, y, z, n % 2 == 1, rY, rX, (n * 3 + k) % 3, luz);
            }
        }
    }
}
