package com.miscelanea.client.feria;

import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.miscelanea.feria.CapsulaGachaItem;
import com.miscelanea.feria.MaquinaAltaBlock;
import com.miscelanea.feria.MaquinaAltaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.client.resources.language.I18n;

import java.util.Map;
import java.util.WeakHashMap;

import static com.miscelanea.feria.MaquinaAltaBlockEntity.*;

/** Lo que se mueve en las máquinas altas: garra y peluches, rodillos y palanca, cápsulas y manivela; y las luces. */
public class MaquinaAltaRenderer implements BlockEntityRenderer<MaquinaAltaBlockEntity> {
    private final Map<MaquinaAltaBlockEntity, float[]> suave = new WeakHashMap<>();

    private final net.minecraft.client.gui.Font font;

    public MaquinaAltaRenderer(BlockEntityRendererProvider.Context c) {
        font = c.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(MaquinaAltaBlockEntity be) {
        return true;
    }

    private static float azar(int i, int k) {
        int h = i * 73856093 ^ k * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    @Override
    public void render(MaquinaAltaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        long ahora = be.getLevel().getGameTime();
        float t = ahora + pt;
        int brillo = Math.max(luz & 0xFFFF, 0xA0) | (luz & 0xFFFF0000);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(CasetaTiroBlockEntity.giro(be.facing())));
        switch (be.tipo()) {
            case MaquinaAltaBlock.GANCHO -> gancho(be, ps, vc, t, brillo);
            case MaquinaAltaBlock.TRAGAPERRAS -> tragaperras(be, ps, vc, t, brillo);
            case MaquinaAltaBlock.CANASTA -> canasta(be, ps, vc, buf, t, brillo);
            case MaquinaAltaBlock.BOXEO -> boxeo(be, ps, vc, buf, t, brillo);
            case MaquinaAltaBlock.MARTILLO -> martillo(be, ps, vc, buf, t, brillo);
            default -> gachapon(be, ps, vc, t, brillo);
        }
        ps.popPose();
    }

    private void peluche(PoseStack ps, VertexConsumer vc, int color, double x, double y, double z, float giro, float esc, int luz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.scale(esc, esc, esc);
        ps.translate(-0.5, 0, -0.5);
        FeriaPiezas.modelo(ps, vc, CasetaTiroRenderer.PELUCHE[Math.floorMod(color, 4)], 1, 1, 1, luz);
        ps.popPose();
    }

    private void gancho(MaquinaAltaBlockEntity be, PoseStack ps, VertexConsumer vc, float t, int luz) {
        float[] g;
        float[] loc = GanchoCliente.local(be.getBlockPos());
        if (be.fase == 2) g = be.garra(t - be.inicioBajada);
        else {
            float[] s = suave.computeIfAbsent(be, k -> new float[]{be.gx, be.gz});
            if (loc != null) {
                s[0] = loc[0];
                s[1] = loc[1];
            } else {
                s[0] += (be.gx - s[0]) * 0.3F;
                s[1] += (be.gz - s[1]) * 0.3F;
            }
            g = new float[]{s[0], ARRIBA, s[1], 1};
        }
        if (be.fase == 2) suave.put(be, new float[]{TRAMPILLA_X, TRAMPILLA_Z});
        // premios
        for (int k = 0; k < PREMIOS; k++) {
            if (be.pc[k] < 0) continue;
            if (k == be.agarrado && be.fase == 2 && t - be.inicioBajada >= BAJA + CIERRA) {
                float tt = t - be.inicioBajada;
                if (be.caeT < 0 || tt < be.caeT)
                    peluche(ps, vc, be.pc[k], g[0], g[1] - 0.24, g[2], k * 50 + Mth.sin(tt * 0.3F) * 8, 0.3F, luz);
                continue;
            }
            peluche(ps, vc, be.pc[k], be.px[k], SUELO - 0.02, be.pz[k], k * 50 + azar(k, be.pc[k]) * 60, 0.3F, luz);
        }
        // pórtico, carro, cable y garra
        FeriaPiezas.barra(ps, vc, "metal_blanco", -0.42, ARRIBA + 0.14, g[2], 0.42, ARRIBA + 0.14, g[2], 0.04F, 0.04F, luz);
        FeriaPiezas.caja(ps, vc, "metal_rojo", g[0], ARRIBA + 0.11, g[2], 0.1, 0.07, 0.1, luz);
        FeriaPiezas.barra(ps, vc, "negro", g[0], ARRIBA + 0.08, g[2], g[0], g[1] + 0.05, g[2], 0.012F, 0.012F, luz);
        FeriaPiezas.caja(ps, vc, "dorado", g[0], g[1] + 0.03, g[2], 0.07, 0.06, 0.07, luz);
        float ab = g[3];
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3 + 0.4, r0 = 0.03, r1 = 0.035 + 0.075 * ab, r2 = 0.02 + 0.05 * ab;
            double x1 = g[0] + Math.cos(a) * r1, z1 = g[2] + Math.sin(a) * r1, x2 = g[0] + Math.cos(a) * r2, z2 = g[2] + Math.sin(a) * r2;
            FeriaPiezas.barra(ps, vc, "metal_blanco", g[0] + Math.cos(a) * r0, g[1], g[2] + Math.sin(a) * r0, x1, g[1] - 0.09, z1, 0.018F, 0.018F, luz);
            FeriaPiezas.barra(ps, vc, "metal_blanco", x1, g[1] - 0.09, z1, x2, g[1] - 0.17, z2, 0.018F, 0.018F, luz);
        }
        // luces del marco
        for (int k = 0; k < 10; k++) {
            double x = -0.42 + 0.84 * k / 9;
            FeriaPiezas.bombilla(ps, vc, x, 1.94, -0.46, 0.05F, FeriaPiezas.luz(k, t, be.activo));
            FeriaPiezas.bombilla(ps, vc, x, 0.92, -0.46, 0.04F, FeriaPiezas.luz(k + 3, t, be.activo));
        }
    }

    private void tragaperras(MaquinaAltaBlockEntity be, PoseStack ps, VertexConsumer vc, float t, int luz) {
        float tt = be.activo ? t - be.inicio : 999;
        double r = 0.16, cara = 2 * r * Math.tan(Math.PI / 5);
        for (int i = 0; i < 3; i++) {
            float base = -be.rodillo[i] * 72F, rot;
            if (tt < PARA[i]) rot = base - (PARA[i] - tt) * 38F;
            else rot = base + (tt - PARA[i] < 8 ? Mth.sin((tt - PARA[i]) * 1.2F) * 10 * (1 - (tt - PARA[i]) / 8) : 0);
            double cx = 0.27 - i * 0.27;
            for (int k = 0; k < 5; k++) {
                ps.pushPose();
                ps.translate(cx, 1.3, -0.27);
                ps.mulPose(Axis.XP.rotationDegrees(rot + k * 72));
                ps.translate(0, 0, -r);
                ps.scale(0.24F, (float) cara, 0.02F);
                ps.translate(-0.5, -0.5, -0.5);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("simbolo" + k), 1, 1, 1, luz);
                ps.popPose();
            }
        }
        // palanca a la derecha del jugador (-x)
        float tira = tt < 14 ? Mth.sin(tt / 14 * Mth.PI) * 70 : 0;
        ps.pushPose();
        ps.translate(-0.47, 1.05, 0.0);
        ps.mulPose(Axis.XP.rotationDegrees(-tira));
        FeriaPiezas.barra(ps, vc, "metal_blanco", 0, 0, 0, 0, 0.42, -0.02, 0.035F, 0.035F, luz);
        FeriaPiezas.bombilla(ps, vc, 0, 0.45, -0.02, 0.09F, 0xE02828);
        ps.popPose();
        boolean gana = be.activo && tt > PARA[2] + 6 && premio(be.rodillo) > 0;
        for (int k = 0; k < 9; k++) {
            double x = -0.36 + 0.72 * k / 8;
            int c = gana ? ((int) (t / 2 + k) % 2 == 0 ? 0xFFF060 : 0xFF4040) : FeriaPiezas.luz(k, t, be.activo);
            FeriaPiezas.bombilla(ps, vc, x, 1.88, -0.43, 0.05F, c);
        }
    }

    private void gachapon(MaquinaAltaBlockEntity be, PoseStack ps, VertexConsumer vc, float t, int luz) {
        float tt = be.activo ? t - be.inicio : 999;
        // cápsulas dentro de la bola de cristal
        for (int i = 0; i < 22; i++) {
            double x = (azar(i, 1) - 0.5) * 0.52, z = (azar(i, 2) - 0.5) * 0.52;
            double y = 1.1 + azar(i, 3) * (0.42 - Math.hypot(x, z) * 0.5);
            if (tt < 26) {
                y += Math.abs(Mth.sin(tt * 0.6F + i)) * 0.03;
            }
            capsula(ps, vc, CapsulaGachaItem.COLORES[i % 6], x, y, z, azar(i, 4) * 360, luz);
        }
        // manivela
        float giro = tt < 24 ? tt / 24 * 360 : 0;
        ps.pushPose();
        ps.translate(0, 0.62, -0.45);
        ps.mulPose(Axis.ZP.rotationDegrees(giro));
        FeriaPiezas.caja(ps, vc, "metal_blanco", 0, 0, 0, 0.18, 0.18, 0.03, luz);
        FeriaPiezas.barra(ps, vc, "metal_rojo", -0.12, 0, -0.03, 0.12, 0, -0.03, 0.04F, 0.04F, luz);
        FeriaPiezas.bombilla(ps, vc, 0.12, 0, -0.07, 0.05F, 0xF0F0F0);
        ps.popPose();
        // la cápsula que sale
        if (tt >= 20 && tt < 40) {
            float k = Math.min(1, (tt - 20) / 12);
            capsula(ps, vc, CapsulaGachaItem.COLORES[be.colorCapsula % 6], 0, 0.42 - 0.12 * k, -0.3 - 0.16 * k, tt * 20, luz);
        }
    }

    // ------------------------------------------------------------------ canastas
    private void canasta(MaquinaAltaBlockEntity be, PoseStack ps, VertexConsumer vc, MultiBufferSource buf, float t, int luz) {
        float tt = be.activo ? t - be.inicio : 0;
        double[] a = MaquinaAltaBlockEntity.aro(tt);
        // tablero
        ps.pushPose();
        ps.translate(0, 2.02, 0.44);
        ps.scale(0.92F, 0.62F, 1F);
        ps.translate(-0.5, -0.5, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("tablero"), 1, 1, 1, luz);
        ps.popPose();
        FeriaPiezas.caja(ps, vc, "cromo", 0, 2.02, 0.48, 0.96, 0.66, 0.02, luz);
        // aro y red
        double r = 0.17;
        FeriaPiezas.barra(ps, vc, "metal_rojo", a[0], a[1], a[2] + r, a[0], a[1], 0.42, 0.03F, 0.03F, luz);
        for (int k = 0; k < 14; k++) {
            double a0 = k * Math.PI * 2 / 14, a1 = (k + 1) * Math.PI * 2 / 14;
            FeriaPiezas.barra(ps, vc, "metal_rojo", a[0] + Math.cos(a0) * r, a[1], a[2] + Math.sin(a0) * r, a[0] + Math.cos(a1) * r, a[1], a[2] + Math.sin(a1) * r, 0.025F, 0.025F, luz);
            {
                double vaiven = Math.sin(t * 0.3 + k) * 0.01;
                FeriaPiezas.barra(ps, vc, "lona_blanca", a[0] + Math.cos(a0) * r, a[1], a[2] + Math.sin(a0) * r,
                        a[0] + Math.cos(a0) * r * 0.55 + vaiven, a[1] - 0.2, a[2] + Math.sin(a0) * r * 0.55, 0.012F, 0.012F, luz);
            }
        }
        // marcador encima
        String txt = be.activo ? I18n.get("miscelanea.canasta.marcador", be.puntos, Math.max(0, (int) ((MaquinaAltaBlockEntity.CANASTA_DURA - tt) / 20)))
                : I18n.get("miscelanea.canasta.cartel");
        letrero(ps, buf, txt, 2.45, -0.06, be.activo ? 0xFFFF5040 : 0xFFFFD040);
        for (int k = 0; k < 9; k++) FeriaPiezas.bombilla(ps, vc, -0.4 + k * 0.1, 2.36, -0.04, 0.045F, FeriaPiezas.luz(k, t, be.activo));
    }

    private void letrero(PoseStack ps, MultiBufferSource buf, String s, double y, double z, int col) {
        ps.pushPose();
        ps.translate(0, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(0.012F, -0.012F, 0.012F);
        float esc = Math.min(1, 74F / Math.max(1, font.width(s)));
        ps.scale(esc, esc, esc);
        font.drawInBatch(s, -font.width(s) / 2F, 0, col, false, ps.last().pose(), buf, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0xC0000000, 0xF000F0);
        ps.popPose();
    }

    // ------------------------------------------------------------------ boxeo
    private void boxeo(MaquinaAltaBlockEntity be, PoseStack ps, VertexConsumer vc, MultiBufferSource buf, float t, int luz) {
        float dt = t - be.golpeT;
        boolean fuera = be.activo && be.fase >= 1;
        float ang = be.fase == 2 && dt < 40 ? (float) (Math.sin(dt * 0.5) * 70 * Math.exp(-dt * 0.08)) : 0;
        if (be.fase == 2 && dt < 4) ang = 75 * dt / 4;
        ps.pushPose();
        ps.translate(0, 1.62, fuera ? -0.36 : -0.22);
        ps.mulPose(Axis.XP.rotationDegrees(-ang));
        // pera de cuero colgada de la correa (el modelo tiene el punto de giro arriba)
        ps.translate(0, 0, -0.08);
        ps.scale(0.66F, 0.66F, 0.66F);
        ps.translate(-0.5, -1, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("saco"), 1, 1, 1, luz);
        ps.popPose();
        int mostrado = be.fase == 2 ? (int) Math.min(be.valor, be.valor * Math.max(0, dt) / 30) : 0;
        String num = String.format("%03d", mostrado);
        int col = be.fase == 2 && dt > 30 && be.valor >= 900 && ((int) t / 3) % 2 == 0 ? 0xFFFFFF60 : 0xFFFF3020;
        letrero(ps, buf, num, 1.86, -0.47, col);
        for (int k = 0; k < 7; k++) {
            boolean enc = be.fase == 2 && mostrado >= (k + 1) * 140;
            FeriaPiezas.bombilla(ps, vc, -0.36 + k * 0.12, 1.08, -0.47, 0.06F, enc ? new int[]{0x40FF40, 0x80FF40, 0xC0FF40, 0xFFE040, 0xFFA020, 0xFF6020, 0xFF2020}[k] : 0x302020);
        }
    }

    // ------------------------------------------------------------------ martillo de fuerza
    private void martillo(MaquinaAltaBlockEntity be, PoseStack ps, VertexConsumer vc, MultiBufferSource buf, float t, int luz) {
        double base = 1.0, alto = 3.3;
        FeriaPiezas.caja(ps, vc, "lona_blanca", 0, base + alto / 2 + 0.25, 0.26, 0.26, alto + 0.5, 0.1, luz);
        // escala pintada: BEBÉ, FLOJO, NORMAL, FUERTE y ¡CAMPEÓN!
        for (int k = 0; k < 5; k++) {
            ps.pushPose();
            ps.translate(0, base + 0.2 + (k + 0.5) * alto / 5, 0.2);
            ps.scale(0.22F, (float) (alto / 5 - 0.03), 1F);
            ps.translate(-0.5, -0.5, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("escala" + k), 1, 1, 1, luz);
            ps.popPose();
        }
        FeriaPiezas.barra(ps, vc, "metal_blanco", -0.13, base, 0.17, -0.13, base + alto + 0.3, 0.17, 0.025F, 0.025F, luz);
        FeriaPiezas.barra(ps, vc, "metal_blanco", 0.13, base, 0.17, 0.13, base + alto + 0.3, 0.17, 0.025F, 0.025F, luz);
        // campana
        double top = base + alto + 0.45;
        float dt = t - be.golpeT;
        double objetivo = base + 0.15 + (alto + 0.05) * be.valor / 100.0, h = base + 0.15;
        if (be.fase == 2) {
            if (dt < 12) h = base + 0.15 + (objetivo - base - 0.15) * Math.sin(dt / 12 * Math.PI / 2);
            else if (dt < 22) h = objetivo;
            else h = Math.max(base + 0.15, objetivo - (dt - 22) * (dt - 22) * 0.004);
        }
        boolean suena = be.fase == 2 && be.valor >= 97 && dt > 10 && dt < 40;
        ps.pushPose();
        ps.translate(0, top, 0.17);
        if (suena) ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(dt * 1.5) * 12));
        ps.scale(0.42F, 0.42F, 0.42F);
        ps.translate(-0.5, 0, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("campana"), 1, 1, 1, luz);
        ps.popPose();
        FeriaPiezas.caja(ps, vc, "dorado", 0, h, 0.16, 0.2, 0.1, 0.12, luz);
        // medidor en la base
        float m = be.fase == 1 && be.activo ? be.medidor(t) : be.fase == 2 ? be.valor : 0;
        double ang = Math.PI * (1 - m / 100.0);
        FeriaPiezas.caja(ps, vc, "negro", 0, 0.36, -0.49, 0.42, 0.24, 0.02, luz);
        for (int k = 0; k < 9; k++) {
            double a = Math.PI * (1 - k / 8.0);
            FeriaPiezas.bombilla(ps, vc, Math.cos(a) * -0.17, 0.27 + Math.sin(a) * 0.17, -0.51, 0.03F, k < 4 ? 0x40E040 : k < 7 ? 0xF0D040 : 0xF03020);
        }
        FeriaPiezas.barra(ps, vc, "metal_rojo", 0, 0.27, -0.515, Math.cos(ang) * -0.15, 0.27 + Math.sin(ang) * 0.15, -0.515, 0.02F, 0.02F, luz);
        // mazo
        ps.pushPose();
        ps.translate(-0.62, 0.0, -0.3);
        float golpe = be.fase == 2 && dt < 8 ? (float) Math.sin(dt / 8 * Math.PI) * 50 : 0;
        ps.mulPose(Axis.ZP.rotationDegrees(-25 + golpe));
        ps.translate(-0.5, 0, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("mazo"), 1, 1, 1, luz);
        ps.popPose();
        letrero(ps, buf, be.fase == 2 && dt > 12 ? String.valueOf(be.valor * 10) : I18n.get("miscelanea.martillo.cartel"), top + 0.45, 0.08, 0xFFFFD040);
    }

    private void capsula(PoseStack ps, VertexConsumer vc, int c, double x, double y, double z, float giro, int luz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.mulPose(Axis.XP.rotationDegrees(giro * 0.5F));
        ps.scale(0.11F, 0.11F, 0.11F);
        ps.translate(-0.5, -0.5, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("capsula"), (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, luz);
        ps.popPose();
    }
}
