package com.miscelanea.client.rave;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Lo que brilla de los modelos de la rave (LED, pantallas, neones): el modelo se vuelve a dibujar con su textura de brillo
 * (lo que brilla, en su color, sobre transparente) sumada a plena luz encima del modelo normal. A diferencia de la capa de
 * brillo automática de GeckoLib, no recorta la textura del modelo: con los modelos de bloque esos trozos se quedaban en negro.
 */
final class CapaBrillo<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private final RenderType tipo;

    CapaBrillo(GeoRenderer<T> r, ResourceLocation brillo) {
        super(r);
        this.tipo = RenderType.eyes(brillo);
    }

    @Override
    public void render(PoseStack ps, T animatable, BakedGeoModel modelo, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float pt, int luz, int overlay) {
        getRenderer().reRender(modelo, ps, buf, animatable, tipo, buf.getBuffer(tipo), pt, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
    }
}
