package com.miscelanea.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Para que la cámara de vigilancia use su propia cámara y no mueva la del jugador (sonido, partículas, mira...). */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Mutable
    @Accessor("mainCamera")
    void miscelanea$setMainCamera(Camera c);
}
