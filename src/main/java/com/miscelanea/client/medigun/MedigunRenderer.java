package com.miscelanea.client.medigun;

import com.miscelanea.Miscelanea;
import com.miscelanea.medigun.Medigun;
import com.miscelanea.medigun.MedigunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** assets/miscelanea/{geo,animations,textures}/item/medigun.* — el medidor de carga crece con la ÜberCarga. */
public class MedigunRenderer extends GeoItemRenderer<MedigunItem> {
    public MedigunRenderer() {
        super(new DefaultedItemGeoModel<MedigunItem>(new ResourceLocation(Miscelanea.MOD_ID, "medigun")) {
            @Override
            public void setCustomAnimations(MedigunItem animatable, long instanceId, AnimationState<MedigunItem> state) {
                ItemStack s = state.getData(DataTickets.ITEMSTACK);
                float f = s == null ? 0.0F : Medigun.carga(s) / Medigun.MAX;
                getBone("carga").ifPresent(b -> {
                    b.setHidden(f <= 0.01F);
                    b.setScaleZ(Math.max(0.02F, f));
                });
            }
        });
        addRenderLayer(new GlowTintado<>(this, () -> {
            ItemStack s = getCurrentItemStack();
            return s == null ? Medigun.COLOR_DEFECTO : Medigun.color(s);
        }));
    }

    @Override
    public void preRender(PoseStack poseStack, MedigunItem animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // La manguera fija solo en primera persona; en tercera la dibuja MedigunLayer hasta la mochila
        boolean primeraPersona = this.renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || this.renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        model.getBone("manguera_fp").ifPresent(b -> b.setHidden(!primeraPersona));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}
