package com.miscelanea.client.tirolina;

import com.miscelanea.Miscelanea;
import com.miscelanea.tirolina.Cable;
import com.miscelanea.tirolina.Polea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

/** La polea: ruedas sobre el cable, alineada con su pendiente; las ruedas giran según la velocidad. */
public class PoleaRenderer extends EntityRenderer<Polea> {
    public static final ResourceLocation MODELO = new ResourceLocation(Miscelanea.MOD_ID, "block/polea");

    public PoleaRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public void render(Polea e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        Vec3 u = Cable.tangente(e.a(), e.b(), e.t()).normalize();
        if (e.velocidad() < 0) u = u.scale(-1);
        ps.pushPose();
        ps.translate(0, 0.75, 0);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(u.x, u.z)));
        ps.mulPose(Axis.XP.rotation((float) -Math.asin(Math.max(-1, Math.min(1, u.y)))));
        ps.translate(-0.5, -0.75, -0.5);
        var m = Minecraft.getInstance().getModelManager().getModel(MODELO);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, m,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(Polea e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
