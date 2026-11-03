package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static com.miscelanea.feria.CasetaTiroBlockEntity.*;

/**
 * La caseta: la estructura (lonas, toldo festoneado, telón, cartel, estantes y pósteres) es un modelo a media escala;
 * aquí se mueve lo demás: patos y dianas, ristras de bombillas, marcador de dígitos LED y peluches de premio.
 */
public class CasetaTiroRenderer implements BlockEntityRenderer<CasetaTiroBlockEntity> {
    public static final ResourceLocation[] PELUCHE = new ResourceLocation[4];

    static {
        for (int i = 0; i < 4; i++) PELUCHE[i] = new ResourceLocation(Miscelanea.MOD_ID, "block/peluche" + i);
    }

    /** Medidas de la estructura (las mismas que en el modelo). */
    private static final double XW = 1.78, ZF = -0.42, ZB = 3.08, TECHO = 3.36;
    /** Tics que dura la celebración al acabar una partida con premio. */
    private static final int FIESTA = 80;

    public CasetaTiroRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public boolean shouldRenderOffScreen(CasetaTiroBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(CasetaTiroBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        long ahoraL = be.getLevel().getGameTime();
        float t = ahoraL + pt;
        boolean activa = be.activa(ahoraL);
        float trasFin = ahoraL - (be.inicio + DURA) + pt;
        boolean fiesta = !activa && trasFin >= 0 && trasFin < FIESTA && be.puntos >= 8;
        int modo = fiesta ? PuestosPiezas.PREMIO : activa ? PuestosPiezas.JUEGO : PuestosPiezas.REPOSO;
        int brillo = Math.max(luz & 0xFFFF, 0xB0) | (luz & 0xFFFF0000);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(giro(be.facing())));
        // estructura: modelo a media escala (1 px = 1/8 de bloque) desplazado a (0, 1, 1)
        ps.pushPose();
        ps.translate(0, 1, 1);
        PuestosPiezas.modelo(ps, vc, PuestosPiezas.CASETA, 2, brillo);
        ps.popPose();
        // bombillas: faldón del toldo, marco del cartel, mostradores y el mostrador central
        PuestosPiezas.ristra(ps, vc, -XW, TECHO - 0.03, ZF - 0.17, XW, TECHO - 0.03, ZF - 0.17, 15, 0.09F, t, modo, 0);
        double sy0 = TECHO + 0.2, sy1 = sy0 + 2.6 * 48 / 128, sz = ZF + 0.0;
        PuestosPiezas.ristra(ps, vc, 1.24, sy1 - 0.02, sz, -1.24, sy1 - 0.02, sz, 13, 0.07F, t, modo, 3);
        PuestosPiezas.ristra(ps, vc, -1.24, sy0 + 0.04, sz, 1.24, sy0 + 0.04, sz, 13, 0.07F, t, modo, 16);
        for (int s = -1; s <= 1; s += 2) {
            PuestosPiezas.ristra(ps, vc, s * 1.24, sy0 + 0.2, sz, s * 1.24, sy1 - 0.18, sz, 3, 0.07F, t, modo, s > 0 ? 0 : 29);
            PuestosPiezas.ristra(ps, vc, s * 0.62, 0.66, ZF + 0.07, s * 1.62, 0.66, ZF + 0.07, 5, 0.06F, t, modo, s > 0 ? 20 : 25);
        }
        PuestosPiezas.ristra(ps, vc, -0.42, 0.55, -0.485, 0.42, 0.55, -0.485, 5, 0.06F, t, modo, 7);
        // marcador LED del fondo: puntos, tiempo y récord
        double mz = ZB - 0.106, my = 2.9, mh = 0.2;
        int seg = activa ? (int) Math.max(0, (DURA - (ahoraL - be.inicio)) / 20) : 0;
        boolean prisa = activa && seg <= 5 && ((int) (t / 4) & 1) == 0;
        String puntos = fiesta && ((int) (t / 4) & 1) == 1 ? "   " : tres(be.puntos);
        PuestosPiezas.led(ps, vc, puntos, 0.79, my, mz, mh, 0xFF5028, true);
        PuestosPiezas.led(ps, vc, activa ? (prisa ? "  " : dos(seg)) : "--", 0.02, my, mz, mh, 0x50FF70, true);
        PuestosPiezas.led(ps, vc, tres(be.record), -0.76, my, mz, mh, 0xFFD040, true);
        // filas de blancos (los patos cabecean, las dianas se balancean)
        for (int k = 0; k < BLANCOS; k++) {
            double x = CasetaTiroBlockEntity.x(k, t);
            if (Math.abs(x) > VISIBLE) continue;
            float caida = t - be.caido[k];
            if (caida >= 8 && caida < VUELVE) continue;
            float giro = caida < 8 ? Math.min(1, caida / 5) * 85 : 0;
            // al volver a salir, suben desde abajo
            float sale = caida >= VUELVE && caida < VUELVE + 6 ? (1 - (caida - VUELVE) / 6) * 0.3F : 0;
            ps.pushPose();
            if (k < PATOS) {
                ps.translate(x, PATO_Y - sale + Mth.sin(t * 0.3F + k * 2) * 0.012, PATO_Z);
                ps.mulPose(Axis.XP.rotationDegrees(giro));
                ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.25F + k) * 4));
                ps.translate(-0.5, 0, -0.5);
                boolean oro = dorado(k, ahoraL);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl(oro ? "pato_oro" : "pato"), 1, 1, 1, oro ? 0xF000F0 : brillo);
            } else {
                ps.translate(x, DIANA_Y - sale, DIANA_Z);
                ps.mulPose(Axis.XP.rotationDegrees(giro));
                ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.08F + k) * 20));
                ps.translate(-0.5, 0, -0.5);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("diana"), 1, 1, 1, brillo);
            }
            ps.popPose();
        }
        // premios en los estantes: se mecen un poco y saltan de alegría al acabar con premio
        for (int s = -1; s <= 1; s += 2)
            for (int j = 0; j < 2; j++)
                for (int k = 0; k < 3; k++) {
                    int n = (s > 0 ? 6 : 0) + j * 3 + k;
                    float bote = fiesta ? PuestosPiezas.salto((trasFin + n * 3) % 16, 16, 0.18F) : 0;
                    float ladeo = Mth.sin(t * 0.05F + n) * 3;
                    PuestosPiezas.peluche(ps, vc, n * 7 % 4, s * 1.5, 1.66 + j * 0.75, 0.5 + k * 0.6, s * 75 + 180, 0.5F, bote, ladeo, brillo);
                }
        // peluches colgados de las esquinas del toldo
        for (int s = -1; s <= 1; s += 2)
            PuestosPiezas.pelucheColgado(ps, vc, s > 0 ? 2 : 1, s * (XW - 0.2), TECHO - 0.02, ZF + 0.12, 0.35, t, s * 1.3F, 0.42F, brillo);
        ps.popPose();
    }

    private static String tres(int n) {
        n = Mth.clamp(n, 0, 999);
        return String.format("%3d", n);
    }

    private static String dos(int n) {
        n = Mth.clamp(n, 0, 99);
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
