package com.miscelanea.client.armasfuego.pistola;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.pistola.Tambor;
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

/** Registro del cliente (bus del mod) del revólver: texturas según el óxido, el tambor del HUD y la bengala. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PistolaClienteMod {
    private PistolaClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            PistolaCliente.init();
            ItemProperties.register(ModItems.REVOLVER.get(), new ResourceLocation(Miscelanea.MOD_ID, "oxido"),
                    (s, l, ent, seed) -> Tambor.oxido(s) / 4F);
        });
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "revolver_tambor", PistolaCliente::hud);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.REVOLVER_BENGALA.get(), BengalaRenderer::new);
    }
}
