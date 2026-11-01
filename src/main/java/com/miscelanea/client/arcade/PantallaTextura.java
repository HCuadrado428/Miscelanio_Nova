package com.miscelanea.client.arcade;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Textura dinámica donde se vuelca una Pantalla de la recreativa. */
public class PantallaTextura {
    private static final int[] ABGR = new int[64];

    static {
        for (int i = 0; i < 64; i++) {
            int c = Pantalla.PALETA[i];
            ABGR[i] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
        }
    }

    private final DynamicTexture tex;
    public final ResourceLocation rl;

    public PantallaTextura(String nombre) {
        tex = new DynamicTexture(Pantalla.W, Pantalla.H, false);
        rl = Minecraft.getInstance().getTextureManager().register("miscelanea_arcade_" + nombre, tex);
    }

    public void subir(Pantalla p) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        byte[] b = p.buf;
        for (int y = 0; y < Pantalla.H; y++)
            for (int x = 0; x < Pantalla.W; x++) img.setPixelRGBA(x, y, ABGR[b[y * Pantalla.W + x] & 63]);
        tex.upload();
    }

    public void dibujar(GuiGraphics g, Pantalla p) {
        g.blit(rl, 0, 0, Pantalla.W, Pantalla.H, 0, 0, Pantalla.W, Pantalla.H, Pantalla.W, Pantalla.H);
        for (Object[] t : p.textos)
            g.drawString(Minecraft.getInstance().font, (String) t[0], (int) t[1], (int) t[2], p.color((int) t[3]), false);
    }

    public void cerrar() {
        Minecraft.getInstance().getTextureManager().release(rl);
    }
}
