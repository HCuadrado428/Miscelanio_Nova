package com.miscelanea.client.armasfuego.rifle;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.rifle.Rifle;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registro del cliente (bus del mod) del rifle de catalejo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RifleClienteMod {
    private RifleClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            // óxido del cobre (0, 1/3, 2/3, 1: las propiedades van de 0 a 1) y cerrojo abierto (mientras se acciona)
            ItemProperties.register(ModItems.RIFLE.get(), new ResourceLocation(Miscelanea.MOD_ID, "oxido"), (s, l, en, seed) -> Rifle.oxido(s) / 3F);
            ItemProperties.register(ModItems.RIFLE.get(), new ResourceLocation(Miscelanea.MOD_ID, "cerrojo"),
                    (s, l, en, seed) -> Rifle.cerrojo(s) > 2 ? 1F : 0F);
        });
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.BALA_RIFLE.get(), BalaRifleRenderer::new);
        e.registerEntityRenderer(ModEntities.GANCHO_TIROLINA.get(), GanchoTirolinaRenderer::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.SPYGLASS.id(), "rifle_telemetro", RifleHud::dibujar);
    }
}
