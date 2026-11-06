package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.aparatos.AparatosRegistro;
import com.miscelanea.tortura.aparatos.HierroMarcarItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registro del cliente (bus del mod) de los aparatos: renderizadores, modelos sueltos, HUD, animación y tooltip. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AparatosClienteMod {
    private AparatosClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        // por encima del lazo y los grilletes, por debajo de la ejecución de la trampa (1300 lo usa el móvil)
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "aparato"), 1310, AnimAparato::new);
        e.enqueueWork(() -> ItemProperties.register(AparatosRegistro.HIERRO_MARCAR.get(), new ResourceLocation(Miscelanea.MOD_ID, "calor"),
                (st, l, ent, s) -> {
                    long t = l != null ? l.getGameTime() : ent != null ? ent.level().getGameTime()
                            : Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
                    return HierroMarcarItem.calor(st, t);
                }));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(AparatosRegistro.ASIENTO.get(), NoopRenderer::new);
        e.registerBlockEntityRenderer(AparatosRegistro.DONCELLA_BE.get(), DoncellaRenderer::new);
        e.registerBlockEntityRenderer(AparatosRegistro.POTRO_BE.get(), PotroRenderer::new);
        e.registerBlockEntityRenderer(AparatosRegistro.SILLA_BE.get(), SillaRenderer::new);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        for (ResourceLocation rl : ModelosAparatos.TODOS) e.register(rl);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("tortura_aparatos", AparatosCliente::hud);
    }

    @SubscribeEvent
    public static void tooltips(RegisterClientTooltipComponentFactoriesEvent e) {
        e.register(HierroMarcarItem.MarcaTooltip.class, MarcaTooltipCliente::new);
    }
}
