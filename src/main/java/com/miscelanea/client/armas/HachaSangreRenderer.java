package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.sangre.HachaSangreItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** El Hacha de sangre cambia de textura (y de brillo) cuando llega a la fase 4, la insaciable. */
public class HachaSangreRenderer extends ArmaGeoRenderer<HachaSangreItem> {
    private static final ResourceLocation INSACIABLE = new ResourceLocation(Miscelanea.MOD_ID, "textures/item/hacha_sangre_insaciable.png");

    public HachaSangreRenderer() {
        super("hacha_sangre", true);
    }

    @Override
    public ResourceLocation getTextureLocation(HachaSangreItem animatable) {
        ItemStack s = getCurrentItemStack();
        return s != null && HachaSangreItem.fase(s) >= 4 ? INSACIABLE : super.getTextureLocation(animatable);
    }
}
