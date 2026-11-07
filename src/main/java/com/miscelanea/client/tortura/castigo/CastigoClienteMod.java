package com.miscelanea.client.tortura.castigo;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.TorturaRegistro;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
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

/** Registro del cliente (bus del mod) de los castigos: va aparte de TorturaClienteMod para no tocarlo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CastigoClienteMod {
    private CastigoClienteMod() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        // por encima del lazo (1260) y por debajo de la ejecución de la trampa (1400)
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "castigo"), 1300, AnimCastigo::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(TorturaRegistro.JAULA.get(), JaulaRenderer::new);
        e.registerEntityRenderer(TorturaRegistro.CEPO_ASIENTO.get(), NoopRenderer::new);
        e.registerEntityRenderer(TorturaRegistro.TOMATE_LANZADO.get(), PodridoRenderer::new);
        e.registerEntityRenderer(TorturaRegistro.HUEVO_LANZADO.get(), PodridoRenderer::new);
        e.registerEntityRenderer(TorturaRegistro.BOLA_PRESO_ENT.get(), BolaRenderer::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("castigo_jaula", CastigoCliente::hud);
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        e.register(JaulaRenderer.CUERPO);
        e.register(JaulaRenderer.TECHO);
        e.register(JaulaRenderer.PUERTA);
        e.register(BolaRenderer.MODELO);
    }

    @SubscribeEvent
    public static void capas(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
            if (r != null) {
                r.addLayer(new ManchasLayer(r, "slim".equals(skin)));
                r.addLayer(new CastigoLayer<>(r));
            }
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
        lr.addLayer(new CastigoLayer(lr));
    }
}
