package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import net.minecraftforge.api.distmarker.Dist;
import com.miscelanea.pociones.PocionesRegistro;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registro del cliente (bus del mod) de las pociones del cuerpo: los HUD, el humo de la sombra y el enganche del tamaño de los demás. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PocionesClienteMod {
    private PocionesClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(Estados::iniciar);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        // por debajo del HUD: la barra y el resto se siguen viendo
        e.registerBelowAll("pociones_sangre", SangreCliente::hud);
        e.registerBelowAll("pociones_niebla", NieblaCliente::hud);
        e.registerBelowAll("pociones_eco", EcoCliente::hud);
        e.registerBelowAll("pociones_sombra", SombraCliente::hud);
    }

    @SubscribeEvent
    public static void particulas(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(PocionesRegistro.HUMO_SOMBRA.get(), SombraHumoParticula.Provider::new);
    }
}
