package com.miscelanea.mixin;

import com.miscelanea.client.armasfuego.comun.Apuntado;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apuntando con un arma se cierra el campo de visión (como con el arco tensado, pero lo que diga la config). */
@Mixin(AbstractClientPlayer.class)
public abstract class FovApuntadoMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void miscelanea$apuntado(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer p && p == Minecraft.getInstance().player) {
            float f = Apuntado.fov(p);
            if (f != 1F) cir.setReturnValue(cir.getReturnValueF() * f);
        }
    }
}
