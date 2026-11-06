package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilItem;
import com.miscelanea.movil.MovilRegistro;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registros de cliente del móvil (bus del mod): tecla, color de la carcasa, capa de la mano y la pose de playerAnimator. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MovilClienteMod {
    private MovilClienteMod() {}

    @SubscribeEvent
    public static void teclas(RegisterKeyMappingsEvent e) {
        e.register(MovilCliente.ABRIR);
    }

    @SubscribeEvent
    public static void colores(RegisterColorHandlersEvent.Item e) {
        e.register((s, tinte) -> tinte == 0 ? 0xFF000000 | MovilItem.color(s) : -1, MovilRegistro.MOVIL.get());
    }

    @SubscribeEvent
    public static void capas(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
            if (r != null) r.addLayer(new MovilManoLayer(r));
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        // por encima de las de combate (1200) y por debajo de la ocarina (1350): el hueco que dejó el Pip-Boy
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "movil"), 1300, AnimMovil::new);
    }
}
