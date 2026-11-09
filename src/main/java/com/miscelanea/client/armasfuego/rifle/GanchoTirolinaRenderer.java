package com.miscelanea.client.armasfuego.rifle;

import com.miscelanea.armasfuego.rifle.GanchoTirolina;
import com.miscelanea.client.tirolina.PoleaRenderer;
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
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** El gancho que baja por la cuerda: la misma polea de las tirolinas de la feria, alineada con la cuerda. */
public class GanchoTirolinaRenderer extends EntityRenderer<GanchoTirolina> {
    public GanchoTirolinaRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public void render(GanchoTirolina e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        Vector3f u = new Vector3f(e.direccion());
        if (e.velocidad() < 0) u.mul(-1);
        ps.pushPose();
        ps.translate(0, 0.75, 0);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(u.x, u.z)));
        ps.mulPose(Axis.XP.rotation((float) -Math.asin(Math.max(-1, Math.min(1, u.y)))));
        ps.translate(-0.5, -0.75, -0.5);
        var m = Minecraft.getInstance().getModelManager().getModel(PoleaRenderer.MODELO);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, m,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(GanchoTirolina e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
