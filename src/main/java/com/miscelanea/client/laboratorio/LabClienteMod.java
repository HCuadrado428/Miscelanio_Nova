package com.miscelanea.client.laboratorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.laboratorio.LabRegistro;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro del cliente (bus del mod) del laboratorio: renderizadores de las estaciones y sus piezas móviles. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LabClienteMod {
    private LabClienteMod() {}

    private static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/laboratorio/" + n);
    }

    public static final ResourceLocation CENTRIFUGA_BRAZO = rl("centrifuga_brazo");
    public static final ResourceLocation[] TODOS = {CENTRIFUGA_BRAZO};

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(LabRegistro.CENTRIFUGA_BE.get(), CentrifugaRenderer::new);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        for (ResourceLocation r : TODOS) e.register(r);
    }
}
