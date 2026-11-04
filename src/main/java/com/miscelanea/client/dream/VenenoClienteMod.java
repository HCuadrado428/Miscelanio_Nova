package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Overlays del veneno de Dream (bus del mod): la pantalla del desangrado y el indicador «sin veneno». */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VenenoClienteMod {
    private VenenoClienteMod() {}

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerBelowAll("dream_desangrado", VenenoCliente::hudDesangrado);
        e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "dream_veneno", VenenoCliente::hudVeneno);
    }
}
