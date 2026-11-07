package com.miscelanea.mixin;

import com.miscelanea.client.TeclasExclusivas;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Teclas exclusivas (ver {@link TeclasExclusivas}): cuando una tecla del mod está en uso (por ejemplo la de recargar con
 * la ballesta en la mano), al pulsarla solo se enteran esa tecla y las de Minecraft; las de otros mods en la misma
 * tecla (recargar sombreadores, el chat de voz...) no. Al soltar se avisa a todas, como siempre.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "click", at = @At("HEAD"), cancellable = true, require = 0)
    private static void miscelanea$clicExclusivo(InputConstants.Key tecla, CallbackInfo ci) {
        List<KeyMapping> solo = TeclasExclusivas.receptores(tecla);
        if (solo == null) return;
        for (KeyMapping k : solo) {
            KeyMappingAccessor a = (KeyMappingAccessor) k;
            a.miscelanea$clics(a.miscelanea$clics() + 1);
        }
        ci.cancel();
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true, require = 0)
    private static void miscelanea$pulsarExclusivo(InputConstants.Key tecla, boolean abajo, CallbackInfo ci) {
        if (!abajo) return;
        List<KeyMapping> solo = TeclasExclusivas.receptores(tecla);
        if (solo == null) return;
        for (KeyMapping k : solo) k.setDown(true);
        ci.cancel();
    }
}
