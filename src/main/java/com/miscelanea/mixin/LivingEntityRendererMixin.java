package com.miscelanea.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Justo antes de dibujar el modelo (después de TODAS las animaciones, también las propias de cada mob): pose de brazos
 * atados de los mobs humanoides con grilletes y su forcejeo en la ejecución de la trampa de oso. require = 0: si otra versión cambia el método, solo se pierde la pose.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
            require = 0)
    private void miscelanea$grilletes(LivingEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz, CallbackInfo ci) {
        com.miscelanea.client.tortura.PoseGrilletes.posar(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), e);
        com.miscelanea.client.tortura.EjecucionCliente.posar(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), e, pt);
    }
}
