package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.CoheteEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Cohete orientado según su rumbo (el modelo apunta a -z; GeckoLib lo gira 180º). La llama brilla. */
public class CoheteRenderer extends GeoEntityRenderer<CoheteEntity> {
    public CoheteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "cohete")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(CoheteEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.translate(0, 0.125, 0);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial, e.rumboYO, e.rumboY)));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.rumboXO, e.rumboX)));
        ps.translate(0, -0.125, 0);
        super.render(e, 0, partial, ps, buf, luz);
        ps.popPose();
    }
}
