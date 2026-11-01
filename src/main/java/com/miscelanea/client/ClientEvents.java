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
            com.miscelanea.client.combate.LlaveCliente.init();
            com.miscelanea.client.combate.HachaCliente.init();
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.miscelanea.registry.ModItems.CANA.get(),
                    new net.minecraft.resources.ResourceLocation("cast"), (stack, level, ent, seed) -> {
                        if (!(ent instanceof net.minecraft.world.entity.player.Player pl)) return 0F;
                        boolean principal = pl.getMainHandItem() == stack, otra = pl.getOffhandItem() == stack;
                        if (pl.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem) otra = false;
                        return (principal || otra) && pl.fishing != null ? 1F : 0F;
                    }));
            // Animaciones de combate con armas con estilo (Llave Espada...): por encima de todo
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "combate"), 1200,
                    com.miscelanea.client.combate.AnimJugador::new);
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "pipboy"), 1300,
                    com.miscelanea.client.pipboy.AnimPipBoy::new);
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "ocarina"), 1350,
                    com.miscelanea.client.ocarina.AnimOcarina::new);
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "tirolina"), 1400,
                    com.miscelanea.client.tirolina.AnimTirolina::new);
            dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "trampolin"), 1450,
                    com.miscelanea.client.trampolin.AnimTrampolin::new);
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
        public static void coloresBloque(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
            event.register((st, l, p, tinte) -> tinte == 0 && st.hasProperty(com.miscelanea.muebles.Muebles.COLOR)
                            ? com.miscelanea.muebles.Muebles.rgb(st.getValue(com.miscelanea.muebles.Muebles.COLOR)) : -1,
                    com.miscelanea.registry.ModBlocks.SILLA.get(), com.miscelanea.registry.ModBlocks.SOFA.get(), com.miscelanea.registry.ModBlocks.LAMPARA.get());
        }

        @SubscribeEvent
        public static void coloresItem(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
            event.register((st, tinte) -> {
                if (tinte != 0) return -1;
                net.minecraft.world.item.DyeColor def = st.is(com.miscelanea.registry.ModItems.LAMPARA.get()) ? net.minecraft.world.item.DyeColor.WHITE : net.minecraft.world.item.DyeColor.RED;
                var t = st.getTag();
                if (t != null && t.getCompound("BlockStateTag").contains("color"))
                    def = net.minecraft.world.item.DyeColor.byName(t.getCompound("BlockStateTag").getString("color"), def);
                return com.miscelanea.muebles.Muebles.rgb(def);
            }, com.miscelanea.registry.ModItems.SILLA.get(), com.miscelanea.registry.ModItems.SOFA.get(), com.miscelanea.registry.ModItems.LAMPARA.get());
        }

        @SubscribeEvent
        public static void modelosExtra(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
            event.register(com.miscelanea.client.tirolina.PoleaRenderer.MODELO);
            for (String n : com.miscelanea.client.imperio.ImperioRenderer.FICHAS) event.register(com.miscelanea.client.imperio.ImperioRenderer.modelo(n));
            for (String n : new String[]{"casa", "hotel", "dado"}) event.register(com.miscelanea.client.imperio.ImperioRenderer.modelo(n));
            for (String n : com.miscelanea.client.parchis.ParchisRenderer.COLOR_ID) event.register(com.miscelanea.client.parchis.ParchisRenderer.modelo("ficha_" + n));
            for (String p : com.miscelanea.client.juegos.TableroRenderer.PIEZAS) {
                event.register(com.miscelanea.client.juegos.TableroRenderer.modelo(p, true));
                event.register(com.miscelanea.client.juegos.TableroRenderer.modelo(p, false));
            }
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(com.miscelanea.registry.ModEntities.ASIENTO.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.RECREATIVA_BE.get(), com.miscelanea.client.arcade.RecreativaRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.TANQUE_BE.get(), com.miscelanea.client.tanques.TanqueRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.TABLERO_BE.get(), com.miscelanea.client.juegos.TableroRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.IMPERIO_BE.get(), com.miscelanea.client.imperio.ImperioRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.PARCHIS_BE.get(), com.miscelanea.client.parchis.ParchisRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.POSTE_TIROLINA_BE.get(), com.miscelanea.client.tirolina.PosteRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.TRAMPOLIN_BE.get(), com.miscelanea.client.trampolin.TrampolinRenderer::new);
            event.registerEntityRenderer(com.miscelanea.registry.ModEntities.POLEA.get(), com.miscelanea.client.tirolina.PoleaRenderer::new);

            event.registerEntityRenderer(ModEntities.DESATASCADOR.get(), DesatascadorRenderer::new);
            event.registerEntityRenderer(ModEntities.TINTA.get(), com.miscelanea.client.tinta.TintaProyectilRenderer::new);
            event.registerEntityRenderer(ModEntities.GANCHO.get(), com.miscelanea.client.armas.GanchoRenderer::new);
            event.registerEntityRenderer(ModEntities.COHETE.get(), com.miscelanea.client.armas.CoheteRenderer::new);
            event.registerEntityRenderer(ModEntities.LLAVE_LANZADA.get(), com.miscelanea.client.combate.LlaveLanzadaRenderer::new);
            event.registerEntityRenderer(ModEntities.HECHIZO.get(), com.miscelanea.client.combate.HechizoRenderer::new);
            event.registerEntityRenderer(ModEntities.HACHA_LANZADA.get(), com.miscelanea.client.combate.HachaLanzadaRenderer::new);
            event.registerEntityRenderer(ModEntities.BICHO.get(), com.miscelanea.client.bichos.BichoRenderer::new);
            event.registerBlockEntityRenderer(com.miscelanea.registry.ModBlocks.VITRINA_BE.get(), com.miscelanea.client.bichos.VitrinaRenderer::new);
        }

        @SubscribeEvent
        public static void capas(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(com.miscelanea.client.tinta.CalamarRender.CAPA, com.miscelanea.client.tinta.CalamarRender::crear);
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
                    renderer.addLayer(new com.miscelanea.client.tinta.DepositoLayer(renderer));
                    renderer.addLayer(new com.miscelanea.client.combate.EscudoLayer(renderer, "slim".equals(skin)));
                    renderer.addLayer(new com.miscelanea.client.pipboy.PipBoyLayer(renderer, "slim".equals(skin)));
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
            event.register(com.miscelanea.client.combate.LlaveCliente.COMANDO);
            event.register(com.miscelanea.client.combate.LlaveCliente.CAMBIAR);
            event.register(com.miscelanea.client.combate.LlaveCliente.FIJAR);
            event.register(com.miscelanea.client.combate.HachaCliente.APUNTAR);
            event.register(com.miscelanea.client.combate.HachaCliente.ESCUDO);
            event.register(com.miscelanea.client.pipboy.PipBoyCliente.ABRIR);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("forcejeo", com.miscelanea.client.porra.AgarreEventos::renderForcejeo);
            event.registerAboveAll("flashbang", FlashOverlay::render);
            event.registerAboveAll("captura", CapturaHud::render);
            event.registerBelowAll("nucleo", com.miscelanea.client.nucleo.NucleoCliente::render);
            event.registerBelowAll("medigun", com.miscelanea.client.medigun.MedigunCliente::render);
            event.registerAboveAll("tinta", com.miscelanea.client.tinta.CalamarRender::hud);
            event.registerAboveAll("armas", com.miscelanea.client.armas.ArmasCliente::hud);
            event.registerAboveAll("llave", com.miscelanea.client.combate.LlaveCliente::hud);
            event.registerAboveAll("hacha", com.miscelanea.client.combate.HachaCliente::hud);
            event.registerAboveAll("ocarina", com.miscelanea.client.ocarina.OcarinaCliente::hud);
            event.registerAboveAll("detector", com.miscelanea.client.tesoros.DetectorCliente::hud);
            event.registerAboveAll("trampolin", com.miscelanea.client.trampolin.TrampolinCliente::hud);
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
