package com.miscelanea.client;

import com.miscelanea.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Pantallazo blanco del flashbang que se desvanece poco a poco, con pitido de oídos. */
public final class FlashOverlay {
    private static long inicio = -1L;
    private static long duracion = 0L;
    private static float intensidad = 0.0F;

    private FlashOverlay() {}

    public static void disparar(float i) {
        // Si ya estaba cegado, nos quedamos con el más fuerte
        float actual = alphaActual();
        if (i < actual) return;
        intensidad = Mth.clamp(i, 0.0F, 1.0F);
        duracion = 1500L + (long) (4000L * intensidad);
        inicio = Util.getMillis();

        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PITIDO.get(), 1.0F, 0.4F + 0.6F * intensidad));
    }

    private static float alphaActual() {
        if (inicio < 0) return 0.0F;
        float t = (Util.getMillis() - inicio) / (float) duracion;
        if (t >= 1.0F) return 0.0F;
        // Blanco total el primer 25 %, luego se desvanece (curva suave)
        float a = t < 0.25F ? 1.0F : 1.0F - (t - 0.25F) / 0.75F;
        return intensidad * a * a * (3.0F - 2.0F * a);
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        float a = alphaActual();
        if (a <= 0.0F) return;
        int alpha = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        graphics.fill(0, 0, width, height, (alpha << 24) | 0xFFFFFF);
    }
}
