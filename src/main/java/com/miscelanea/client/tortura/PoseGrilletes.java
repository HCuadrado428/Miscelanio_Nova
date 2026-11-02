package com.miscelanea.client.tortura;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Mobs humanoides con grilletes (zombis, esqueletos, piglins...): brazos juntos por delante. Con el lazo: brazos
 * pegados al cuerpo (enlazado) o muñecas a la espalda y piernas dobladas hacia ellas (maniatado; el giro de 90º lo
 * pone LazoCliente). Lo llama LivingEntityRendererMixin justo antes de dibujar el modelo, después de TODAS sus
 * animaciones (los zombis ponen los brazos rectos en su propio setupAnim). Los jugadores usan {@link AnimGrilletes}
 * y {@link AnimLazo} (playerAnimator).
 */
public final class PoseGrilletes {
    private PoseGrilletes() {}

    public static void posar(EntityModel<?> m, LivingEntity e) {
        if (e instanceof Player || !(m instanceof HumanoidModel<?> hm)) return;
        int lazo = TorturaCliente.lazo(e.getId());
        if (lazo != 0) {
            lazo(hm, lazo, e);
            return;
        }
        if (!TorturaCliente.atado(e.getId())) return;
        hm.rightArm.xRot = AnimGrilletes.RX;
        hm.rightArm.yRot = AnimGrilletes.RY;
        hm.rightArm.zRot = AnimGrilletes.RZ;
        hm.leftArm.xRot = AnimGrilletes.RX;
        hm.leftArm.yRot = -AnimGrilletes.RY;
        hm.leftArm.zRot = -AnimGrilletes.RZ;
    }

    private static void lazo(HumanoidModel<?> hm, int estado, LivingEntity e) {
        if (estado == 2) {
            // derribado: brazos sueltos, sin atacar
            hm.rightArm.xRot = -0.2F;
            hm.leftArm.xRot = -0.25F;
            hm.rightArm.yRot = hm.leftArm.yRot = 0;
            return;
        }
        if (estado == 1) {
            float f = (float) Math.sin(e.tickCount * 0.9F) * 0.15F;
            hm.rightArm.xRot = f;
            hm.leftArm.xRot = -f;
            hm.rightArm.yRot = hm.leftArm.yRot = 0;
            hm.rightArm.zRot = 0.07F;
            hm.leftArm.zRot = -0.07F;
            return;
        }
        hm.rightArm.xRot = hm.leftArm.xRot = 0.62F;
        hm.rightArm.yRot = hm.leftArm.yRot = 0;
        hm.rightArm.zRot = -0.32F;
        hm.leftArm.zRot = 0.32F;
        hm.rightLeg.xRot = hm.leftLeg.xRot = 1.35F;
        hm.rightLeg.yRot = hm.leftLeg.yRot = 0;
        hm.rightLeg.zRot = -0.08F;
        hm.leftLeg.zRot = 0.08F;
    }
}
