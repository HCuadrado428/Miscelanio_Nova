package com.miscelanea.armasfuego.comun;

import com.miscelanea.Miscelanea;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Limpieza del estado de las armas de fuego nuevas al salir un jugador o cerrar el mundo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class ArmasFuegoEventos {
    private ArmasFuegoEventos() {}

    @SubscribeEvent
    public static void sale(PlayerEvent.PlayerLoggedOutEvent e) {
        ArmaFuegoItem.olvidar(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void para(net.minecraftforge.event.server.ServerStoppedEvent e) {
        ArmaFuegoItem.limpiar();
    }
}
