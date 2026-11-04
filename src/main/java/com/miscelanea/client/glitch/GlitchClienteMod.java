package com.miscelanea.client.glitch;

import com.miscelanea.Miscelanea;
import com.miscelanea.glitch.GlitchRegistro;
import com.miscelanea.glitch.MuestraGlitchItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro del cliente (bus del mod) de lo del glitch: color del líquido, partícula, muestra lanzada y pantalla rota. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GlitchClienteMod {
    private GlitchClienteMod() {}

    /** Colores del glitch entre los que salta el líquido. */
    private static final int[] PALETA = {0xFF00FF, 0x0A0012, 0x00FFFF, 0x8A2BE2, 0xFF2A9A, 0x5A00B4};

    @SubscribeEvent
    public static void colores(RegisterColorHandlersEvent.Item e) {
        // capa 0 del frasco de poción del juego (potion_overlay): el líquido
        e.register((st, capa) -> capa == 0 ? colorLiquido(st) : -1,
                GlitchRegistro.MUESTRA.get(), GlitchRegistro.MUESTRA_LANZADA_ITEM.get());
    }

    /**
     * El líquido de la muestra: casi siempre un morado que va y viene, con fotogramas sueltos de otro color (magenta,
     * negro, cian...). Se calcula con el reloj en cada fotograma. Tirada en el suelo (inestable) parpadea en blanco
     * cada vez más deprisa, como un creeper a punto de estallar.
     */
    public static int colorLiquido(ItemStack st) {
        long ms = Util.getMillis();
        CompoundTag tag = st.getTag();
        var level = Minecraft.getInstance().level;
        if (tag != null && tag.contains(MuestraGlitchItem.TAG_INESTABLE) && level != null) {
            float k = Mth.clamp((level.getGameTime() - tag.getLong(MuestraGlitchItem.TAG_INESTABLE)) / (float) MuestraGlitchItem.MECHA_TICKS, 0.0F, 1.0F);
            long periodo = (long) (260 - 210 * k);
            if ((ms / periodo) % 2 == 0) return 0xFFFFFF;
        }
        int h = mezcla((int) (ms / 70));
        if ((h & 0xFF) < 70) return PALETA[((h >>> 8) & 0x7FFFFFFF) % PALETA.length];
        // base: entre morado y magenta, despacio
        float f = 0.5F + 0.5F * Mth.sin(ms / 420.0F);
        int r = (int) Mth.lerp(f, 0x6A, 0xE0), g = (int) Mth.lerp(f, 0x00, 0x10), b = (int) Mth.lerp(f, 0xD0, 0xFF);
        return r << 16 | g << 8 | b;
    }

    private static int mezcla(int x) {
        x *= 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return x;
    }

    @SubscribeEvent
    public static void particulas(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(GlitchRegistro.PARTICULA.get(), GlitchParticula.Provider::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(GlitchRegistro.MUESTRA_LANZADA.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("glitch_pantalla", GlitchPantalla::hud);
    }
}
