package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.QteCliente;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Registro del cliente (bus del mod) de la sala de juegos: va aparte de ClientEvents para no tocarlo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TorturaClienteMod {
    private TorturaClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "grilletes"), 1250, AnimGrilletes::new);
        // el lazo, por encima de los grilletes (tumbado manda sobre los brazos atados)
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "lazo"), 1260, AnimLazo::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(com.miscelanea.tortura.TorturaRegistro.LAZO_LANZADO.get(), LazoRenderer::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("tortura_trampa", TrampaHud::render);
        e.registerAboveAll("tortura_lazo", LazoCliente::hud);
        e.registerAboveAll("tortura_qte", QteCliente::hud);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        e.register(TorturaLayer.TRAMPA);
        e.register(TorturaLayer.ENGRANAJE);
        e.register(TorturaLayer.GRILLETE);
    }

    @SubscribeEvent
    public static void capas(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
            if (r != null) r.addLayer(new TorturaLayer<>(r));
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
        lr.addLayer(new TorturaLayer(lr));
    }
}
