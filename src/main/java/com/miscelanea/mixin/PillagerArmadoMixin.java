package com.miscelanea.mixin;

import com.miscelanea.armasfuego.comun.MobsArmados;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Un saqueador con arma de fuego la lleva en alto con las dos manos (la pose de la ballesta apuntando) y, mientras recarga,
 * hace el gesto de cargar la ballesta (el servidor activa «cargando ballesta», que ya viaja al cliente).
 */
@Mixin(Pillager.class)
public abstract class PillagerArmadoMixin {
    @Shadow
    protected abstract boolean isChargingCrossbow();

    @Inject(method = "getArmPose", at = @At("RETURN"), cancellable = true)
    private void miscelanea$armado(CallbackInfoReturnable<AbstractIllager.IllagerArmPose> cir) {
        Pillager p = (Pillager) (Object) this;
        if (!MobsArmados.armado(p)) return;
        cir.setReturnValue(this.isChargingCrossbow() ? AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE : AbstractIllager.IllagerArmPose.CROSSBOW_HOLD);
    }
}
