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
