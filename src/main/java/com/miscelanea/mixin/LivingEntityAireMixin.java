package com.miscelanea.mixin;

import com.miscelanea.tortura.saw.CajaAgua;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Común (cliente y servidor). Con la caja de agua puesta el aire lo lleva {@link CajaAgua} entero: vanilla no lo
 * recarga (+4 por tick fuera del agua), porque si no el valor que se sincroniza y las burbujas del HUD no cuadraban
 * con el aire de verdad de la caja. En el cliente se mira el estado de la caja que manda el servidor.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAireMixin {
    @Inject(method = "increaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void miscelanea$aireCaja(int aire, CallbackInfoReturnable<Integer> cir) {
        LivingEntity v = (LivingEntity) (Object) this;
        if (CajaAgua.controlaAire(v)) cir.setReturnValue(aire);
    }
}
