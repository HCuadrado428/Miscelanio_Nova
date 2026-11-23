package com.miscelanea.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Para dibujar el mundo desde una cámara de vigilancia (ver CamarasCliente): cambiar un momento la pantalla principal por otra
 * textura y la entidad de cámara SIN pasar por setCameraEntity (que reinicia los efectos de pantalla).
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("mainRenderTarget")
    void miscelanea$setMainRenderTarget(RenderTarget t);

    @Accessor("cameraEntity")
    void miscelanea$setCameraEntity(Entity e);
}
