package com.miscelanea.client.feria;

import com.miscelanea.feria.MaquinaFeriaBlock;
import com.miscelanea.feria.MaquinaFeriaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** El algodón que crece girando en la cuba y las palomitas que saltan y se amontonan tras el cristal. */
public class MaquinaFeriaRenderer implements BlockEntityRenderer<MaquinaFeriaBlockEntity> {
    public MaquinaFeriaRenderer(BlockEntityRendererProvider.Context c) {}

    private static float azar(int i, int k) {
        int h = i * 73856093 ^ k * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    @Override
    public void render(MaquinaFeriaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        float t = be.getLevel().getGameTime() + pt;
        float p = be.progreso(t);
        switch (be.tipo()) {
            case MaquinaFeriaBlock.ALGODON -> {
                if (!be.activo) return;
                int c = MaquinaFeriaBlockEntity.COLORES[Math.floorMod(be.color, MaquinaFeriaBlockEntity.COLORES.length)];
                ps.pushPose();
                ps.translate(0.5, 0.62 + 0.1 * p, 0.5);
                ps.mulPose(Axis.YP.rotationDegrees(t * 25));
                float e = 0.25F + 0.7F * p;
                ps.scale(e, 0.6F + 0.4F * e, e);
                ps.translate(-0.5, -0.5, -0.5);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("nube"), (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, luz);
                ps.popPose();
                // hilos de azúcar que salen girando
                for (int k = 0; k < 6; k++) {
                    double a = t * 0.5 + k * Math.PI / 3, r = 0.2 + 0.18 * ((t * 0.07 + k * 0.3) % 1);
                    FeriaPiezas.bombilla(ps, vc, 0.5 + Math.cos(a) * r, 0.62, 0.5 + Math.sin(a) * r, 0.05F, c);
                }
            }
            case MaquinaFeriaBlock.PALOMITAS -> {
                // montón: unos pocos siempre, más cuanto más avanza la tanda
                int n = 8 + (int) (40 * p);
                for (int i = 0; i < n; i++) {
                    float x = 3.5F + azar(i, 1) * 9, z = 3.5F + azar(i, 2) * 9;
                    float alto = (1 - Math.abs(x - 8) / 6) * (1 - Math.abs(z - 8) / 6);
                    float y = 6.6F + azar(i, 3) * (0.5F + 3.5F * alto * (0.3F + 0.7F * p));
                    palomita(ps, vc, x / 16, y / 16, z / 16, azar(i, 4) * 360, luz);
                }
                if (be.activo && p > 0.12F && p < 0.97F)
                    for (int k = 0; k < 7; k++) {
                        float ciclo = 14 + k * 3, fase = ((t + k * 5.3F) % ciclo) / ciclo;
                        int id = (int) ((t + k * 5.3F) / ciclo);
                        float x = 4 + azar(id, k) * 8, z = 4 + azar(id, k + 9) * 8;
                        float y = 13 - 4 * Mth.abs(fase * 2 - 1) * 1.6F;
                        palomita(ps, vc, x / 16, Math.max(7, y + (1 - (fase * 2 - 1) * (fase * 2 - 1)) * 3) / 16, z / 16, t * 30 + k * 50, luz);
                    }
            }
            default -> {
                if (be.activo && ((int) (t / 2)) % 3 != 0) {
                    float k = 0.06F + 0.03F * Mth.sin(t * 2.3F);
                    FeriaPiezas.bombilla(ps, vc, 0.5, 13.5 / 16, 0.5, k, 0xFFC040);
                }
            }
        }
    }

    private void palomita(PoseStack ps, VertexConsumer vc, double x, double y, double z, float giro, int luz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.mulPose(Axis.XP.rotationDegrees(giro * 0.7F));
        ps.translate(-0.5, -0.5, -0.5);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("palomita"), 1, 1, 1, luz);
        ps.popPose();
    }
}
