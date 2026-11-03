package com.miscelanea.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ejecución de la trampa de oso: sin cabeza, nada puesto en ella (calabazas, cabezas de mob, bloques...). require = 0. */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$sinCabeza(PoseStack ps, MultiBufferSource buf, int luz, LivingEntity e, float limb, float limbAmt, float pt, float edad,
                                      float yaw, float pitch, CallbackInfo ci) {
        if (com.miscelanea.client.tortura.EjecucionCliente.ocultarCabeza(e) || com.miscelanea.client.tortura.efectos.MiedoEngano.ocultarArmadura(e)) ci.cancel();
    }
}
