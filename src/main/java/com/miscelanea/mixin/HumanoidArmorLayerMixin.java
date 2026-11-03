package com.miscelanea.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ejecución de la trampa de oso: sin cabeza, sin casco (ver EjecucionCliente). require = 0: si cambia, solo se ve el casco. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$sinCasco(PoseStack ps, MultiBufferSource buf, LivingEntity e, EquipmentSlot slot, int luz, HumanoidModel<?> modelo,
                                    CallbackInfo ci) {
        if (slot == EquipmentSlot.HEAD && com.miscelanea.client.tortura.EjecucionCliente.ocultarCabeza(e)) ci.cancel();
        // toxina del miedo: tu armadura "no está" (muñeco del inventario y tercera persona)
        else if (com.miscelanea.client.tortura.efectos.MiedoEngano.ocultarArmadura(e)) ci.cancel();
    }
}
