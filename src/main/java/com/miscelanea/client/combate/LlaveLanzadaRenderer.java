package com.miscelanea.client.combate;

import com.miscelanea.Miscelanea;
import com.miscelanea.llave.LlaveLanzada;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Llave lanzada: tumbada y girando rápido sobre su centro, orientada según su vuelo. */
public class LlaveLanzadaRenderer extends GeoEntityRenderer<LlaveLanzada> {
    public LlaveLanzadaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "llave_lanzada")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(LlaveLanzada e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        var v = e.getDeltaMovement();
        float rumbo = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
        ps.pushPose();
        ps.translate(0, 0.25, 0);
        ps.mulPose(Axis.YP.rotationDegrees(rumbo));
        ps.mulPose(Axis.XP.rotationDegrees(90));                         // tumbada (plano horizontal)
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partial) * 50)); // giro de bumerán
        ps.translate(0, -0.45, 0);                                       // el centro de la llave sobre la entidad
        ps.scale(0.9F, 0.9F, 0.9F);
        super.render(e, 0, partial, ps, buf, luz);
        ps.popPose();
    }
}
