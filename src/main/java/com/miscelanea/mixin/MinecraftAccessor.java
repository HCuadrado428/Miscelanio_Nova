package com.miscelanea.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Para cambiar un momento la pantalla principal por otra textura y dibujar el mundo desde una cámara (ver CamarasCliente). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("mainRenderTarget")
    void miscelanea$setMainRenderTarget(RenderTarget t);
}
