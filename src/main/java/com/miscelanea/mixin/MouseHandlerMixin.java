package com.miscelanea.mixin;

import com.miscelanea.client.combate.CamaraCombate;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Con la cámara de combate libre, el ratón gira la cámara en vez del jugador. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void miscelanea$girar(LocalPlayer jugador, double dy, double dx) {
        if (!CamaraCombate.girar(dy, dx)) jugador.turn(dy, dx);
    }
}
