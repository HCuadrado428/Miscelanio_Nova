package com.miscelanea.montana;

import com.miscelanea.Miscelanea;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * La barra de seguridad: con el tren en marcha no se puede bajar uno mismo (agachándose). Todo lo demás sí le baja: morir,
 * desconectarse, un teletransporte, que se rompa el tren...
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class MontanaEventos {
    private MontanaEventos() {}

    private static boolean forzar;

    /** Le baja aunque el tren vaya en marcha (para llevárselo a otra parte, p. ej. al Purgatorio). */
    public static void bajarSiOSi(net.minecraft.world.entity.Entity e) {
        forzar = true;
        try {
            e.stopRiding();
        } finally {
            forzar = false;
        }
    }

    @SubscribeEvent
    public static void bajar(EntityMountEvent e) {
        if (forzar || !e.isDismounting() || !(e.getEntityBeingMounted() instanceof TrenMontana t) || t.isRemoved()) return;
        if (!(e.getEntityMounting() instanceof Player p) || !p.isAlive()) return;
        // solo decide el servidor: si el cliente también lo cancelara, se quedaría montado cuando el servidor ya le ha bajado
        if (p.level().isClientSide) return;
        if (p instanceof ServerPlayer sp && sp.hasDisconnected()) return;
        // solo se cancela cuando es él quien se intenta bajar (agachado); si le baja otra cosa, se baja
        if (!p.isShiftKeyDown() || t.puedeBajar()) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("montana.miscelanea.barra"), true);
    }

    /**
     * Si se desconecta en marcha, se baja junto a la vía y el tren sigue en ella (si no, Minecraft se llevaría el tren
     * guardado con el jugador y desaparecería de la vía hasta que volviera).
     */
    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity().getVehicle() instanceof TrenMontana) bajarSiOSi(e.getEntity());
    }
}
