package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.aparatos.AparatosRegistro;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registro del cliente (bus del mod) de la silla de interrogatorio y del gancho: renderizadores, piezas móviles del torno
 * (modelos sueltos de scratchpad/gen_tortura5.py), HUD y la capa de animación. Va aparte de {@link AparatosClienteMod}
 * para no tocarlo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InterrogaClienteMod {
    private InterrogaClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        // justo por encima de la de los aparatos (1310), que para estos tipos no hace nada
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "interrogatorio"), 1312, AnimInterroga::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(AparatosRegistro.INTERROGATORIO_BE.get(), SillaInterrogatorioRenderer::new);
        e.registerBlockEntityRenderer(AparatosRegistro.GANCHO_BE.get(), GanchoRenderer::new);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        e.register(GanchoRenderer.TAMBOR);
        e.register(GanchoRenderer.MANIVELA);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("tortura_interrogatorio", InterrogaCliente::hud);
    }
}
