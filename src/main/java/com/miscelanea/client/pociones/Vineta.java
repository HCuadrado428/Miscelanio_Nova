package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Viñeta de pantalla de las pociones: una textura blanca con alfa radial suave (vineta.png) que se tiñe y se estira a
 * toda la pantalla. Sustituye a las franjas de degradado de arriba y abajo, que se veían como dos bandas.
 */
final class Vineta {
    private Vineta() {}

    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/pociones/vineta.png");

    /** Viñeta de color {@code rgb} con opacidad {@code a}; {@code escala} > 1 la abre (menos borde), < 1 la cierra. */
    static void dibujar(GuiGraphics g, int w, int h, int rgb, float a, float escala) {
        if (a <= 0.004F) return;
        int ew = Math.round(w * escala), eh = Math.round(h * escala);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, Mth.clamp(a, 0F, 1F));
        g.blit(TEX, (w - ew) / 2, (h - eh) / 2, ew, eh, 0F, 0F, 256, 256, 256, 256);
        // si se cierra (escala < 1), lo que queda fuera de la textura se rellena del mismo color
        if (escala < 1F) {
            int x0 = (w - ew) / 2, y0 = (h - eh) / 2;
            int c = (Math.round(Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            g.fill(0, 0, w, y0, c);
            g.fill(0, y0 + eh, w, h, c);
            g.fill(0, y0, x0, y0 + eh, c);
            g.fill(x0 + ew, y0, w, y0 + eh, c);
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }
}
