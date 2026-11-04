package com.miscelanea.client.bromas;

import com.miscelanea.Miscelanea;
import com.miscelanea.bromas.BromasRegistro;
import com.miscelanea.tortura.aparatos.RuedaRegistro;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registro del cliente (bus del mod) de las bromas y de la rueda de Catalina: color de la poción, confeti, poción en
 * vuelo, la rueda, el cartel en la espalda, el HUD y la postura en aspa de la víctima de la rueda.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BromasClienteMod {
    private BromasClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        // por encima de AnimAparato (1310) y AnimInterroga (1312)
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "rueda"), 1316, AnimRueda::new);
    }

    @SubscribeEvent
    public static void colores(RegisterColorHandlersEvent.Item e) {
        // capa 0 del frasco de poción del juego (potion_overlay): el líquido
        e.register((st, capa) -> capa == 0 ? BromasRegistro.COLOR_CONTROLES : -1, BromasRegistro.POCION_CONTROLES.get());
    }

    @SubscribeEvent
    public static void particulas(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(BromasRegistro.CONFETI.get(), ConfetiParticula.Provider::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BromasRegistro.POCION_LANZADA.get(), ThrownItemRenderer::new);
        e.registerBlockEntityRenderer(RuedaRegistro.RUEDA_BE.get(), RuedaRenderer::new);
    }

    @SubscribeEvent
    public static void capas(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
            if (r != null) r.addLayer(new CartelLayer(r));
        }
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("rueda_catalina", RuedaCliente::hud);
    }
}
