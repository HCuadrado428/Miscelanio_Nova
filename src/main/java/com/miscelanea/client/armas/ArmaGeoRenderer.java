package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderizador GeckoLib genérico para armas: assets/miscelanea/{geo,animations,textures}/item/&lt;nombre&gt;.* (opcional _glowmask). */
public class ArmaGeoRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    public ArmaGeoRenderer(String nombre, boolean brilla) {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, nombre)));
        if (brilla) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
