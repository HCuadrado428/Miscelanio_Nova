package com.miscelanea.client.camaras;

import com.miscelanea.Miscelanea;
import com.miscelanea.camaras.CamarasRegistro;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro del cliente (bus del mod) de las cámaras: el dibujado de la pantalla de las teles. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CamarasClienteMod {
    private CamarasClienteMod() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(CamarasRegistro.TELE_BE.get(), TeleRenderer::new);
    }
}
