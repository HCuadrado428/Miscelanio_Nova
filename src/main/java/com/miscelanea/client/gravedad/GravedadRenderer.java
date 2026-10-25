package com.miscelanea.client.gravedad;

import com.miscelanea.Miscelanea;
import com.miscelanea.gravedad.GravedadItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** assets/miscelanea/{geo,animations,textures}/item/pistola_gravedad.* — el núcleo naranja brilla. */
public class GravedadRenderer extends GeoItemRenderer<GravedadItem> {
    public GravedadRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "pistola_gravedad")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
