package com.miscelanea.client.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.CarroChivato;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Silla con ruedas + chivato atado, girada como la vagoneta sobre el raíl. */
public class CarroChivatoRenderer extends GeoEntityRenderer<CarroChivato> {
    public CarroChivatoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "carro_chivato")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6F;
    }

    @Override
    protected void applyRotations(CarroChivato e, PoseStack pose, float ageInTicks, float yaw, float partial) {
        // La vagoneta guarda su yaw "de raíl"; el modelo mira hacia -Z
        float y = net.minecraft.util.Mth.rotLerp(partial, e.yRotO, e.getYRot());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - y));
        float x = net.minecraft.util.Mth.lerp(partial, e.xRotO, e.getXRot());
        if (x != 0) pose.mulPose(Axis.XP.rotationDegrees(-x));
    }
}
