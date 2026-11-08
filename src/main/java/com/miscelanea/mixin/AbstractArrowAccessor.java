package com.miscelanea.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Para saber qué objeto es una flecha o un tridente lanzado (Recuperación y Tormenta). */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Invoker("getPickupItem")
    ItemStack miscelanea$recoger();
}
