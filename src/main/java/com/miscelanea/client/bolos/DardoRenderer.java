package com.miscelanea.client.bolos;

import com.miscelanea.client.feria.FeriaPiezas;
import com.miscelanea.dardos.Dardo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Dardo en vuelo con la punta por delante. */
public class DardoRenderer extends EntityRenderer<Dardo> {
    public DardoRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public void render(Dardo e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - Mth.lerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.scale(0.55F, 0.55F, 0.55F);
        ps.translate(-0.5, -0.5, -0.25);
        FeriaPiezas.modelo(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), FeriaPiezas.rl("dardo"), 1, 1, 1, luz);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(Dardo e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
