package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.miscelanea.feria.AsientoFeria;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Al dibujar a alguien montado en la noria, el pulpo o un caballito, su cuerpo mira siempre hacia donde mira su asiento.
 * Sin esto, como la cabina se mueve, el juego giraba el cuerpo hacia donde iba el movimiento y el jugador acababa
 * sentado de lado en el banco.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class PasajeroCliente {
    private PasajeroCliente() {}

    @SubscribeEvent
    public static void antes(RenderLivingEvent.Pre<?, ?> ev) {
        LivingEntity e = ev.getEntity();
        if (!(e.getVehicle() instanceof AsientoFeria a)) return;
        float rumbo = a.rumboPasajero(e, ev.getPartialTick());
        e.yBodyRot = rumbo;
        e.yBodyRotO = rumbo;
    }
}
