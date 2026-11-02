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

/**
 * Registro del cliente (bus del mod) del péndulo y la silla de inmersión: renderizadores, piezas móviles (modelos
 * sueltos de scratchpad/gen_tortura4a.py, en las mismas coordenadas que el modelo del bloque, mirando al norte) y HUD.
 * Va aparte de {@link AparatosClienteMod} para no tocarlo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PenduloInmersionClienteMod {
    private PenduloInmersionClienteMod() {}

    private static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/" + n);
    }

    public static final ResourceLocation PENDULO_ALTO = rl("pendulo_alto");
    public static final ResourceLocation PENDULO_VARA = rl("pendulo_vara");
    public static final ResourceLocation PENDULO_CUCHILLA = rl("pendulo_cuchilla");
    public static final ResourceLocation PENDULO_TRINQUETE = rl("pendulo_trinquete");
    public static final ResourceLocation PENDULO_PALANCA = rl("pendulo_palanca");
    public static final ResourceLocation PENDULO_MANIVELA = rl("pendulo_manivela");
    public static final ResourceLocation PENDULO_CORREA = rl("pendulo_correa");
    public static final ResourceLocation PENDULO_CORREA_ROTA = rl("pendulo_correa_rota");
    public static final ResourceLocation INMERSION_BRAZO = rl("inmersion_brazo");
    public static final ResourceLocation INMERSION_SILLA = rl("inmersion_silla");
    public static final ResourceLocation[] TODOS = {PENDULO_ALTO, PENDULO_VARA, PENDULO_CUCHILLA, PENDULO_TRINQUETE, PENDULO_PALANCA,
            PENDULO_MANIVELA, PENDULO_CORREA, PENDULO_CORREA_ROTA, INMERSION_BRAZO, INMERSION_SILLA};

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(AparatosRegistro.PENDULO_BE.get(), PenduloRenderer::new);
        e.registerBlockEntityRenderer(AparatosRegistro.INMERSION_BE.get(), InmersionRenderer::new);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        for (ResourceLocation r : TODOS) e.register(r);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("tortura_pendulo_inmersion", PenduloInmersionCliente::hud);
    }
}
