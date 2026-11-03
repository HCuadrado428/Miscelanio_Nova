package com.miscelanea.mixin;

import com.miscelanea.client.tortura.efectos.MiedoEngano;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toxina del miedo (ver MiedoEngano): en las pantallas de inventario, tus casillas de armadura se dibujan vacías y
 * algunos objetos se ven como otra cosa un momento. Solo cambia lo que se DIBUJA (el inventario de verdad no se toca).
 * require = 0: si otra versión cambia estos métodos, simplemente no se engaña.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Redirect(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private ItemStack miscelanea$casillaMiedo(Slot slot) {
        return MiedoEngano.verSlot(slot);
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$sinTooltipArmadura(GuiGraphics g, int x, int y, CallbackInfo ci) {
        if (MiedoEngano.ocultarTooltip(((AbstractContainerScreen<?>) (Object) this).getSlotUnderMouse())) ci.cancel();
    }

    @Redirect(method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"), require = 0)
    private ItemStack miscelanea$tooltipMiedo(Slot slot) {
        return MiedoEngano.verSlot(slot);
    }
}
