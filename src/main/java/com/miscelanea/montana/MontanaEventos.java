package com.miscelanea.montana;

import com.miscelanea.Miscelanea;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** La barra de seguridad: con el tren en marcha no se puede bajar. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class MontanaEventos {
    private MontanaEventos() {}

    @SubscribeEvent
    public static void bajar(EntityMountEvent e) {
        if (!e.isDismounting() || !(e.getEntityBeingMounted() instanceof TrenMontana t) || t.isRemoved()) return;
        if (!(e.getEntityMounting() instanceof Player p) || !p.isAlive()) return;
        if (t.puedeBajar()) return;
        e.setCanceled(true);
        if (!p.level().isClientSide) p.displayClientMessage(Component.translatable("montana.miscelanea.barra"), true);
    }
}
