package com.miscelanea.client.bolos;

import com.miscelanea.bolos.BolaBolos;
import com.miscelanea.client.feria.FeriaPiezas;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** La bola rodando en la dirección en que va. */
public class BolaBolosRenderer extends EntityRenderer<BolaBolos> {
    public BolaBolosRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.2F;
    }

    @Override
    public void render(BolaBolos e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        var v = e.getDeltaMovement();
        float dir = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        ps.translate(0, 0.17, 0);
        ps.mulPose(Axis.YP.rotationDegrees(dir));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.giroO, e.giro)));
        ps.scale(0.36F, 0.36F, 0.36F);
        ps.translate(-0.5, -0.5, -0.5);
        FeriaPiezas.modelo(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), FeriaPiezas.rl("bola"), 1, 1, 1, luz);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(BolaBolos e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
