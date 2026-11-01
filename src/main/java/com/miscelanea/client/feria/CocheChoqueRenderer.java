package com.miscelanea.client.feria;

import com.miscelanea.feria.CocheChoque;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Coche de choque del color que toque, con una sacudida al chocar. */
public class CocheChoqueRenderer extends EntityRenderer<CocheChoque> {
    public CocheChoqueRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.7F;
    }

    @Override
    public void render(CocheChoque e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        float s = e.sacudida, t = e.tickCount + pt;
        if (s > 0.01F) {
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 2.5F) * 6 * s));
            ps.mulPose(Axis.XP.rotationDegrees(Mth.cos(t * 2.1F) * 4 * s));
        }
        ps.translate(-0.5, 0, -0.5);
        FeriaPiezas.modelo(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), FeriaPiezas.COCHE[Math.floorMod(e.color(), 4)], 1, 1, 1, luz);
        ps.popPose();
        // chispa en la punta del mástil
        if ((e.tickCount / 2) % 7 == 0 && e.isVehicle()) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
            FeriaPiezas.bombilla(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), 0, 31 / 16.0, -2 / 16.0 - 0.0, 0.18F, 0xA0E0FF);
            ps.popPose();
        }
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(CocheChoque e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
