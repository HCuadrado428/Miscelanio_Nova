package com.miscelanea.cocina;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Gazpacho fresquito: apaga el fuego que lleves encima y protege del calor un rato. */
public class GazpachoItem extends BowlFoodItem {
    public GazpachoItem(Properties p) {
        super(p);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level l, LivingEntity e) {
        e.clearFire();
        return super.finishUsingItem(st, l, e);
    }
}
