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

    private static boolean forzar;

    /** Le baja aunque el tren vaya en marcha (para llevárselo a otra parte, p. ej. al Espera). */
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
        if (t.puedeBajar()) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("montana.miscelanea.barra"), true);
    }
}
