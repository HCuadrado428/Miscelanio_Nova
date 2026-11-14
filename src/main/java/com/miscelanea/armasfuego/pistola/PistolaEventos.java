package com.miscelanea.armasfuego.pistola;

import com.miscelanea.Miscelanea;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lo que el revólver necesita en cada tick del servidor (luces de bengala, balas imán, duelos) y al salir. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PistolaEventos {
    private PistolaEventos() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Revolver.tickTirones(e.getServer());
        LucesTemporales.tick(e.getServer());
        Duelo.tick(e.getServer());
    }

    @SubscribeEvent
    public static void sale(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Duelo.salir(p);
            Revolver.olvidar(p.getUUID());
        }
    }

    /** Al cerrar el mundo (un jugador: se puede abrir otro sin reiniciar el juego) no se queda nada colgando. */
    @SubscribeEvent
    public static void para(net.minecraftforge.event.server.ServerStoppedEvent e) {
        Duelo.limpiar();
        Revolver.limpiar();
    }

    @SubscribeEvent
    public static void comandos(RegisterCommandsEvent e) {
        Duelo.comando(e.getDispatcher());
    }
}
