package com.miscelanea.tirolina;

import com.miscelanea.Miscelanea;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** En la tirolina agacharse frena en vez de soltarte en el aire: solo te bajas al llegar al poste. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class TirolinaEventos {
    private TirolinaEventos() {}

    @SubscribeEvent
    public static void bajar(EntityMountEvent e) {
        // solo en el servidor: el cliente obedece lo que diga el servidor
        if (e.getEntityMounting().level().isClientSide) return;
        if (!e.isDismounting() || !(e.getEntityBeingMounted() instanceof Polea p) || p.dejaBajar()) return;
        // solo se impide el "agacharse para bajar" de un jugador vivo; cualquier otra causa (morir, teletransporte...) sigue igual
        if (e.getEntityMounting() instanceof Player pl && pl.isAlive() && pl.isShiftKeyDown() && !pl.isSpectator()) e.setCanceled(true);
    }
}
