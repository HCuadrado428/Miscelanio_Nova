package com.miscelanea.client.cocina;

import com.miscelanea.client.feria.FeriaPiezas;
import com.miscelanea.cocina.FogonBlockEntity;
import com.miscelanea.cocina.Recetas;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** La comida en la sartén: trozos de cada ingrediente que se van dorando y, al final, el plato hecho. */
public class FogonRenderer implements BlockEntityRenderer<FogonBlockEntity> {
    public FogonRenderer(BlockEntityRendererProvider.Context c) {}

    private static float azar(int i, int k) {
        int h = i * 73856093 ^ k * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    private static int mezcla(int a, int b, float k) {
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    @Override
    public void render(FogonBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        float t = be.getLevel().getGameTime() + pt;
        float k = be.cocinando() ? Math.min(1, (t - be.inicio) / FogonBlockEntity.COCCION) : 0;
        double y0 = 15.3 / 16;
        if (!be.hecho.isEmpty() || (be.cocinando() && k > 0.6F)) {
            // capa del plato
            float c = be.hecho.isEmpty() ? (k - 0.6F) / 0.4F : 1;
            int col = be.color;
            ps.pushPose();
            ps.translate(0.5, y0 + 0.01, 0.5);
            ps.scale(0.62F, 0.04F * c + 0.002F, 0.62F);
            ps.translate(-0.5, -0.5, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("trozo"), (col >> 16 & 255) / 255F, (col >> 8 & 255) / 255F, (col & 255) / 255F, luz);
            ps.popPose();
            if (be.receta.startsWith("paella") || "gazpacho".equals(be.receta))
                for (int i = 0; i < 14; i++) {
                    int cc = be.receta.startsWith("paella") ? (i % 3 == 0 ? 0xE0302A : i % 3 == 1 ? 0xF2B0A0 : 0x5AA048) : 0xF4F0E0;
                    trozo(ps, vc, 0.5 + (azar(i, 1) - 0.5) * 0.5, y0 + 0.035, 0.5 + (azar(i, 2) - 0.5) * 0.5, 0.05F, cc, azar(i, 3) * 90, luz);
                }
            if ("churros".equals(be.receta) || "chocolate".equals(be.receta))
                for (int i = 0; i < 5; i++) {
                    ps.pushPose();
                    ps.translate(0.5, y0 + 0.05, 0.3 + i * 0.1);
                    ps.mulPose(Axis.YP.rotationDegrees(-10 + i * 6));
                    ps.scale(0.45F, 0.04F, 0.04F);
                    ps.translate(-0.5, -0.5, -0.5);
                    FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("trozo"), 0.85F, 0.58F, 0.3F, luz);
                    ps.popPose();
                }
            return;
        }
        // ingredientes troceados
        int n = 0;
        for (var st : be.dentro) {
            int col = mezcla(Recetas.color(st.getItem()), 0xB8782C, k * 0.6F);
            for (int i = 0; i < 6; i++, n++) {
                float salto = be.cocinando() ? Math.max(0, Mth.sin(t * 0.5F + n * 1.7F)) * 0.02F : 0;
                trozo(ps, vc, 0.5 + (azar(n, 1) - 0.5) * 0.5, y0 + 0.02 + salto, 0.5 + (azar(n, 2) - 0.5) * 0.5, 0.06F, col, azar(n, 3) * 90 + (be.cocinando() ? t * 3 : 0), luz);
            }
        }
    }

    private void trozo(PoseStack ps, VertexConsumer vc, double x, double y, double z, float tam, int col, float giro, int luz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.scale(tam, tam * 0.6F, tam);
        ps.translate(-0.5, -0.5, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("trozo"), (col >> 16 & 255) / 255F, (col >> 8 & 255) / 255F, (col & 255) / 255F, luz);
        ps.popPose();
    }
}
