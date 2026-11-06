package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Registro del cliente (bus del mod) de los efectos: proyectiles, modelo del collar, capa, HUD del collar, del miedo y del fantasma. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EfectosClienteMod {
    private EfectosClienteMod() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(EfectosRegistro.GRANADA_LANZADA.get(), ThrownItemRenderer::new);
        e.registerEntityRenderer(EfectosRegistro.DARDO_LANZADO.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void particulas(net.minecraftforge.client.event.RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(EfectosRegistro.GAS.get(), GasMiedoParticula.Provider::new);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        e.register(CollarLayer.COLLAR);
        e.register(CollarLayer.LUZ);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        // el miedo por debajo de todo (la barra y el resto del HUD se siguen viendo); el collar, encima
        e.registerBelowAll("efectos_miedo", MiedoCliente::hud);
        // las manos fantasma, el fogonazo del warden y la cara del Espantapájaros tapan también el HUD
        e.registerAboveAll("efectos_miedo_encima", MiedoCliente::hudEncima);
        e.registerAboveAll("efectos_collar", CollarCliente::hud);
        // el icono del fantasma latiendo (solo para quien lo lleva)
        e.registerAboveAll("efectos_fantasma", FantasmaCliente::hud);
    }

    @SubscribeEvent
    public static void capas(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
            if (r != null) r.addLayer(new CollarLayer<>(r));
        }
        for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (t == EntityType.PLAYER) continue;
            Object r;
            try {
                r = renderer(e, t);
            } catch (ClassCastException ex) {
                continue;
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
        lr.addLayer(new CollarLayer(lr));
    }
}
