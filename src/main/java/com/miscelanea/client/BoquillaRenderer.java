package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.AspirafantasmasItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** assets/miscelanea/{geo,animations,textures}/item/aspirafantasmas.* */
public class BoquillaRenderer extends GeoItemRenderer<AspirafantasmasItem> {
    public BoquillaRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "aspirafantasmas")));
        // La boca se pone al rojo con el calor
        addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this) {
            private final ResourceLocation tex = new ResourceLocation(Miscelanea.MOD_ID, "textures/item/aspirafantasmas_calor.png");

            @Override
            public void render(PoseStack pose, AspirafantasmasItem animatable, BakedGeoModel model,
                               net.minecraft.client.renderer.RenderType renderType, MultiBufferSource buffers,
                               VertexConsumer buffer, float partialTick, int light, int overlay) {
                net.minecraft.world.item.ItemStack s = getCurrentItemStack();
                if (s == null) return;
                float f = com.miscelanea.aspira.Calor.fraccion(s);
                float k;
                if (com.miscelanea.aspira.Calor.sobrecalentada(s)) {
                    k = 0.65F + 0.35F * net.minecraft.util.Mth.sin(System.currentTimeMillis() / 90.0F);
                } else {
                    if (f < 0.3F) return;
                    k = (f - 0.3F) / 0.7F;
                }
                float r = 1.0F, g = 0.18F + 0.4F * k, b = 0.06F + 0.14F * k * k;
                net.minecraft.client.renderer.RenderType rt = net.minecraft.client.renderer.RenderType.eyes(tex);
                getRenderer().reRender(model, pose, buffers, animatable, rt, buffers.getBuffer(rt), partialTick,
                        net.minecraft.client.renderer.LightTexture.FULL_SKY,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, r * k, g * k, b * k, 1.0F);
            }
        });
    }

    @Override
    public void preRender(PoseStack poseStack, AspirafantasmasItem animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // La manguera fija del modelo solo se ve en primera persona;
        // en tercera persona la dibuja MangueraLayer hasta la mochila.
        boolean primeraPersona = this.renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || this.renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        model.getBone("manguera_fp").ifPresent(b -> b.setHidden(!primeraPersona));

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}
