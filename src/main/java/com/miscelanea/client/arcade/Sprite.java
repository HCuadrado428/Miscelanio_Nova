package com.miscelanea.client.arcade;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Sprite en índices de la paleta de la recreativa (255 = transparente). Se cargan de hojas PNG en assets/miscelanea/arcade. */
public final class Sprite {
    public final int w, h;
    public final byte[] px;
    private static final Map<String, Sprite[]> HOJAS = new HashMap<>();
    private static final Map<Integer, Integer> EXACTO = new HashMap<>();

    static {
        for (int i = 0; i < 64; i++) EXACTO.put(Pantalla.PALETA[i] & 0xFFFFFF, i);
    }

    public Sprite(int w, int h, byte[] px) {
        this.w = w;
        this.h = h;
        this.px = px;
    }

    /** Hoja dividida en cuadros de fw x fh (de izquierda a derecha y de arriba abajo). */
    public static Sprite[] hoja(String nombre, int fw, int fh) {
        String clave = nombre + "@" + fw + "x" + fh;
        Sprite[] s = HOJAS.get(clave);
        if (s != null) return s;
        s = cargar(nombre, fw, fh);
        HOJAS.put(clave, s);
        return s;
    }

    public static Sprite uno(String nombre) {
        return hoja(nombre, 0, 0)[0];
    }

    private static Sprite[] cargar(String nombre, int fw, int fh) {
        ResourceLocation rl = new ResourceLocation("miscelanea", "arcade/" + nombre + ".png");
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(rl).open(); NativeImage img = NativeImage.read(in)) {
            int W = img.getWidth(), H = img.getHeight();
            if (fw <= 0) fw = W;
            if (fh <= 0) fh = H;
            int cols = W / fw, filas = H / fh;
            Sprite[] out = new Sprite[cols * filas];
            for (int fy = 0; fy < filas; fy++)
                for (int fx = 0; fx < cols; fx++) {
                    byte[] p = new byte[fw * fh];
                    for (int y = 0; y < fh; y++)
                        for (int x = 0; x < fw; x++) {
                            int abgr = img.getPixelRGBA(fx * fw + x, fy * fh + y);
                            int a = (abgr >>> 24) & 255;
                            if (a < 128) {
                                p[y * fw + x] = (byte) 255;
                                continue;
                            }
                            int rgb = ((abgr & 255) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 255);
                            Integer i = EXACTO.get(rgb);
                            p[y * fw + x] = (byte) (i != null ? i : Pantalla.cercano(0xFF000000 | rgb));
                        }
                    out[fy * cols + fx] = new Sprite(fw, fh, p);
                }
            return out;
        } catch (Exception e) {
            return new Sprite[]{new Sprite(1, 1, new byte[]{(byte) 255})};
        }
    }

    public static void olvidar() {
        HOJAS.clear();
    }
}
