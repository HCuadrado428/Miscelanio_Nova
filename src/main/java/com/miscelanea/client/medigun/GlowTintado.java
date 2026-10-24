package com.miscelanea.client.medigun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.function.IntSupplier;

/** Capa emisiva (_glowmask) teñida con el color del rayo. */
public class GlowTintado<T extends GeoAnimatable> extends AutoGlowingGeoLayer<T> {
    private final IntSupplier color;

    public GlowTintado(GeoRenderer<T> renderer, IntSupplier color) {
        super(renderer);
        this.color = color;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        int c = color.getAsInt();
        RenderType emisivo = getRenderType(animatable);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, emisivo,
                bufferSource.getBuffer(emisivo), partialTick, LightTexture.FULL_SKY, OverlayTexture.NO_OVERLAY,
                ((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F, 1.0F);
    }
}
