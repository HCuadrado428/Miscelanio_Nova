package com.miscelanea.armasfuego.escopeta;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModEffects;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos de la escopeta de presión que no van en el objeto. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class EscopetaEventos {
    private EscopetaEventos() {}

    /** Roomba: recoger del suelo lo que ha traído aspirando (agachado y usando la escopeta). */
    @SubscribeEvent
    public static void recoger(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent e) {
        var p = e.getEntity();
        if (p.isShiftKeyDown() && p.isUsingItem() && p.getUseItem().getItem() instanceof EscopetaPresionItem)
            com.miscelanea.logros.LogrosMiscelanea.dar(p, "escopeta_aspirar");
    }

    /** Pringado de miel: el salto se queda en nada (en el cliente del jugador también, porque el efecto se sincroniza). */
    @SubscribeEvent
    public static void salto(LivingEvent.LivingJumpEvent e) {
        var v = e.getEntity();
        if (!v.hasEffect(ModEffects.PRINGADO.get())) return;
        var m = v.getDeltaMovement();
        v.setDeltaMovement(m.x * 0.3, Math.min(m.y, 0.0), m.z * 0.3);
    }
}
