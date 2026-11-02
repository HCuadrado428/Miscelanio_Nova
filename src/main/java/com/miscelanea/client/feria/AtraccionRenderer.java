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
        double tt = be.getLevel().getGameTime() + (double) pt;
        float t = (float) (be.getLevel().getGameTime() % 100000L) + pt;
        float ang = be.angulo(tt);
        int brillo = Math.max(luz & 0xFFFF, 0xA0) | (luz & 0xFFFF0000);
        if (be.tipo() == AtraccionBlock.NORIA) noria(be, ps, vc, t, ang, brillo);
        else if (be.tipo() == AtraccionBlock.PULPO) pulpo(be, ps, vc, t, ang, brillo);
        else tiovivo(be, ps, vc, t, ang, brillo);
    }

    private void noria(AtraccionBlockEntity be, PoseStack ps, VertexConsumer vc, float t, float ang, int luz) {
        Direction f = be.facing(), u = f.getClockWise();
        double ux = u.getStepX(), uz = u.getStepZ(), fx = f.getStepX(), fz = f.getStepZ();
        double cx = 0.5, cy = AtraccionBlockEntity.CY_NORIA, cz = 0.5, R = AtraccionBlockEntity.R_NORIA, w = AtraccionBlockEntity.MEDIA_RUEDA;
        // patas en A a cada lado de la rueda, con travesaños y escalera
        for (int lado = -1; lado <= 1; lado += 2) {
            double ox = fx * lado * (w + 0.45), oz = fz * lado * (w + 0.45);
            for (int pie = -1; pie <= 1; pie += 2) {
                FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * pie * 5.6, 0, cz + oz + uz * pie * 5.6, cx + ox, cy, cz + oz, 0.45F, 0.45F, luz);
                FeriaPiezas.caja(ps, vc, "metal_blanco", cx + ox + ux * pie * 5.6, 0.25, cz + oz + uz * pie * 5.6, 1.1, 0.5, 1.1, luz);
            }
            for (double h : new double[]{4.5, 9})
                FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox - ux * 5.6 * (1 - h / cy), h, cz + oz - uz * 5.6 * (1 - h / cy),
                        cx + ox + ux * 5.6 * (1 - h / cy), h, cz + oz + uz * 5.6 * (1 - h / cy), 0.22F, 0.22F, luz);
            for (int k = 0; k < 6; k++) {
                double h0 = 0.5 + k * 2, h1 = h0 + 2;
                FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox - ux * 5.6 * (1 - h0 / cy), h0, cz + oz - uz * 5.6 * (1 - h0 / cy),
                        cx + ox + ux * 5.6 * (1 - h1 / cy), h1, cz + oz + uz * 5.6 * (1 - h1 / cy), 0.12F, 0.12F, luz);
            }
        }
        // eje y buje
        FeriaPiezas.barra(ps, vc, "dorado", cx - fx * (w + 0.8), cy, cz - fz * (w + 0.8), cx + fx * (w + 0.8), cy, cz + fz * (w + 0.8), 0.55F, 0.55F, luz);
        FeriaPiezas.barra(ps, vc, "metal_rojo", cx - fx * (w + 0.1), cy, cz - fz * (w + 0.1), cx + fx * (w + 0.1), cy, cz + fz * (w + 0.1), 1.1F, 1.1F, luz);
        // rueda: dos caras con aro exterior, aro interior, radios dobles y tirantes
        int seg = 40, n = be.asientos();
        for (int cara = -1; cara <= 1; cara += 2) {
            double ox = fx * cara * w, oz = fz * cara * w;
            for (int k = 0; k < seg; k++) {
                double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
                for (double r : new double[]{R, R * 0.93})
                    FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox + ux * Math.cos(a0) * r, cy + Math.sin(a0) * r, cz + oz + uz * Math.cos(a0) * r,
                            cx + ox + ux * Math.cos(a1) * r, cy + Math.sin(a1) * r, cz + oz + uz * Math.cos(a1) * r, 0.22F, 0.22F, luz);
                double ri = R * 0.45;
                FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * Math.cos(a0) * ri, cy + Math.sin(a0) * ri, cz + oz + uz * Math.cos(a0) * ri,
                        cx + ox + ux * Math.cos(a1) * ri, cy + Math.sin(a1) * ri, cz + oz + uz * Math.cos(a1) * ri, 0.16F, 0.16F, luz);
            }
            for (int k = 0; k < n; k++) {
                double a = ang + 2 * Math.PI * k / n, b = a + Math.PI / n;
                FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox, cy, cz + oz, cx + ox + ux * Math.cos(a) * R, cy + Math.sin(a) * R, cz + oz + uz * Math.cos(a) * R, 0.14F, 0.14F, luz);
                // zigzag entre aros
                FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * Math.cos(a) * R * 0.45, cy + Math.sin(a) * R * 0.45, cz + oz + uz * Math.cos(a) * R * 0.45,
                        cx + ox + ux * Math.cos(b) * R * 0.93, cy + Math.sin(b) * R * 0.93, cz + oz + uz * Math.cos(b) * R * 0.93, 0.1F, 0.1F, luz);
                FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * Math.cos(b) * R * 0.93, cy + Math.sin(b) * R * 0.93, cz + oz + uz * Math.cos(b) * R * 0.93,
                        cx + ox + ux * Math.cos(a + 2 * Math.PI / n) * R * 0.45, cy + Math.sin(a + 2 * Math.PI / n) * R * 0.45, cz + oz + uz * Math.cos(a + 2 * Math.PI / n) * R * 0.45, 0.1F, 0.1F, luz);
            }
        }
        for (int k = 0; k < n; k++) {
            double a = ang + 2 * Math.PI * k / n;
            double px = cx + ux * Math.cos(a) * R, py = cy + Math.sin(a) * R, pz = cz + uz * Math.cos(a) * R;
            FeriaPiezas.barra(ps, vc, "dorado", px - fx * w, py, pz - fz * w, px + fx * w, py, pz + fz * w, 0.2F, 0.2F, luz);
            double b = a + Math.PI / n;
            double qx = cx + ux * Math.cos(b) * R * 0.93, qy = cy + Math.sin(b) * R * 0.93, qz = cz + uz * Math.cos(b) * R * 0.93;
            FeriaPiezas.barra(ps, vc, "metal_blanco", qx - fx * w, qy, qz - fz * w, qx + fx * w, qy, qz + fz * w, 0.12F, 0.12F, luz);
        }
        // bombillas por los aros y los radios, y estrella en el buje
        for (int cara = -1; cara <= 1; cara += 2) {
            double ox = fx * cara * (w + 0.14), oz = fz * cara * (w + 0.14);
            for (int k = 0; k < 80; k++) {
                double a = ang + 2 * Math.PI * k / 80;
                FeriaPiezas.bombilla(ps, vc, cx + ox + ux * Math.cos(a) * R, cy + Math.sin(a) * R, cz + oz + uz * Math.cos(a) * R, 0.2F, FeriaPiezas.luz(k, t, be.gira));
            }
            for (int k = 0; k < n; k++)
                for (int j = 1; j <= 6; j++) {
                    double a = ang + 2 * Math.PI * k / n, r = R * j / 7.0;
                    FeriaPiezas.bombilla(ps, vc, cx + ox + ux * Math.cos(a) * r, cy + Math.sin(a) * r, cz + oz + uz * Math.cos(a) * r, 0.16F,
                            FeriaPiezas.luz(j * 2 + k, t * 0.7F, be.gira));
                }
            for (int k = 0; k < 8; k++) {
                double a = -ang * 0.5 + Math.PI * k / 4;
                FeriaPiezas.bombilla(ps, vc, cx + fx * cara * (w + 0.62) + ux * Math.cos(a) * 0.5, cy + Math.sin(a) * 0.5, cz + fz * cara * (w + 0.62) + uz * Math.cos(a) * 0.5, 0.18F,
                        FeriaPiezas.luz(k, t * 1.3F, be.gira));
            }
            FeriaPiezas.bombilla(ps, vc, cx + fx * cara * (w + 0.62), cy, cz + fz * cara * (w + 0.62), 0.6F, FeriaPiezas.luz(cara + 2, t, be.gira));
        }
    }

    private void pulpo(AtraccionBlockEntity be, PoseStack ps, VertexConsumer vc, float t, float ang, int luz) {
        double cx = 0.5, cz = 0.5, hub = AtraccionBlockEntity.HUB_PULPO;
        // base octogonal y columna
        for (int k = 0; k < 8; k++) {
            double a0 = k * Math.PI / 4, a1 = (k + 1) * Math.PI / 4;
            FeriaPiezas.barra(ps, vc, k % 2 == 0 ? "metal_rojo" : "dorado", cx + Math.cos(a0) * 1.6, 0.15, cz + Math.sin(a0) * 1.6, cx + Math.cos(a1) * 1.6, 0.15, cz + Math.sin(a1) * 1.6, 0.3F, 0.3F, luz);
            FeriaPiezas.barra(ps, vc, "metal_blanco", cx, 0.12, cz, cx + Math.cos(a0 + 0.39) * 1.55, 0.12, cz + Math.sin(a0 + 0.39) * 1.55, 0.2F, 0.9F, luz);
        }
        FeriaPiezas.barra(ps, vc, "metal_rojo", cx, 0.2, cz, cx, hub, cz, 0.9F, 0.9F, luz);
        for (int k = 0; k < 5; k++) FeriaPiezas.barra(ps, vc, "dorado", cx, 0.6 + k * 0.6, cz, cx, 0.75 + k * 0.6, cz, 1.0F, 1.0F, luz);
        // cabeza del pulpo: cúpula con ojos que gira
        ps.pushPose();
        ps.translate(cx, hub, cz);
        ps.mulPose(com.mojang.math.Axis.YP.rotation(-ang));
        FeriaPiezas.caja(ps, vc, "color0", 0, 0.3, 0, 1.6, 0.6, 1.6, luz);
        FeriaPiezas.caja(ps, vc, "color0", 0, 0.75, 0, 1.2, 0.4, 1.2, luz);
        FeriaPiezas.caja(ps, vc, "color0", 0, 1.05, 0, 0.7, 0.3, 0.7, luz);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2;
            FeriaPiezas.bombilla(ps, vc, Math.cos(a) * 0.82, 0.5, Math.sin(a) * 0.82, 0.22F, 0xFFFFFF);
            FeriaPiezas.bombilla(ps, vc, Math.cos(a) * 0.9, 0.5, Math.sin(a) * 0.9, 0.1F, 0x101020);
        }
        ps.popPose();
        // brazos con bombillas y barra de la cesta
        int n = be.asientos();
        for (int i = 0; i < n; i++) {
            double[] e = be.brazo(i, be.getLevel().getGameTime() + (double) pt);
            double ex = cx + e[0], ey = e[1], ez = cz + e[2];
            FeriaPiezas.barra(ps, vc, "metal_blanco", cx, hub + 0.2, cz, ex, ey, ez, 0.32F, 0.32F, luz);
            FeriaPiezas.barra(ps, vc, "color0", cx, hub + 0.45, cz, ex, ey + 0.1, ez, 0.12F, 0.12F, luz);
            FeriaPiezas.barra(ps, vc, "dorado", ex, ey, ez, ex, ey - AtraccionBlockEntity.CUELGA_CESTA + 0.9, ez, 0.14F, 0.14F, luz);
            for (int j = 1; j <= 6; j++) {
                double k = j / 7.0;
                FeriaPiezas.bombilla(ps, vc, cx + (ex - cx) * k, hub + 0.42 + (ey - hub - 0.42) * k, cz + (ez - cz) * k, 0.12F, FeriaPiezas.luz(i * 3 + j, t, be.gira));
            }
        }
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
