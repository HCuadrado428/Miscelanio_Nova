package com.miscelanea.client.laser;

import com.miscelanea.Miscelanea;
import com.miscelanea.laser.Laser;
import com.miscelanea.laser.LaserRegistro;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registro del cliente (bus del mod) del puntero láser. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LaserClienteMod {
    private LaserClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(LaserCliente::init);
    }

    @SubscribeEvent
    public static void colores(RegisterColorHandlersEvent.Item e) {
        // capa 1 (la lente y su brillo) del color del láser
        e.register((s, capa) -> capa == 1 ? 0xFF000000 | Laser.rgb(s) : -1, LaserRegistro.PUNTERO.get());
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("laser_destello", LaserCliente::hud);
    }
}
