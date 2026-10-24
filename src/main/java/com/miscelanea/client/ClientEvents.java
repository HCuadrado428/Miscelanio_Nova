package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.network.SoltarCapturaPacket;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModParticles;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final KeyMapping SOLTAR_CAPTURA = new KeyMapping(
            "key." + Miscelanea.MOD_ID + ".soltar_captura",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories." + Miscelanea.MOD_ID);

    /** Abre la ventana de la mochila del Aspirafantasmas. */
    public static final KeyMapping MOCHILA = new KeyMapping(
            "key." + Miscelanea.MOD_ID + ".mochila",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories." + Miscelanea.MOD_ID);

    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(PoseAspiradora::init); // crea el ArmPose a dos manos
            // La llave cambia de modelo según su símbolo
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                    com.miscelanea.registry.ModItems.LLAVE.get(),
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "simbolo"),
                    (stack, level, entity, seed) -> com.miscelanea.carcel.LlaveItem.simbolo(stack) / 10.0F));
            // Animaciones del agarre de la porra (playerAnimator)
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "agarre"), 1000,
                    com.miscelanea.client.porra.AgarreAnimacion::new);
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "estocada"), 900,
                    com.miscelanea.client.porra.EstocadaAnimacion::new);
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "aliento"), 1100,
                    com.miscelanea.client.nucleo.AlientoAnimacion::new);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.DESATASCADOR.get(), DesatascadorRenderer::new);
            event.registerEntityRenderer(ModEntities.CARRO_CHIVATO.get(), com.miscelanea.client.carcel.CarroChivatoRenderer::new);
            event.registerEntityRenderer(ModEntities.ARROJABLE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
            event.registerEntityRenderer(ModEntities.GUARDIA.get(), c -> new com.miscelanea.client.carcel.EnemigoRenderer<>(c, "guardia", 0.5F));
            event.registerEntityRenderer(ModEntities.GRITON.get(), c -> new com.miscelanea.client.carcel.EnemigoRenderer<>(c, "griton", 0.45F));
            event.registerEntityRenderer(ModEntities.JEFE.get(), c -> new com.miscelanea.client.carcel.EnemigoRenderer<>(c, "jefe", 0.8F));
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new MangueraLayer(renderer));
                    renderer.addLayer(new com.miscelanea.client.porra.CableLayer(renderer));
                    renderer.addLayer(new com.miscelanea.client.medigun.MedigunLayer(renderer));
                    renderer.addLayer(new com.miscelanea.client.medigun.UberLayer(renderer, "slim".equals(skin)));
                    renderer.addLayer(new com.miscelanea.client.nucleo.GlitchCapaLayer(renderer, "slim".equals(skin)));
                }
            }
        }

        @SubscribeEvent
        public static void registerParticles(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.ASPIRACION.get(), AireParticle.Aspiracion::new);
            event.registerSpriteSet(ModParticles.SOPLIDO.get(), AireParticle.Soplido::new);
            event.registerSpriteSet(ModParticles.ROCIO.get(), RocioParticle.Provider::new);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(SOLTAR_CAPTURA);
            event.register(MOCHILA);
            event.register(com.miscelanea.client.carcel.GafasCliente.TECLA);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("forcejeo", com.miscelanea.client.porra.AgarreEventos::renderForcejeo);
            event.registerAboveAll("flashbang", FlashOverlay::render);
            event.registerAboveAll("captura", CapturaHud::render);
            event.registerBelowAll("psicosis", com.miscelanea.client.carcel.PsicosisCliente::render);
            event.registerBelowAll("gafas", com.miscelanea.client.carcel.GafasCliente::render);
            event.registerBelowAll("taquilla", com.miscelanea.client.carcel.TaquillaCliente::render);
            event.registerBelowAll("nucleo", com.miscelanea.client.nucleo.NucleoCliente::render);
            event.registerBelowAll("medigun", com.miscelanea.client.medigun.MedigunCliente::render);
        }
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) return;
            while (SOLTAR_CAPTURA.consumeClick()) {
                ModNetwork.CHANNEL.sendToServer(new SoltarCapturaPacket());
            }
            while (MOCHILA.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null && com.miscelanea.aspira.Deposito.llevaMochila(mc.player)) {
                    mc.setScreen(new MochilaScreen());
                }
            }
        }
    }
}
