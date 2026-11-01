package com.miscelanea.client.feria;

import com.miscelanea.feria.SustoBlock;
import com.miscelanea.feria.SustoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** Muñeco que salta de la caja: sale con un rebote, se menea y vuelve a meterse. En reposo solo asoman los ojos. */
public class SustoRenderer implements BlockEntityRenderer<SustoBlockEntity> {
    public SustoRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(SustoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        var vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        float t = be.getLevel().getGameTime() + pt - be.t;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-be.facing().toYRot()));
        if (t < 0 || t > SustoBlockEntity.DURA) {
            // ojos que parpadean dentro de la caja
            float g = be.getLevel().getGameTime() + pt;
            if ((int) (g / 7 + be.getBlockPos().asLong()) % 23 != 0) {
                ps.translate(-0.5, 0, -0.5);
                FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("ojos"), 1, 1, 1, 0xF000F0);
            }
            ps.popPose();
            return;
        }
        float sale;
        if (t < 6) {
            float k = t / 6;
            sale = 1 + 0.25F * Mth.sin(k * Mth.PI) - (1 - k) * (1 - k);
        } else if (t > SustoBlockEntity.DURA - 10) sale = (SustoBlockEntity.DURA - t) / 10;
        else sale = 1;
        float meneo = t < SustoBlockEntity.DURA - 10 ? Mth.sin(t * 0.9F) * 10 * Math.max(0, 1 - t / 40) : 0;
        ps.translate(0, 0.1 + 0.3 * sale, 0.15 + 0.75 * sale);
        ps.mulPose(Axis.ZP.rotationDegrees(meneo));
        ps.mulPose(Axis.XP.rotationDegrees(-12 * sale));
        float e = 0.35F + 0.65F * Math.max(0, sale);
        ps.scale(e, e, e);
        ps.translate(-0.5, 0, -0.5);
        int tipo = be.getBlockState().getValue(SustoBlock.TIPO);
        FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl(SustoBlock.NOMBRES[tipo]), 1, 1, 1, tipo == 3 ? 0xF000F0 : Math.max(luz & 0xFFFF, 0x90) | (luz & 0xFFFF0000));
        ps.popPose();
    }
}
