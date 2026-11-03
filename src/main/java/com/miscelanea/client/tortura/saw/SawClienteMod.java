package com.miscelanea.client.tortura.saw;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.saw.SawRegistro;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Registro del cliente (bus del mod) de los juegos de Jigsaw: capa de la caja de agua, reloj de la salida, tecla y HUD. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SawClienteMod {
    private SawClienteMod() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(SawRegistro.SALIDA_BE.get(), SalidaRenderer::new);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        e.register(CajaAguaLayer.MARCO);
    }

    @SubscribeEvent
    public static void teclas(RegisterKeyMappingsEvent e) {
        e.register(SawCliente.MANOS);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerBelowAll("saw_caja_agua", SawCliente::hud);
    }

    @SubscribeEvent
    public static void capas(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
            if (r != null) r.addLayer(new CajaAguaLayer<>(r));
        }
        for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES.getValues()) {
            Object r;
            try {
                r = renderer(e, t);
            } catch (ClassCastException ex) {
                continue;   // no es un renderer de entidad viva
            }
            if (r instanceof LivingEntityRenderer<?, ?> lr) anadir(lr);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object renderer(EntityRenderersEvent.AddLayers e, EntityType t) {
        return e.getRenderer(t);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void anadir(LivingEntityRenderer lr) {
        lr.addLayer(new CajaAguaLayer(lr));
    }
}
