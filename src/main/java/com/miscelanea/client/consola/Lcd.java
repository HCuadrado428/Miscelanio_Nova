package com.miscelanea.client.consola;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Pantalla LCD de 160x144 con 4 tonos de verde. Los textos se dibujan al final, encima de todo. */
public class Lcd {
    public static final int W = 160, H = 144;
    public static final int[] PAL = {0xFF9BBC0F, 0xFF8BAC0F, 0xFF306230, 0xFF0F380F};

    GuiGraphics g;
    Font font;
    final List<Object[]> textos = new ArrayList<>();

    public void rect(int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(W, x + w), y1 = Math.min(H, y + h);
        if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, PAL[c & 3]);
    }

    public void px(int x, int y, int c) {
        rect(x, y, 1, 1, c);
    }

    public void marco(int x, int y, int w, int h, int c) {
        rect(x, y, w, 1, c);
        rect(x, y + h - 1, w, 1, c);
        rect(x, y, 1, h, c);
        rect(x + w - 1, y, 1, h, c);
    }

    /** Caja de diálogo: fondo claro con borde doble oscuro. */
    public void caja(int x, int y, int w, int h) {
        rect(x, y, w, h, 0);
        marco(x, y, w, h, 3);
        marco(x + 2, y + 2, w - 4, h - 4, 2);
    }

    /** Sprite en texto: '.' transparente, '0'..'3' tono. */
    public void sprite(String[] s, int x, int y) {
        for (int j = 0; j < s.length; j++) {
            String f = s[j];
            int i = 0;
            while (i < f.length()) {
                char ch = f.charAt(i);
                if (ch == '.') {
                    i++;
                    continue;
                }
                int k = i;
                while (k < f.length() && f.charAt(k) == ch) k++;
                rect(x + i, y + j, k - i, 1, ch - '0');
                i = k;
            }
        }
    }

    public void spriteEspejo(String[] s, int x, int y) {
        for (int j = 0; j < s.length; j++) {
            String f = s[j];
            for (int i = 0; i < f.length(); i++) {
                char ch = f.charAt(i);
                if (ch != '.') px(x + f.length() - 1 - i, y + j, ch - '0');
            }
        }
    }

    public void texto(String s, int x, int y, int c) {
        textos.add(new Object[]{s, x, y, c});
    }

    public void textoC(String s, int cx, int y, int c) {
        texto(s, cx - font.width(s) / 2, y, c);
    }

    public void textoD(String s, int xd, int y, int c) {
        texto(s, xd - font.width(s), y, c);
    }

    public int ancho(String s) {
        return font.width(s);
    }

    void volcarTextos() {
        for (Object[] t : textos) g.drawString(font, (String) t[0], (int) t[1], (int) t[2], PAL[(int) t[3] & 3], false);
        textos.clear();
    }
}
