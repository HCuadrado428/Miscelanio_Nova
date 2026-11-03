package com.miscelanea.mixin;

import com.miscelanea.client.tortura.efectos.MiedoEngano;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Toxina del miedo (ver MiedoEngano): la barra rápida enseña a veces otro objeto. Solo el dibujo. require = 0. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "renderSlot", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack miscelanea$hotbarMiedo(ItemStack st) {
        return MiedoEngano.verHotbar(st);
    }
}
