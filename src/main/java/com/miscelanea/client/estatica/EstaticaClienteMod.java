package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.estatica.EstaticaRegistro;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Registro del cliente (bus del mod) de la estática: tecla de la rueda, partículas, HUD, capa del cuerpo y animación. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EstaticaClienteMod {
    private EstaticaClienteMod() {}

    /** Mantener para abrir la rueda de poderes (Y: libre en vanilla y en el resto del mod). */
    public static final KeyMapping RUEDA = new KeyMapping("key.miscelanea.rueda_estatica", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y, com.miscelanea.client.combate.LlaveCliente.CATEGORIA);
    public static final ResourceLocation HUD = new ResourceLocation(Miscelanea.MOD_ID, "estatica_pantalla");

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "estatica"), 1405, AnimEstatica::new);
    }

    @SubscribeEvent
    public static void teclas(RegisterKeyMappingsEvent e) {
        e.register(RUEDA);
    }

    @SubscribeEvent
    public static void particulas(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(EstaticaRegistro.GOTA.get(), EstaticaParticula.Gota::new);
        e.registerSpriteSet(EstaticaRegistro.CHISPA.get(), EstaticaParticula.Chispa::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll(HUD.getPath(), EstaticaHud::hud);
    }

    @SubscribeEvent
    public static void capas(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
            if (r != null) r.addLayer(new EstaticaLayer(r));
        }
    }
}
