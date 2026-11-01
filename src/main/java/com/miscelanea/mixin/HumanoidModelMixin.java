package com.miscelanea.mixin;

import com.miscelanea.client.baile.BailePoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pista de baile: después de la animación normal, se pone la pose de baile del jugador (si está bailando). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void miscelanea$baile(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof net.minecraft.world.entity.player.Player) BailePoses.posar((HumanoidModel<?>) (Object) this, entity);
    }
}
