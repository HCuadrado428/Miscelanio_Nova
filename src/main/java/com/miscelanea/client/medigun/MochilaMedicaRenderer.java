package com.miscelanea.client.medigun;

import com.miscelanea.Miscelanea;
import com.miscelanea.medigun.Medigun;
import com.miscelanea.medigun.MedigunItem;
import com.miscelanea.medigun.MochilaMedicaItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** assets/miscelanea/{geo,animations,textures}/item/armor/mochila_medica.* — el vial sigue a la ÜberCarga. */
public class MochilaMedicaRenderer extends GeoArmorRenderer<MochilaMedicaItem> {
    public MochilaMedicaRenderer() {
        super(new DefaultedItemGeoModel<MochilaMedicaItem>(new ResourceLocation(Miscelanea.MOD_ID, "armor/mochila_medica")) {
            @Override
            public void setCustomAnimations(MochilaMedicaItem animatable, long instanceId, AnimationState<MochilaMedicaItem> state) {
                ItemStack gun = medigun(state.getData(DataTickets.ENTITY));
                float f = gun.isEmpty() ? 0.0F : Medigun.carga(gun) / Medigun.MAX;
                getBone("carga").ifPresent(b -> {
                    b.setHidden(f <= 0.01F);
                    b.setScaleY(Math.max(0.02F, f));
                });
            }
        });
        addRenderLayer(new GlowTintado<>(this, () -> {
            ItemStack gun = medigun(getCurrentEntity());
            return gun.isEmpty() ? Medigun.COLOR_DEFECTO : Medigun.color(gun);
        }));
    }

    /** La medigun que lleva en la mano (la principal primero). */
    static ItemStack medigun(Entity e) {
        if (!(e instanceof LivingEntity le)) return ItemStack.EMPTY;
        if (le.getMainHandItem().getItem() instanceof MedigunItem) return le.getMainHandItem();
        if (le.getOffhandItem().getItem() instanceof MedigunItem) return le.getOffhandItem();
        return ItemStack.EMPTY;
    }
}
