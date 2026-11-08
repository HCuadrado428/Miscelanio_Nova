package com.miscelanea.mixin;

import com.miscelanea.ConfigServidor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sin durabilidad (como en Enchancement): nada se gasta ni se rompe, ni se ve la barra. Lo que esté en la etiqueta
 * {@code miscelanea:con_durabilidad} sigue como siempre. Se apaga en la config (encantamientos.sin_durabilidad).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilidadMixin {
    @Unique
    private static final TagKey<Item> miscelanea$CON = TagKey.create(Registries.ITEM, new ResourceLocation("miscelanea", "con_durabilidad"));

    @Inject(method = "isDamageableItem", at = @At("HEAD"), cancellable = true)
    private void miscelanea$sinDurabilidad(CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigServidor.encantosActivo() || !ConfigServidor.encantosSinDurabilidad()) return;
        ItemStack self = (ItemStack) (Object) this;
        if (self.isEmpty() || !self.getItem().canBeDepleted() || self.is(miscelanea$CON)) return;
        cir.setReturnValue(false);
    }
}
