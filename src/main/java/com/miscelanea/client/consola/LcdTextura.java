package com.miscelanea.client.consola;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Textura dinámica donde se vuelca una Lcd (píxeles nítidos). */
public class LcdTextura {
    private final DynamicTexture tex;
    public final ResourceLocation rl;

    public LcdTextura(String nombre) {
        tex = new DynamicTexture(Lcd.W, Lcd.H, false);
        rl = Minecraft.getInstance().getTextureManager().register("miscelanea_lcd_" + nombre, tex);
    }

    public void subir(Lcd l) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        int[] pal = l.paleta;
        int[] abgr = new int[16];
        for (int i = 0; i < 16; i++) {
            int c = pal[Math.min(pal.length - 1, i)];
            abgr[i] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
        }
        byte[] b = l.buf;
        for (int y = 0; y < Lcd.H; y++)
            for (int x = 0; x < Lcd.W; x++) img.setPixelRGBA(x, y, abgr[b[y * Lcd.W + x] & 15]);
        tex.upload();
    }

    /** Dibuja la pantalla y sus textos en (0,0)-(160,144) de la pose actual. */
    public void dibujar(GuiGraphics g, Lcd l) {
        g.blit(rl, 0, 0, Lcd.W, Lcd.H, 0, 0, Lcd.W, Lcd.H, Lcd.W, Lcd.H);
        for (Object[] t : l.textos)
            g.drawString(Minecraft.getInstance().font, (String) t[0], (int) t[1], (int) t[2], l.color((int) t[3]), false);
    }

    public void cerrar() {
        Minecraft.getInstance().getTextureManager().release(rl);
    }
}
