package com.miscelanea.client.porra;

import com.miscelanea.Miscelanea;
import com.miscelanea.porra.PorraItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** assets/miscelanea/{geo,animations,textures}/item/porra.* — la punta brilla (porra_glowmask.png). */
public class PorraRenderer extends GeoItemRenderer<PorraItem> {
    public PorraRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "porra")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
