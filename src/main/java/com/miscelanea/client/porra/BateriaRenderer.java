package com.miscelanea.client.porra;

import com.miscelanea.Miscelanea;
import com.miscelanea.porra.BateriaItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** assets/miscelanea/{geo,animations,textures}/item/armor/bateria.* — el piloto y las chispas brillan. */
public class BateriaRenderer extends GeoArmorRenderer<BateriaItem> {
    public BateriaRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "armor/bateria")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
