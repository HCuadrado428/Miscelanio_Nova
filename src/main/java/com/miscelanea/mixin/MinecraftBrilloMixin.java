package com.miscelanea.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Percepción: agachado, se ve la silueta de lo que hay cerca (solo para quien lo lleva). */
@Mixin(Minecraft.class)
public abstract class MinecraftBrilloMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void miscelanea$percepcion(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (com.miscelanea.client.encantos.EncantosCliente.silueta(e)) cir.setReturnValue(true);
    }
}
