package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.muebles.Muebles;
import com.miscelanea.rave.Neon;
import com.miscelanea.rave.RaveRegistro;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro del cliente de la rave: colores del neón (bloques y objetos) y la barra luminosa lanzada. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RaveClienteMod {
    private RaveClienteMod() {}

    @SubscribeEvent
    public static void coloresBloque(RegisterColorHandlersEvent.Block e) {
        e.register((st, l, p, tinte) -> tinte == 0 && st.hasProperty(Muebles.COLOR) ? Neon.rgb(st.getValue(Muebles.COLOR)) : -1,
                RaveRegistro.TUBO.get(), RaveRegistro.PANEL.get(), RaveRegistro.BARRA_SUELO.get());
    }

    @SubscribeEvent
    public static void coloresItem(RegisterColorHandlersEvent.Item e) {
        e.register((st, tinte) -> tinte == 0 ? Neon.rgb(Neon.de(st)) : -1, RaveRegistro.TUBO_ITEM.get(), RaveRegistro.PANEL_ITEM.get(), RaveRegistro.BARRA.get());
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(RaveRegistro.BARRA_LANZADA.get(), ThrownItemRenderer::new);
    }
}
