package com.miscelanea.client.voz;

import com.miscelanea.Miscelanea;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro del cliente (bus del mod) de las voces: la capa de respaldo de la borrachera. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VozClienteMod {
    private VozClienteMod() {}

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerBelowAll("voz_borrachera", BorracheraCliente::hud);
    }
}
