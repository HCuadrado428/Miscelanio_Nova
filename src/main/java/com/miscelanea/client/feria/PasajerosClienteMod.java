package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registro (bus del mod) de la capa de playerAnimator de los pasajeros de la feria. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PasajerosClienteMod {
    private PasajerosClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        // por encima del agarre de la porra (1000) y el aliento (1100), por debajo del combate (1200) y el baile (1250)
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "pasajero_feria"), 1150, AnimPasajero::new);
    }
}
