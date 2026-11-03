package com.miscelanea.client.tortura.saw;

import com.miscelanea.tortura.saw.FaseSalida;
import com.miscelanea.tortura.saw.SalidaBlock;
import com.miscelanea.tortura.saw.SalidaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * El reloj de la puerta de salida (delante y detrás, en el dintel) y el resplandor de la bombilla enjaulada: tenue y
 * quieta parada, parpadeando como un fluorescente viejo durante la cuenta, roja y moribunda cuando se acaba el tiempo.
 * Medidas: gen_tortura_saw.py S_RELOJ_Y, S_RELOJ_Z, S_BOMBILLA (en la mitad de arriba).
 */
public class SalidaRenderer implements BlockEntityRenderer<SalidaBlockEntity> {
    private static final float RELOJ_Y = 16F + 14.3F, RELOJ_Z0 = 4.95F, RELOJ_Z1 = 11.05F;
    private static final float BX = 8F, BY = 16F + 17.75F, BZ = 8F;

    public SalidaRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(SalidaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        BlockState s = be.getBlockState();
        if (!s.hasProperty(SalidaBlock.FACING)) return;
        Direction f = s.getValue(SalidaBlock.FACING);
        FaseSalida fase = be.fase();
        long t = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        String txt;
        int color;
        if (fase == FaseSalida.ESPERA) {
            txt = SalidaBlockEntity.reloj(be.segundos() * 20);
            color = 0xFFE0A020;
        } else if (fase == FaseSalida.CERRADA) {
            txt = "0:00";
            color = (t / 10) % 2 == 0 ? 0xFFFF2020 : 0xFF501010;
        } else {
            txt = SalidaBlockEntity.reloj(be.restante());
            color = be.restante() <= 200 && (t / 5) % 2 == 0 ? 0xFFFF6040 : 0xFFFF2020;
        }
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-(f.toYRot() + 180F)));
        ps.translate(-0.5F, 0, -0.5F);
        Font font = Minecraft.getInstance().font;
        float esc = 0.0145F;
        for (int lado = 0; lado < 2; lado++) {
            ps.pushPose();
            ps.translate(8F / 16F, RELOJ_Y / 16F, (lado == 0 ? RELOJ_Z0 : RELOJ_Z1) / 16F);
            if (lado == 0) ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.scale(esc, -esc, esc);
            font.drawInBatch(txt, -font.width(txt) / 2F, -3.5F, color, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
            ps.popPose();
        }
        // la bombilla
        float k = brillo(fase, be.abierta(), t, pt, be.getBlockPos().asLong());
        if (k > 0.02F) {
            float r = 1F, g = fase == FaseSalida.CERRADA ? 0.15F : 0.85F, b = fase == FaseSalida.CERRADA ? 0.1F : 0.55F;
            VertexConsumer vc = buf.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();
            for (int capa = 0; capa < 3; capa++) {
                float rad = (1.6F + capa * 1.4F) / 16F, a = k * (0.32F - capa * 0.09F);
                halo(vc, m, BX / 16F, BY / 16F, BZ / 16F, rad, r * a, g * a, b * a, a);
            }
        }
        ps.popPose();
    }

    /** Brillo de la bombilla (0..1). */
    private static float brillo(FaseSalida fase, boolean abierta, long t, float pt, long semilla) {
        float x = t + pt;
        switch (fase) {
            case ESPERA:
                return 0.35F;
            case CERRADA:
                // casi apagada: de vez en cuando un chispazo rojo
                return Mth.sin(x * 0.07F + semilla % 13) > 0.97F ? 0.5F : 0F;
            default:
                // fluorescente viejo: tiembla y a ratos se va un momento
                float n = Mth.sin(x * 1.3F) * 0.08F + Mth.sin(x * 0.37F + 1.7F) * 0.06F;
                boolean corte = Mth.sin(x * 0.11F + semilla % 7) > 0.93F && ((long) x % 3) != 0;
                return corte ? 0.08F : Mth.clamp(0.85F + n + (abierta ? 0.1F : 0F), 0F, 1F);
        }
    }

    /** Tres planos cruzados (aditivos) alrededor de la bombilla. */
    private static void halo(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float cr, float cg, float cb, float a) {
        float[][][] planos = {
                {{x - r, y - r, z}, {x + r, y - r, z}, {x + r, y + r, z}, {x - r, y + r, z}},
                {{x, y - r, z - r}, {x, y - r, z + r}, {x, y + r, z + r}, {x, y + r, z - r}},
                {{x - r, y, z - r}, {x + r, y, z - r}, {x + r, y, z + r}, {x - r, y, z + r}}};
        for (float[][] q : planos) {
            for (int i = 0; i < 4; i++) vc.vertex(m, q[i][0], q[i][1], q[i][2]).color(cr, cg, cb, a).endVertex();
            for (int i = 3; i >= 0; i--) vc.vertex(m, q[i][0], q[i][1], q[i][2]).color(cr, cg, cb, a).endVertex();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(SalidaBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
