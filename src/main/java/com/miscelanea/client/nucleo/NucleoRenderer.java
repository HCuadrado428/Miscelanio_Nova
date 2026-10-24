package com.miscelanea.client.nucleo;

import com.miscelanea.Miscelanea;
import com.miscelanea.nucleo.NucleoItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** assets/miscelanea/{geo,animations,textures}/item/armor/nucleo.* — texturas animadas (.mcmeta) con el glitch. */
public class NucleoRenderer extends GeoArmorRenderer<NucleoItem> {
    private static final ResourceLocation BRILLO = new ResourceLocation(Miscelanea.MOD_ID, "textures/item/armor/nucleo_brillo.png");

    public NucleoRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "armor/nucleo")));
        // El error brilla en la oscuridad, y más mientras carga o dispara
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, NucleoItem animatable, BakedGeoModel model, RenderType renderType,
                               MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
                AnimatableTexture.setAndUpdate(BRILLO);
                float f = getCurrentEntity() == null ? -1 : NucleoCliente.fase(getCurrentEntity().getId(), partialTick);
                float k = f < 0 ? 0.85F : f < 1 ? 0.85F + 0.15F * f : 1.0F;
                RenderType rt = RenderType.eyes(BRILLO);
                getRenderer().reRender(model, pose, buffers, animatable, rt, buffers.getBuffer(rt), partialTick,
                        LightTexture.FULL_SKY, OverlayTexture.NO_OVERLAY, k, k, k, 1.0F);
            }
        });
    }
}
