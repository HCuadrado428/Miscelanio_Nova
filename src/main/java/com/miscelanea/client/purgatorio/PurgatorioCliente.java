package com.miscelanea.client.purgatorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.purgatorio.Purgatorio;
import com.miscelanea.purgatorio.PurgatorioGenerador;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * El Purgatorio en el cliente: sin cielo, sin nubes, sin sol ni estrellas (no se dibuja nada) y con una niebla corta y
 * oscura que se come lo de lejos. Además de dar el ambiente, ahorra: lo que tapa la niebla casi no cuesta.
 */
public final class PurgatorioCliente {
    private PurgatorioCliente() {}

    /** Hasta dónde se ve (bloques): en la feria, al aire libre, un poco más que en la sala de espera. */
    static final float NIEBLA_FERIA = 34, NIEBLA_SALA = 24;

    static final class Efectos extends DimensionSpecialEffects {
        Efectos() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 c, float brillo) {
            return c;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registro {
        @SubscribeEvent
        public static void efectos(RegisterDimensionSpecialEffectsEvent e) {
            e.register(new ResourceLocation(Miscelanea.MOD_ID, "purgatorio"), new Efectos());
        }
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
    public static final class Niebla {
        static boolean aqui() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && Purgatorio.en(mc.level);
        }

        static boolean enSala() {
            Minecraft mc = Minecraft.getInstance();
            var c = mc.gameRenderer.getMainCamera().getBlockPosition();
            return PurgatorioGenerador.zona(c.getX() >> 4, c.getZ() >> 4) == PurgatorioGenerador.Zona.SALA;
        }

        @SubscribeEvent
        public static void niebla(ViewportEvent.RenderFog e) {
            if (!aqui()) return;
            float lejos = Math.min(e.getFarPlaneDistance(), enSala() ? NIEBLA_SALA : NIEBLA_FERIA);
            e.setNearPlaneDistance(e.getMode() == FogRenderer.FogMode.FOG_SKY ? 0 : lejos * 0.15F);
            e.setFarPlaneDistance(lejos);
            e.setFogShape(FogShape.SPHERE);
            e.setCanceled(true);
        }

        @SubscribeEvent
        public static void color(ViewportEvent.ComputeFogColor e) {
            if (!aqui()) return;
            if (enSala()) {
                e.setRed(0.10F);
                e.setGreen(0.10F);
                e.setBlue(0.085F);
            } else {
                e.setRed(0.07F);
                e.setGreen(0.05F);
                e.setBlue(0.10F);
            }
        }
    }
}
