package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.MochilaItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** assets/miscelanea/{geo,animations,textures}/item/armor/aspirafantasmas_mochila.* */
public class MochilaRenderer extends GeoArmorRenderer<MochilaItem> {
    public MochilaRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "armor/aspirafantasmas_mochila")));
    }
}
