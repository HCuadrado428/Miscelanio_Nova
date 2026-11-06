package com.miscelanea.client.arcade;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Textura dinámica donde se vuelca una Pantalla de la recreativa. Las de las máquinas del mundo (crt) llevan un toque de
 * tubo de imagen horneado al subirla: líneas de barrido, esquinas más oscuras y un brillo suave arriba a la izquierda.
 */
public class PantallaTextura {
    private static final int[] ABGR = new int[64];

    static {
        for (int i = 0; i < 64; i++) {
            int c = Pantalla.PALETA[i];
            ABGR[i] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
        }
    }

    /** Multiplicador de brillo (0..256) y brillo añadido (0..255) de cada píxel para el toque de tubo de imagen. */
    private static int[] crtMul, crtSuma;

    private final DynamicTexture tex;
    public final ResourceLocation rl;
    private final boolean crt;

    public PantallaTextura(String nombre) {
        this(nombre, false);
    }

    public PantallaTextura(String nombre, boolean crt) {
        this.crt = crt;
        if (crt && crtMul == null) tablaCrt();
        tex = new DynamicTexture(Pantalla.W, Pantalla.H, false);
        rl = Minecraft.getInstance().getTextureManager().register("miscelanea_arcade_" + nombre, tex);
    }

    public void subir(Pantalla p) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        byte[] b = p.buf;
        if (!crt) {
            for (int y = 0; y < Pantalla.H; y++)
                for (int x = 0; x < Pantalla.W; x++) img.setPixelRGBA(x, y, ABGR[b[y * Pantalla.W + x] & 63]);
        } else {
            for (int y = 0; y < Pantalla.H; y++)
                for (int x = 0; x < Pantalla.W; x++) {
                    int i = y * Pantalla.W + x, c = ABGR[b[i] & 63], k = crtMul[i], a = crtSuma[i];
                    int r = Math.min(255, ((c & 255) * k >> 8) + a), g = Math.min(255, ((c >> 8 & 255) * k >> 8) + a),
                            bl = Math.min(255, ((c >> 16 & 255) * k >> 8) + a);
                    img.setPixelRGBA(x, y, 0xFF000000 | bl << 16 | g << 8 | r);
                }
        }
        tex.upload();
    }

    private static void tablaCrt() {
        int n = Pantalla.W * Pantalla.H;
        crtMul = new int[n];
        crtSuma = new int[n];
        for (int y = 0; y < Pantalla.H; y++)
            for (int x = 0; x < Pantalla.W; x++) {
                float u = (x + 0.5F) / Pantalla.W * 2 - 1, v = (y + 0.5F) / Pantalla.H * 2 - 1;
                // viñeta: el centro igual, las esquinas hasta un 35 % más oscuras
                float d = (u * u * 0.8F + v * v) / 1.8F;
                float k = 1.0F - 0.35F * d * d * 1.6F;
                // líneas de barrido cada dos filas (suaves, para que de lejos no hagan aguas)
                if ((y & 1) == 1) k *= 0.9F;
                // reflejo del cristal: una mancha suave arriba a la izquierda
                float rx = u + 0.55F, ry = v + 0.6F;
                float refl = Math.max(0, 1 - (rx * rx + ry * ry * 1.6F) / 0.12F);
                crtMul[y * Pantalla.W + x] = Math.max(0, Math.min(256, (int) (k * 256)));
                crtSuma[y * Pantalla.W + x] = (int) (refl * 14);
            }
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
