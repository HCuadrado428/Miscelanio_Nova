package com.miscelanea.client.tortura;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Mobs humanoides con grilletes (zombis, esqueletos, piglins...): brazos juntos por delante. Lo llama
 * LivingEntityRendererMixin justo antes de dibujar el modelo, después de TODAS sus animaciones (los zombis ponen los
 * brazos rectos en su propio setupAnim). Los jugadores usan {@link AnimGrilletes} (playerAnimator).
 */
public final class PoseGrilletes {
    private PoseGrilletes() {}

    public static void posar(EntityModel<?> m, LivingEntity e) {
        if (e instanceof Player || !(m instanceof HumanoidModel<?> hm) || !TorturaCliente.atado(e.getId())) return;
        hm.rightArm.xRot = AnimGrilletes.RX;
        hm.rightArm.yRot = AnimGrilletes.RY;
        hm.rightArm.zRot = AnimGrilletes.RZ;
        hm.leftArm.xRot = AnimGrilletes.RX;
        hm.leftArm.yRot = -AnimGrilletes.RY;
        hm.leftArm.zRot = -AnimGrilletes.RZ;
    }
}
