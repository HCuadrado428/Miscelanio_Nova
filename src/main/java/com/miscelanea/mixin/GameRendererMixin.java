package com.miscelanea.mixin;

import com.miscelanea.client.movil.MovilCaptura;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Con shaders (Oculus/Iris) el mundo no está en la pantalla hasta que el pack lo compone, al final de dibujar el nivel:
 * la cámara del móvil copia la imagen justo ahí (en vez de a mitad del dibujo, donde con shaders sale negra o la del
 * fotograma anterior). Ahí mismo, con shaders, se dibuja la ecolocalización (ver EcoCliente.trasNivel).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void miscelanea$camaraConShaders(float pt, long nanos, boolean nivel, CallbackInfo ci) {
        // la ecolocalización primero: así la cámara del móvil también la saca
        com.miscelanea.client.pociones.EcoCliente.trasNivel();
        MovilCaptura.trasNivel();
    }
}
