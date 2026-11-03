package com.miscelanea.mixin;

import com.miscelanea.client.combate.CamaraCombate;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Con la cámara de combate libre, el ratón gira la cámara en vez del jugador (y colgado del gancho va invertido). */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void miscelanea$girar(LocalPlayer jugador, double dy, double dx) {
        // en la ejecución de la trampa de oso el ratón no hace nada
        if (com.miscelanea.client.tortura.EjecucionCliente.raton()) return;
        // colgado boca abajo del gancho (cámara girada 180º): el ratón al revés en los dos ejes
        if (com.miscelanea.client.tortura.aparatos.InterrogaCliente.ratonInvertido()) {
            dy = -dy;
            dx = -dx;
        }
        if (!CamaraCombate.girar(dy, dx) && !com.miscelanea.client.tortura.qte.QteCliente.raton(dy, dx)) jugador.turn(dy, dx);
    }
}
