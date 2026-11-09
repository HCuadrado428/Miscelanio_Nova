package com.miscelanea.client.armasfuego.comun;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.ArmasFuegoRegistro;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.armasfuego.oeste.CanonManoItem;
import com.miscelanea.armasfuego.oeste.DerringerItem;
import com.miscelanea.armasfuego.oeste.WinchesterItem;
import com.miscelanea.armasfuego.rifle.Pintura;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registro del cliente (bus del mod) de las armas de fuego de la segunda tanda y de lo nuevo de las tres primeras. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FuegoClienteMod {
    private FuegoClienteMod() {}

    private static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, n);
    }

    private static Level nivel(ClientLevel l, LivingEntity e) {
        return l != null ? l : e != null ? e.level() : Minecraft.getInstance().level;
    }

    private static boolean recargando(LivingEntity e, ItemStack s) {
        return e != null && e.isUsingItem() && e.getUseItem() == s && ArmaFuegoItem.modo(s) == ArmaFuegoItem.MODO_RECARGA;
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            // Winchester: la palanca abierta en la primera mitad del ciclo
            ItemProperties.register(ModItems.WINCHESTER.get(), rl("palanca"), (s, l, en, seed) -> {
                Level lv = nivel(l, en);
                float d = ArmaFuegoItem.desdeDisparo(s, lv, 0);
                int pal = en instanceof Player p ? WinchesterItem.palanca(p) : EquilibrioArmas.winchesterPalanca();
                return d >= 1 && d < pal * 0.6F ? 1F : 0F;
            });
            // Derringer: se ve en tercera persona solo justo después de disparar; abierta al recargar
            ItemProperties.register(ModItems.DERRINGER.get(), rl("visible"), (s, l, en, seed) -> DerringerItem.visible(s, nivel(l, en)) ? 1F : 0F);
            ItemProperties.register(ModItems.DERRINGER.get(), rl("abierta"), (s, l, en, seed) -> recargando(en, s) ? 1F : 0F);
            // Thompson: sin tambor durante la primera parte del cambio
            ItemProperties.register(ModItems.THOMPSON.get(), rl("sin_tambor"), (s, l, en, seed) ->
                    recargando(en, s) && en.getTicksUsingItem() < EquilibrioArmas.thompsonRecarga() * 0.6F ? 1F : 0F);
            // mosquete: la baqueta entra y sale durante la recarga (3 posiciones)
            ItemProperties.register(ModItems.MOSQUETE.get(), rl("baqueta"), (s, l, en, seed) -> {
                if (!recargando(en, s)) return 0F;
                float f = en.getTicksUsingItem() / (float) Math.max(1, EquilibrioArmas.mosqueteRecarga());
                if (f < 0.25F) return 0F;
                int k = (int) (en.getTicksUsingItem() / 4) % 4;   // arriba y abajo
                int[] fr = {1, 2, 3, 2};
                return fr[k] / 3F;
            });
            // cañón: la mecha encendida
            ItemProperties.register(ModItems.CANON_MANO.get(), rl("mecha"), (s, l, en, seed) -> CanonManoItem.mecha(s) >= 0 ? 1F : 0F);
            // revólver: el tambor abierto un momento al meter cada bala
            ItemProperties.register(ModItems.REVOLVER.get(), rl("abierto"), (s, l, en, seed) -> {
                Level lv = nivel(l, en);
                if (lv == null || !s.hasTag()) return 0F;
                long t = lv.getGameTime() - s.getTag().getLong(com.miscelanea.armasfuego.pistola.Revolver.CARGA_T);
                return t >= 0 && t < 12 ? 1F : 0F;
            });
        });
    }

    @SubscribeEvent
    public static void particulas(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ArmasFuegoRegistro.FOGONAZO.get(), FuegoParticulas.FogonazoProv::new);
        e.registerSpriteSet(ArmasFuegoRegistro.CASQUILLO.get(), FuegoParticulas.CasquilloProv::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.GATLING.get(), GatlingRenderer::new);
        e.registerEntityRenderer(ModEntities.BOLA_CANON.get(), c -> new ThrownItemRenderer<>(c, 1.2F, false));
    }

    @SubscribeEvent
    public static void modelos(ModelEvent.RegisterAdditional e) {
        for (ResourceLocation r : GatlingRenderer.TODOS) e.register(r);
    }

    @SubscribeEvent
    public static void colores(RegisterColorHandlersEvent.Item e) {
        e.register((s, capa) -> capa == 1 ? Pintura.rgb(Pintura.color(s)) : 0xFFFFFFFF, ModItems.BALA_PINTURA.get());
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "armafuego_balas", FuegoCliente::hud);
    }
}
