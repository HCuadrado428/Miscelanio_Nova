package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import com.miscelanea.dream.DreamRegistro;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registro del cliente (bus del mod) de lo de Dream: aparte de ClientEvents para no tocarlo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DreamClienteMod {
    private DreamClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(AnilloCliente::init); // crea el ArmPose del anillo
        // el mordisco, por encima de los grilletes y el lazo (es lo que está pasando ahora)
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "mordisco"), 1270, MordiscoAnimacion::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(DreamRegistro.CALDERO_BE.get(), CalderoRenderer::new);
    }

    @SubscribeEvent
    public static void colores(RegisterColorHandlersEvent.Item e) {
        // capa 0 del frasco: el líquido, del color de la mezcla
        e.register((st, capa) -> capa == 0 ? PotionUtils.getColor(st) : -1,
                DreamRegistro.FRASCO_MEZCLA.get(), DreamRegistro.FRASCO_MEZCLA_ARROJADIZO.get());
        // la esencia es una botella de poción normal con el líquido verde veneno
        e.register((st, capa) -> capa == 0 ? 0x3FA34A : -1, DreamRegistro.ESENCIA.get());
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerBelowAll("dream_mordisco", MordiscoCliente::hud);
        e.registerAboveAll("dream_anillo", AnilloCliente::hud);
    }
}
