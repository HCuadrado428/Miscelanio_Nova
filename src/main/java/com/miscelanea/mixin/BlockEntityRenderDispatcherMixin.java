package com.miscelanea.mixin;

import com.miscelanea.client.baile.videoclip.VideoclipCliente;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Videoclip de la gramola: los bloques con dibujo propio (cofres, carteles, máquinas...) tampoco se ven. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$videoclip(CallbackInfo ci) {
        if (VideoclipCliente.activo()) ci.cancel();
    }
}
