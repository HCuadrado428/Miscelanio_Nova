package com.miscelanea.client.consola;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de 160x144 en un búfer de índices de color (0..15) y una lista de textos encima.
 * La paleta decide los colores: verde de 4 tonos en la consola; en la recreativa, paletas de colores.
 * El búfer se sube a una textura (LcdTextura) y se puede enviar por red (la recreativa la retransmite).
 */
public class Lcd {
    public static final int W = 160, H = 144;
    public static final int[] VERDE = {0xFF9BBC0F, 0xFF8BAC0F, 0xFF306230, 0xFF0F380F};

    public int[] paleta = VERDE;
    public final byte[] buf = new byte[W * H];
    public final List<Object[]> textos = new ArrayList<>();
    public Font font;

    public void empezar() {
        textos.clear();
    }

    public void rect(int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(W, x + w), y1 = Math.min(H, y + h);
        if (x1 <= x0 || y1 <= y0) return;
        byte b = (byte) (c & 15);
        for (int j = y0; j < y1; j++) java.util.Arrays.fill(buf, j * W + x0, j * W + x1, b);
    }

    public void px(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < W && y < H) buf[y * W + x] = (byte) (c & 15);
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

    private static int indice(char ch) {
        return ch <= '9' ? ch - '0' : ch - 'a' + 10;
    }

    /** Sprite en texto: '.' transparente, '0'..'9' y 'a'..'f' índice de color. */
    public void sprite(String[] s, int x, int y) {
        for (int j = 0; j < s.length; j++) {
            String f = s[j];
            for (int i = 0; i < f.length(); i++) {
                char ch = f.charAt(i);
                if (ch != '.') px(x + i, y + j, indice(ch));
            }
        }
    }

    public void spriteEspejo(String[] s, int x, int y) {
        for (int j = 0; j < s.length; j++) {
            String f = s[j];
            for (int i = 0; i < f.length(); i++) {
                char ch = f.charAt(i);
                if (ch != '.') px(x + f.length() - 1 - i, y + j, indice(ch));
            }
        }
    }

    public void texto(String s, int x, int y, int c) {
        textos.add(new Object[]{s, x, y, c});
    }

    public void textoC(String s, int cx, int y, int c) {
        texto(s, cx - ancho(s) / 2, y, c);
    }

    public void textoD(String s, int xd, int y, int c) {
        texto(s, xd - ancho(s), y, c);
    }

    public int ancho(String s) {
        return font == null ? s.length() * 6 : font.width(s);
    }

    public int color(int indice) {
        return paleta[Math.min(paleta.length - 1, indice & 15)];
    }
}
