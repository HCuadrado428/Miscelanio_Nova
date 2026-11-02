package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.MazoLanzado;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** El mazo lanzado: da vueltas de campana hacia donde va (y al revés cuando vuelve). */
public class MazoLanzadoRenderer extends GeoEntityRenderer<MazoLanzado> {
    public MazoLanzadoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "mazo_lanzado")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(MazoLanzado e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.translate(0, 0.45, 0);
        ps.mulPose(Axis.YP.rotationDegrees(e.rumbo()));
        ps.mulPose(Axis.XP.rotationDegrees((e.tickCount + partial) * (e.vuelve() ? -40F : 40F)));
        ps.translate(0, -0.55, 0);
        ps.scale(0.9F, 0.9F, 0.9F);
        super.render(e, 0, partial, ps, buf, luz);
        ps.popPose();
    }
}
