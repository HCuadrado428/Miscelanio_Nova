package com.miscelanea.mixin;

import com.miscelanea.armasfuego.rifle.RifleItem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Apuntar con el rifle de catalejo cuenta como mirar por un catalejo: el juego pone solo el zoom (FOV), la mira del
 * catalejo, la sensibilidad baja del ratón y quita la mano de la vista.
 */
@Mixin(Player.class)
public abstract class RiflePlayerMixin {
    @Inject(method = "isScoping", at = @At("RETURN"), cancellable = true)
    private void miscelanea$rifleCatalejo(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        Player p = (Player) (Object) this;
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof RifleItem) cir.setReturnValue(true);
    }
}
