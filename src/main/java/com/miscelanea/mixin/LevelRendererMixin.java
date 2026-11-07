package com.miscelanea.mixin;

import com.miscelanea.client.baile.videoclip.VideoclipCliente;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Videoclip de la gramola: mientras se baila, el mundo de verdad no se dibuja (chunks, lluvia y nieve, nubes y borde
 * del mundo); en su lugar se ve el decorado de la canción (ver VideoclipCliente). require = 0: si otro mod cambia
 * estos métodos, el decorado sale encima del mundo y ya está.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$videoclipChunks(CallbackInfo ci) {
        if (VideoclipCliente.activo()) ci.cancel();
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$videoclipLluvia(CallbackInfo ci) {
        if (VideoclipCliente.activo()) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$videoclipNubes(CallbackInfo ci) {
        if (VideoclipCliente.activo()) ci.cancel();
    }

    @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$videoclipBorde(CallbackInfo ci) {
        if (VideoclipCliente.activo()) ci.cancel();
    }
}
