package com.miscelanea.client.tortura.castigo;

import com.miscelanea.tortura.castigo.CepoEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Mobs humanoides en el cepo: cabeza, brazos y piernas como en {@link AnimCastigo} (el cuerpo entero lo dobla
 * CastigoCliente antes de dibujar). Lo llama LivingEntityRendererMixin justo antes de dibujar el modelo.
 */
public final class PoseCastigo {
    private PoseCastigo() {}

    public static void posar(EntityModel<?> m, LivingEntity e, float pt) {
        if (e instanceof Player || !(m instanceof HumanoidModel<?> hm) || !(e.getVehicle() instanceof CepoEntity) || !CepoEntity.humanoide(e)) return;
        float inc = CastigoCliente.inclinacion(e.getId(), pt);
        hm.head.xRot = -AnimCastigo.DOBLADO;
        hm.head.yRot = 0;
        hm.head.zRot = -inc * 0.5F;
        hm.hat.copyFrom(hm.head);
        hm.rightArm.xRot = hm.leftArm.xRot = -2.79F;
        hm.rightArm.yRot = hm.leftArm.yRot = 0;
        hm.rightArm.zRot = -0.75F;
        hm.leftArm.zRot = 0.75F;
        hm.rightLeg.xRot = hm.leftLeg.xRot = -AnimCastigo.DOBLADO;
        hm.rightLeg.yRot = hm.leftLeg.yRot = 0;
        hm.rightLeg.zRot = 0.04F;
        hm.leftLeg.zRot = -0.04F;
    }
}
