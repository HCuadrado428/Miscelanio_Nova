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

    /** Hasta dónde se ve (bloques) en cada zona: al aire libre, un poco más que dentro. */
    static float niebla(PurgatorioGenerador.Zona z) {
        return switch (z) {
            case FERIA -> 34;
            case SALA -> 24;
            case RECREATIVA -> 22;
            case SIN_TEXTURA -> 30;
        };
    }

    /** Color de la niebla de cada zona (r, g, b). */
    static float[] colorNiebla(PurgatorioGenerador.Zona z) {
        return switch (z) {
            case FERIA -> new float[]{0.07F, 0.05F, 0.10F};
            case SALA -> new float[]{0.10F, 0.10F, 0.085F};
            case RECREATIVA -> new float[]{0.05F, 0.02F, 0.09F};
            case SIN_TEXTURA -> new float[]{0.0F, 0.0F, 0.0F};
        };
    }

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

        static PurgatorioGenerador.Zona zona() {
            Minecraft mc = Minecraft.getInstance();
            var c = mc.gameRenderer.getMainCamera().getBlockPosition();
            return PurgatorioGenerador.zona(c.getX() >> 4, c.getZ() >> 4);
        }

        @SubscribeEvent
        public static void niebla(ViewportEvent.RenderFog e) {
            if (!aqui()) return;
            float lejos = Math.min(e.getFarPlaneDistance(), niebla(zona()));
            e.setNearPlaneDistance(e.getMode() == FogRenderer.FogMode.FOG_SKY ? 0 : lejos * 0.15F);
            e.setFarPlaneDistance(lejos);
            e.setFogShape(FogShape.SPHERE);
            e.setCanceled(true);
        }

        @SubscribeEvent
        public static void color(ViewportEvent.ComputeFogColor e) {
            if (!aqui()) return;
            float[] c = colorNiebla(zona());
            e.setRed(c[0]);
            e.setGreen(c[1]);
            e.setBlue(c[2]);
        }
    }

    // ------------------------------------------------------------------ ambiente
    /**
     * Un sonido de fondo en bucle por zona (sin posición: suena "en la cabeza"); al cambiar de zona el de antes se
     * apaga poco a poco y entra el nuevo. Solo hay uno sonando a la vez.
     */
    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
    public static final class Ambiente {
        private static final java.util.EnumMap<PurgatorioGenerador.Zona, Bucle> SONANDO = new java.util.EnumMap<>(PurgatorioGenerador.Zona.class);

        static net.minecraft.sounds.SoundEvent sonido(PurgatorioGenerador.Zona z) {
            return switch (z) {
                case FERIA -> com.miscelanea.registry.ModSounds.PURGATORIO_FERIA.get();
                case SALA -> com.miscelanea.registry.ModSounds.PURGATORIO_SALA.get();
                case RECREATIVA -> com.miscelanea.registry.ModSounds.PURGATORIO_RECREATIVA.get();
                case SIN_TEXTURA -> com.miscelanea.registry.ModSounds.PURGATORIO_SIN_TEXTURA.get();
            };
        }

        /** Para la prueba: qué ambientes suenan y a qué volumen. */
        public static String sonando() {
            StringBuilder b = new StringBuilder();
            SONANDO.forEach((z, s) -> b.append(z).append(String.format("=%.2f ", s.vol())));
            return b.length() == 0 ? "ninguno" : b.toString().trim();
        }

        @SubscribeEvent
        public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
            if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            PurgatorioGenerador.Zona aqui = mc.player != null && Niebla.aqui() && !mc.isPaused() ? Niebla.zona() : null;
            SONANDO.values().removeIf(b -> b.isStopped());
            for (var en : SONANDO.entrySet()) en.getValue().quiere = en.getKey() == aqui;
            if (aqui != null && !SONANDO.containsKey(aqui)) {
                Bucle b = new Bucle(sonido(aqui));
                SONANDO.put(aqui, b);
                mc.getSoundManager().play(b);
            }
        }
    }

    static final class Bucle extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        /** Volumen al que va (y cuánto sube o baja por tic: 2 s de fundido). */
        static final float MAX = 0.55F, PASO = MAX / 40;
        boolean quiere = true;

        Bucle(net.minecraft.sounds.SoundEvent s) {
            super(s, net.minecraft.sounds.SoundSource.AMBIENT, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        float vol() {
            return volume;
        }

        @Override
        public void tick() {
            if (quiere) volume = Math.min(MAX, volume + PASO);
            else {
                volume -= PASO;
                if (volume <= 0) {
                    volume = 0;
                    stop();
                }
            }
        }
    }
}
