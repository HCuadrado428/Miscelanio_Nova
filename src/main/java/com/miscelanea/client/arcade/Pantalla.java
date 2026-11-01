package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Lcd;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de la recreativa: 256x192 con una paleta fija de 64 colores (índices en un búfer de bytes) y textos encima.
 * Los juegos clásicos (160x144, 4 o 16 colores) se dibujan centrados, traduciendo su paleta a la más parecida.
 */
public class Pantalla {
    public static final int W = 256, H = 192;
    public static final int[] PALETA = {0xFF08080C, 0xFF1C1C26, 0xFF363644, 0xFF585868, 0xFF808090, 0xFFAAAAB8, 0xFFD2D2DE, 0xFFF8F8FC, 0xFF560029, 0xFF800029, 0xFFAB0E27, 0xFFD62C2C, 0xFFFF6859, 0xFF8E3300, 0xFFBD5108, 0xFFEC8028, 0xFFFFA14A, 0xFF946300, 0xFFC59718, 0xFFF6D23C, 0xFFFFE05A, 0xFF333009, 0xFF4B4D14, 0xFF5E6625, 0xFF70803A, 0xFF98A859, 0xFF10652B, 0xFF238638, 0xFF3EA848, 0xFF69DC67, 0xFF02585E, 0xFF289C96, 0xFF4CCDAE, 0xFF001856, 0xFF002482, 0xFF153FAD, 0xFF3462D8, 0xFF5CB4FF, 0xFF4F8DC0, 0xFF78BCF0, 0xFF95ECFF, 0xFF3E1370, 0xFF612895, 0xFF8A46BA, 0xFFD772F1, 0xFFBD448C, 0xFFEC6CA8, 0xFFFF8AB3, 0xFF381403, 0xFF54270D, 0xFF70401E, 0xFF8C5C34, 0xFFBA8757, 0xFF8A563C, 0xFFB8815E, 0xFFE6B088, 0xFFFFCFA6, 0xFFAB9162, 0xFFD6C08A, 0xFFFFEAB3, 0xFF000000, 0xFFFFFFFF, 0xFFFFECA0, 0xFF5ADCFF};
    /** Índices con nombre de la paleta (rampas de oscuro a claro). */
    public static final int NEGRO = 0, GRIS_O = 2, GRIS = 4, GRIS_C = 6, BLANCO = 7, ROJO_O = 8, ROJO = 11, ROJO_C = 12, NARANJA = 15, AMARILLO = 19,
            AMARILLO_C = 20, MILITAR = 24, VERDE = 28, VERDE_C = 29, AZUL_O = 33, AZUL = 36, CIELO = 39, MORADO = 43, ROSA = 46, MARRON = 51, PIEL = 55,
            ARENA = 58, CREMA = 62, CIAN = 63;

    public final byte[] buf = new byte[W * H];
    public final List<Object[]> textos = new ArrayList<>();
    public Font font;
    /** Desplazamiento de cámara para los sprites y rectángulos "del mundo". */
    public int camX, camY;

    public void empezar() {
        textos.clear();
        camX = camY = 0;
    }

    public void limpiar(int c) {
        java.util.Arrays.fill(buf, (byte) c);
    }

    public void rect(int x, int y, int w, int h, int c) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(W, x + w), y1 = Math.min(H, y + h);
        if (x1 <= x0 || y1 <= y0) return;
        byte b = (byte) c;
        for (int j = y0; j < y1; j++) java.util.Arrays.fill(buf, j * W + x0, j * W + x1, b);
    }

    public void px(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < W && y < H) buf[y * W + x] = (byte) c;
    }

    public void marco(int x, int y, int w, int h, int c) {
        rect(x, y, w, 1, c);
        rect(x, y + h - 1, w, 1, c);
        rect(x, y, 1, h, c);
        rect(x + w - 1, y, 1, h, c);
    }

    public void linea(int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, e = dx + dy;
        for (int k = 0; k < 2000; k++) {
            px(x0, y0, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * e;
            if (e2 >= dy) {
                e += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                e += dx;
                y0 += sy;
            }
        }
    }

    public void circulo(int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy + 0.5));
            rect(cx - w, cy + dy, w * 2 + 1, 1, c);
        }
    }

    /** Sprite en la posición (x, y) de pantalla; espejo horizontal opcional. */
    public void spr(Sprite s, int x, int y, boolean espejo) {
        if (s == null) return;
        int w = s.w, h = s.h;
        for (int j = 0; j < h; j++) {
            int yy = y + j;
            if (yy < 0 || yy >= H) continue;
            int fila = j * w, base = yy * W;
            for (int i = 0; i < w; i++) {
                byte v = s.px[fila + (espejo ? w - 1 - i : i)];
                if (v == (byte) 255) continue;
                int xx = x + i;
                if (xx >= 0 && xx < W) buf[base + xx] = v;
            }
        }
    }

    /** Sprite teñido de un solo color (destello al recibir daño, siluetas). */
    public void sprColor(Sprite s, int x, int y, boolean espejo, int c) {
        if (s == null) return;
        for (int j = 0; j < s.h; j++)
            for (int i = 0; i < s.w; i++)
                if (s.px[j * s.w + (espejo ? s.w - 1 - i : i)] != (byte) 255) px(x + i, y + j, c);
    }

    /** Sprite en coordenadas del mundo (resta la cámara). */
    public void sprM(Sprite s, float x, float y, boolean espejo) {
        spr(s, Math.round(x) - camX, Math.round(y) - camY, espejo);
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
        return PALETA[indice & 63];
    }

    /** Índice de la paleta más parecido a un color ARGB. */
    public static int cercano(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255, mejor = 0;
        long md = Long.MAX_VALUE;
        for (int i = 0; i < 64; i++) {
            int c = PALETA[i];
            long dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255);
            long d = dr * dr * 3 + dg * dg * 6 + db * db;
            if (d < md) {
                md = d;
                mejor = i;
            }
        }
        return mejor;
    }

    /** Dibuja una pantalla clásica (160x144) centrada, con su paleta traducida. */
    public void clasica(Lcd l, int x0, int y0) {
        int[] mapa = new int[16];
        for (int i = 0; i < 16; i++) mapa[i] = cercano(l.paleta[Math.min(l.paleta.length - 1, i)]);
        for (int y = 0; y < Lcd.H; y++) {
            int yy = y0 + y;
            if (yy < 0 || yy >= H) continue;
            for (int x = 0; x < Lcd.W; x++) {
                int xx = x0 + x;
                if (xx >= 0 && xx < W) buf[yy * W + xx] = (byte) mapa[l.buf[y * Lcd.W + x] & 15];
            }
        }
        for (Object[] t : l.textos)
            textos.add(new Object[]{t[0], (int) t[1] + x0, (int) t[2] + y0, mapa[((int) t[3]) & 15]});
    }
}
