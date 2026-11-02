package com.miscelanea.client.feria;

import com.miscelanea.feria.BalonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Balón de baloncesto girando en el aire. */
public class BalonRenderer extends EntityRenderer<BalonEntity> {
    public BalonRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.12F;
    }

    @Override
    public void render(BalonEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.translate(0, 0.12, 0);
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.giroO, e.giro)));
        ps.scale(0.25F, 0.25F, 0.25F);
        ps.translate(-0.5, -0.5, -0.5);
        FeriaPiezas.modelo(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), FeriaPiezas.rl("balon"), 1, 1, 1, luz);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(BalonEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
