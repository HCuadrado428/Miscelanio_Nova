package com.miscelanea.client.feria;

import com.miscelanea.feria.AtraccionBlock;
import com.miscelanea.feria.AtraccionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** Estructura de la noria (patas, rueda con radios y bombillas) y del tiovivo (plataforma, barras, toldo). */
public class AtraccionRenderer implements BlockEntityRenderer<AtraccionBlockEntity> {
    public AtraccionRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public boolean shouldRenderOffScreen(AtraccionBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(AtraccionBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        float t = be.getLevel().getGameTime() + pt;
        float ang = be.angulo(t);
        int brillo = Math.max(luz & 0xFFFF, 0xA0) | (luz & 0xFFFF0000);
        if (be.tipo() == AtraccionBlock.NORIA) noria(be, ps, vc, t, ang, brillo);
        else tiovivo(be, ps, vc, t, ang, brillo);
    }

    private void noria(AtraccionBlockEntity be, PoseStack ps, VertexConsumer vc, float t, float ang, int luz) {
        Direction f = be.facing(), u = f.getClockWise();
        double ux = u.getStepX(), uz = u.getStepZ(), fx = f.getStepX(), fz = f.getStepZ();
        double cx = 0.5, cy = 8.5, cz = 0.5, R = 6;
        // patas en A a cada lado de la rueda
        for (int lado = -1; lado <= 1; lado += 2) {
            double ox = fx * lado * 0.9, oz = fz * lado * 0.9;
            for (int pie = -1; pie <= 1; pie += 2)
                FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * pie * 3.6, 0, cz + oz + uz * pie * 3.6, cx + ox, cy, cz + oz, 0.3F, 0.3F, luz);
            FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox - ux * 1.8, 4.25, cz + oz - uz * 1.8, cx + ox + ux * 1.8, 4.25, cz + oz + uz * 1.8, 0.18F, 0.18F, luz);
        }
        // eje
        FeriaPiezas.barra(ps, vc, "dorado", cx - fx * 1.1, cy, cz - fz * 1.1, cx + fx * 1.1, cy, cz + fz * 1.1, 0.4F, 0.4F, luz);
        // rueda: dos aros, radios y travesaños
        int seg = 24, n = be.asientos();
        for (int cara = -1; cara <= 1; cara += 2) {
            double ox = fx * cara * 0.55, oz = fz * cara * 0.55;
            for (int k = 0; k < seg; k++) {
                double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
                FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox + ux * Math.cos(a0) * R, cy + Math.sin(a0) * R, cz + oz + uz * Math.cos(a0) * R,
                        cx + ox + ux * Math.cos(a1) * R, cy + Math.sin(a1) * R, cz + oz + uz * Math.cos(a1) * R, 0.2F, 0.2F, luz);
                double ri = R * 0.55;
                FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * Math.cos(a0) * ri, cy + Math.sin(a0) * ri, cz + oz + uz * Math.cos(a0) * ri,
                        cx + ox + ux * Math.cos(a1) * ri, cy + Math.sin(a1) * ri, cz + oz + uz * Math.cos(a1) * ri, 0.12F, 0.12F, luz);
            }
            for (int k = 0; k < n * 2; k++) {
                double a = ang + Math.PI * k / n;
                FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox, cy, cz + oz, cx + ox + ux * Math.cos(a) * R, cy + Math.sin(a) * R, cz + oz + uz * Math.cos(a) * R,
                        0.12F, 0.12F, luz);
            }
        }
        for (int k = 0; k < n; k++) {
            double a = ang + 2 * Math.PI * k / n;
            double px = cx + ux * Math.cos(a) * R, py = cy + Math.sin(a) * R, pz = cz + uz * Math.cos(a) * R;
            FeriaPiezas.barra(ps, vc, "dorado", px - fx * 0.6, py, pz - fz * 0.6, px + fx * 0.6, py, pz + fz * 0.6, 0.16F, 0.16F, luz);
        }
        // bombillas por el aro y los radios
        int b = 0;
        for (int cara = -1; cara <= 1; cara += 2) {
            double ox = fx * cara * 0.68, oz = fz * cara * 0.68;
            for (int k = 0; k < 48; k++, b++) {
                double a = ang + 2 * Math.PI * k / 48;
                FeriaPiezas.bombilla(ps, vc, cx + ox + ux * Math.cos(a) * R, cy + Math.sin(a) * R, cz + oz + uz * Math.cos(a) * R, 0.16F,
                        FeriaPiezas.luz(k, t, be.gira));
            }
            for (int k = 0; k < n; k++)
                for (int j = 1; j <= 4; j++, b++) {
                    double a = ang + 2 * Math.PI * k / n + Math.PI / n, r = R * j / 5.0;
                    FeriaPiezas.bombilla(ps, vc, cx + ox + ux * Math.cos(a) * r, cy + Math.sin(a) * r, cz + oz + uz * Math.cos(a) * r, 0.13F,
                            FeriaPiezas.luz(j * 2 + k, t * 0.7F, be.gira));
                }
        }
        FeriaPiezas.bombilla(ps, vc, cx + fx * 1.15, cy, cz + fz * 1.15, 0.5F, FeriaPiezas.luz(0, t, be.gira));
        FeriaPiezas.bombilla(ps, vc, cx - fx * 1.15, cy, cz - fz * 1.15, 0.5F, FeriaPiezas.luz(3, t, be.gira));
    }

    private void tiovivo(AtraccionBlockEntity be, PoseStack ps, VertexConsumer vc, float t, float ang, int luz) {
        double cx = 0.5, cz = 0.5, R = 3.4, RT = 3.8, techo = 4.2, punta = 5.3;
        int seg = 16;
        // plataforma de tablas
        for (int k = 0; k < seg; k++) {
            double a = ang + 2 * Math.PI * (k + 0.5) / seg;
            FeriaPiezas.barra(ps, vc, k % 2 == 0 ? "madera_a" : "madera_b", cx, 0.12, cz, cx + Math.cos(a) * R, 0.12, cz + Math.sin(a) * R, 0.22F, 1.45F, luz);
        }
        for (int k = 0; k < seg; k++) {
            double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
            FeriaPiezas.barra(ps, vc, "dorado", cx + Math.cos(a0) * R, 0.2, cz + Math.sin(a0) * R, cx + Math.cos(a1) * R, 0.2, cz + Math.sin(a1) * R, 0.3F, 0.12F, luz);
        }
        // mástil central y toldo
        FeriaPiezas.barra(ps, vc, "metal_blanco", cx, 1, cz, cx, punta, cz, 0.7F, 0.7F, luz);
        for (int k = 0; k < 6; k++)
            FeriaPiezas.barra(ps, vc, k % 2 == 0 ? "metal_rojo" : "dorado", cx, 1.4 + k * 0.6, cz, cx, 1.7 + k * 0.6, cz, 0.78F, 0.78F, luz);
        for (int k = 0; k < seg; k++) {
            double a = ang + 2 * Math.PI * (k + 0.5) / seg;
            FeriaPiezas.barra(ps, vc, k % 2 == 0 ? "lona_roja" : "lona_blanca", cx, punta, cz, cx + Math.cos(a) * RT, techo, cz + Math.sin(a) * RT, 0.08F, 1.6F, luz);
        }
        for (int k = 0; k < seg; k++) {
            double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
            String c = k % 2 == 0 ? "lona_roja" : "lona_blanca";
            FeriaPiezas.barra(ps, vc, c, cx + Math.cos(a0) * RT, techo - 0.25, cz + Math.sin(a0) * RT, cx + Math.cos(a1) * RT, techo - 0.25, cz + Math.sin(a1) * RT, 0.5F, 0.06F, luz);
            FeriaPiezas.barra(ps, vc, "dorado", cx + Math.cos(a0) * RT, techo - 0.52, cz + Math.sin(a0) * RT, cx + Math.cos(a1) * RT, techo - 0.52, cz + Math.sin(a1) * RT, 0.06F, 0.08F, luz);
        }
        for (int k = 0; k < 32; k++) {
            double a = ang + 2 * Math.PI * k / 32;
            FeriaPiezas.bombilla(ps, vc, cx + Math.cos(a) * (RT + 0.06), techo - 0.6, cz + Math.sin(a) * (RT + 0.06), 0.15F, FeriaPiezas.luz(k, t, be.gira));
        }
        FeriaPiezas.bombilla(ps, vc, cx, punta + 0.25, cz, 0.45F, FeriaPiezas.luz(1, t, be.gira));
        // barras doradas de los caballitos (los caballitos son las entidades que suben y bajan)
        int n = be.asientos();
        for (int k = 0; k < n; k++) {
            double a = ang + 2 * Math.PI * k / n;
            double x = cx + Math.cos(a) * 2.6, z = cz + Math.sin(a) * 2.6;
            FeriaPiezas.barra(ps, vc, "dorado", x, 0.25, z, x, techo - 0.1, z, 0.1F, 0.1F, luz);
        }
    }
}
