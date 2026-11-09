package com.miscelanea.client.armasfuego.rifle;

import com.miscelanea.armasfuego.rifle.Rifle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * El rifle en la mano: en tercera persona se encara con las dos manos (como la ballesta cargada); en primera persona, el arma se ladea al accionar el cerrojo y da un culatazo al disparar.
 */
public class RifleManoCliente implements IClientItemExtensions {
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand mano, ItemStack s) {
        // en la mano buena se lleva encarado con las dos manos (como la ballesta cargada), apunte o no
        if (mano == InteractionHand.MAIN_HAND || e.isUsingItem() && e.getUseItem() == s) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        return null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer p, HumanoidArm brazo, ItemStack s, float pt, float equipar, float golpe) {
        int lado = brazo == HumanoidArm.RIGHT ? 1 : -1;
        // cerrojo: el arma se ladea y baja un poco mientras la mano tira de la palanca
        int c = Rifle.cerrojo(s);
        if (c > 0) {
            float t = 1F - Math.max(0, c - pt) / Rifle.CERROJO_TICKS;
            float k = Mth.sin(t * Mth.PI);
            ps.translate(lado * -0.06F * k, -0.1F * k, 0.04F * k);
            ps.mulPose(Axis.ZP.rotationDegrees(lado * 14F * k));
            // tirón atrás de la palanca en mitad del ciclo
            float tiron = Mth.sin(Mth.clamp((t - 0.25F) / 0.5F, 0, 1) * Mth.PI);
            ps.translate(0, 0, 0.05F * tiron);
        }
        // culatazo al disparar
        float r = RifleCliente.retroceso(pt);
        if (r > 0) {
            ps.translate(0, 0.02F * r, 0.16F * r);
            ps.mulPose(Axis.XP.rotationDegrees(8F * r));
        }
        return false;
    }
}
