package com.miscelanea.client.movil;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Filtros de la cámara, en el procesador (las imágenes son pequeñas: la foto es de 180x320). Trabajan sobre la imagen tal
 * cual sale del framebuffer (de abajo arriba); a ninguno le importa dónde está arriba.
 * <p>
 * 0 normal · 1 blanco y negro · 2 sepia · 3 antigua (desvaída, con viñeta y grano) · 4 viva (más color y contraste) ·
 * 5 visión nocturna (verde, aclarada, con líneas) · 6 8 bits (píxeles gordos y pocos colores) · 7 Game Boy (4 verdes) ·
 * 8 térmica (de azul a amarillo según lo claro que es).
 */
public final class Filtros {
    private Filtros() {}

    public static final int N = 9;

    private static final int[] GAMEBOY = {0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F};

    public static void aplicar(NativeImage img, int f) {
        if (f <= 0 || f >= N) return;
        int w = img.getWidth(), h = img.getHeight();
        if (f == 6) pixelar(img, Math.max(2, w / 45));
        if (f == 7) pixelar(img, Math.max(1, w / 80));
        long semilla = System.nanoTime();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = img.getPixelRGBA(x, y);
                int r = c & 0xFF, g = (c >> 8) & 0xFF, b = (c >> 16) & 0xFF;
                float l = (0.299F * r + 0.587F * g + 0.114F * b) / 255F;
                float nr, ng, nb;
                switch (f) {
                    case 1 -> nr = ng = nb = contraste(l, 1.15F);
                    case 2 -> {
                        nr = l * 1.07F + 0.06F;
                        ng = l * 0.88F + 0.03F;
                        nb = l * 0.66F;
                    }
                    case 3 -> {
                        float v = vineta(x, y, w, h, 0.75F);
                        float ruido = (azar(semilla, x, y) - 0.5F) * 0.12F;
                        float k = l * 0.8F + 0.12F + ruido;
                        nr = (k * 1.05F + 0.05F) * v;
                        ng = (k * 0.92F + 0.03F) * v;
                        nb = (k * 0.72F + 0.04F) * v;
                    }
                    case 4 -> {
                        float s = 1.65F;
                        nr = contraste(l + (r / 255F - l) * s, 1.12F);
                        ng = contraste(l + (g / 255F - l) * s, 1.12F);
                        nb = contraste(l + (b / 255F - l) * s, 1.12F);
                    }
                    case 5 -> {
                        float k = (float) Math.pow(Math.min(1, l * 1.6F + 0.04F), 0.55);
                        k += (azar(semilla, x, y) - 0.5F) * 0.16F;
                        if (y % 2 == 0) k *= 0.78F;
                        k *= vineta(x, y, w, h, 0.9F);
                        nr = k * 0.25F;
                        ng = k * 1.05F;
                        nb = k * 0.3F;
                    }
                    case 6 -> {
                        nr = Math.round(r / 255F * 3) / 3F;
                        ng = Math.round(g / 255F * 3) / 3F;
                        nb = Math.round(b / 255F * 2) / 2F;
                    }
                    case 7 -> {
                        // un poco de trama para que no se vean bandas
                        float d = ((x + y) % 2 == 0 ? 0.06F : -0.06F);
                        int i = Math.max(0, Math.min(3, (int) ((contraste(l, 1.2F) + d) * 4)));
                        int c2 = GAMEBOY[i];
                        nr = ((c2 >> 16) & 0xFF) / 255F;
                        ng = ((c2 >> 8) & 0xFF) / 255F;
                        nb = (c2 & 0xFF) / 255F;
                    }
                    default -> {
                        // térmica: negro-azul-morado-rojo-amarillo-blanco
                        float t = contraste(l, 1.3F);
                        nr = Math.min(1, Math.max(0, t * 2.2F - 0.35F));
                        ng = Math.min(1, Math.max(0, t * 2.4F - 1.3F));
                        nb = t < 0.45F ? 0.25F + t * 1.3F : Math.max(0, 0.83F - (t - 0.45F) * 2.6F) + Math.max(0, (t - 0.9F) * 8);
                    }
                }
                img.setPixelRGBA(x, y, 0xFF000000 | (a(nb) << 16) | (a(ng) << 8) | a(nr));
            }
    }

    private static int a(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    private static float contraste(float v, float k) {
        return (v - 0.5F) * k + 0.5F;
    }

    private static float vineta(int x, int y, int w, int h, float fuerza) {
        float dx = (x + 0.5F) / w - 0.5F, dy = (y + 0.5F) / h - 0.5F;
        float d = (dx * dx + dy * dy) * 2.2F;
        return Math.max(0.15F, 1 - d * fuerza);
    }

    private static float azar(long s, int x, int y) {
        long n = s * 6364136223846793005L + x * 73856093L + y * 19349663L;
        n ^= n >>> 29;
        n *= 0xBF58476D1CE4E5B9L;
        n ^= n >>> 32;
        return (n & 0xFFFF) / 65535F;
    }

    /** Media de cada bloque de t x t píxeles. */
    private static void pixelar(NativeImage img, int t) {
        if (t <= 1) return;
        int w = img.getWidth(), h = img.getHeight();
        for (int by = 0; by < h; by += t)
            for (int bx = 0; bx < w; bx += t) {
                int sr = 0, sg = 0, sb = 0, n = 0;
                for (int y = by; y < Math.min(h, by + t); y++)
                    for (int x = bx; x < Math.min(w, bx + t); x++) {
                        int c = img.getPixelRGBA(x, y);
                        sr += c & 0xFF;
                        sg += (c >> 8) & 0xFF;
                        sb += (c >> 16) & 0xFF;
                        n++;
                    }
                int c = 0xFF000000 | ((sb / n) << 16) | ((sg / n) << 8) | (sr / n);
                for (int y = by; y < Math.min(h, by + t); y++)
                    for (int x = bx; x < Math.min(w, bx + t); x++) img.setPixelRGBA(x, y, c);
            }
    }
}
