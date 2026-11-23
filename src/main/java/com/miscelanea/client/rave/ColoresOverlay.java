package com.miscelanea.client.rave;

import com.miscelanea.rave.RaveRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Visión de colores (caramelos de fiesta): un velo suave que va cambiando de color por toda la pantalla, más vivo en los bordes. */
public final class ColoresOverlay {
    private ColoresOverlay() {}

    public static void dibujar(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var e = mc.player.getEffect(RaveRegistro.COLORES.get());
        if (e == null) return;
        float t = (mc.player.tickCount + pt) / 60F;
        int c1 = Mth.hsvToRgb(t % 1F, 0.9F, 1F), c2 = Mth.hsvToRgb((t + 0.33F) % 1F, 0.9F, 1F);
        // se va apagando en los últimos segundos
        float k = Math.min(1F, e.getDuration() / 60F) * (e.getAmplifier() > 0 ? 1.5F : 1F);
        int a1 = (int) (30 * k) << 24, a2 = (int) (70 * k) << 24;
        g.fill(0, 0, w, h, a1 | c1);
        int b = h / 4;
        g.fillGradient(0, 0, w, b, a2 | c2, c2);
        g.fillGradient(0, h - b, w, h, c2, a2 | c2);
    }
}
