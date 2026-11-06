package com.miscelanea.client.arcade;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Pixel art de la pizarra de récords de la feria, a 32 píxeles por bloque (como el resto del mod), hecho a la medida de
 * cada grupo de carteles: marco de madera con clavos, pizarra verde con polvo de tiza, guirnalda de banderines arriba y
 * repisa con tizas y borrador abajo. Se genera una vez por tamaño y se guarda.
 */
final class PizarraTextura {
    private PizarraTextura() {}

    static final int PX = 32;
    private static final Map<Integer, ResourceLocation> CACHE = new HashMap<>();

    static ResourceLocation de(int w, int h) {
        int clave = w * 64 + h;
        ResourceLocation rl = CACHE.get(clave);
        if (rl != null) return rl;
        NativeImage im = dibujar(w * PX, h * PX);
        rl = new ResourceLocation(Miscelanea.MOD_ID, "dinamica/pizarra_" + w + "x" + h);
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(im));
        CACHE.put(clave, rl);
        return rl;
    }

    /** Colores en ARGB; NativeImage guarda ABGR. */
    private static void px(NativeImage im, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= im.getWidth() || y >= im.getHeight()) return;
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        im.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
    }

    private static float hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    private static int tono(int argb, int d) {
        int r = Math.max(0, Math.min(255, ((argb >> 16) & 255) + d)), g = Math.max(0, Math.min(255, ((argb >> 8) & 255) + d)),
                b = Math.max(0, Math.min(255, (argb & 255) + d));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // madera (de oscuro a claro) y pizarra
    private static final int M0 = 0xFF3E2410, M1 = 0xFF5E3618, M2 = 0xFF7A4A24, M3 = 0xFF8F5A2E, M4 = 0xFFA86E3C;
    private static final int P0 = 0xFF1A2E22, P1 = 0xFF21392B, P2 = 0xFF284434;
    /** Grosor del marco y alto de la repisa, en píxeles. */
    static final int MARCO = 3, REPISA = 4, GUIRNALDA = 9;

    private static NativeImage dibujar(int W, int H) {
        NativeImage im = new NativeImage(W, H, false);
        // pizarra con grano y polvo de tiza
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                float n = hash(x, y, 1);
                px(im, x, y, n < 0.15F ? P0 : n > 0.9F ? P2 : P1);
            }
        // borrones de borrador: franjas horizontales algo más claras
        int borrones = W * H / 300;
        for (int i = 0; i < borrones; i++) {
            int bx = (int) (hash(i, 0, 7) * W), by = (int) (hash(i, 1, 7) * H), bw = 5 + (int) (hash(i, 2, 7) * 12);
            for (int k = 0; k < bw; k++)
                for (int j = 0; j < 2; j++) if (hash(bx + k, by + j, 9) < 0.7F) px(im, bx + k, by + j, tono(P2, 4));
        }
        // marco de madera: vetas horizontales arriba/abajo y verticales a los lados, luz arriba-izquierda
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int dx = Math.min(x, W - 1 - x), dy = Math.min(y, H - 1 - y);
                int d = Math.min(dx, dy);
                if (d >= MARCO) continue;
                boolean lado = dx < dy;
                float veta = hash(lado ? x : x / 3, lado ? y / 3 : y, 3);
                int c = veta < 0.25F ? M1 : veta > 0.85F ? M3 : M2;
                if (d == 0) c = M0;
                else if (d == MARCO - 1) c = (x < W / 2 && lado) || (y < H / 2 && !lado) ? M4 : M1;   // bisel
                px(im, x, y, c);
            }
        // clavos en las esquinas
        for (int[] c : new int[][]{{1, 1}, {W - 2, 1}, {1, H - 2}, {W - 2, H - 2}}) {
            px(im, c[0], c[1], 0xFFB8B8C0);
        }
        // guirnalda: cuerda que cuelga un poco y banderines triangulares de colores
        int[] tela = {0xFFD83A3A, 0xFFF2C640, 0xFF3A6CD8, 0xFFF0EEE6, 0xFF46B060};
        int ancho = W - 2 * MARCO;
        for (int x = MARCO; x < W - MARCO; x++) {
            float t = (x - MARCO) / (float) Math.max(1, ancho);
            int y = MARCO + 1 + Math.round((float) Math.sin(t * Math.PI * Math.max(1, W / 64F)) * 1.2F + 0.6F);
            px(im, x, y, 0xFFE8DFC8);
            if ((x - MARCO) % 8 == 1 && x + 6 < W - MARCO) {
                int c = tela[((x - MARCO) / 8) % tela.length];
                for (int row = 0; row < 5; row++)
                    for (int k = row; k < 6 - row; k++) {
                        int cc = k == 5 - row ? tono(c, -40) : row == 0 ? tono(c, 20) : c;
                        px(im, x + k, y + 1 + row, cc);
                    }
            }
        }
        // repisa abajo (sobresale) con tizas y borrador
        for (int y = H - MARCO - REPISA; y < H - MARCO; y++)
            for (int x = MARCO - 1; x < W - MARCO + 1; x++) {
                int fila = y - (H - MARCO - REPISA);
                int c = fila == 0 ? M4 : fila == REPISA - 1 ? M0 : hash(x / 2, y, 5) < 0.3F ? M2 : M3;
                px(im, x, y, c);
            }
        int[] tizas = {0xFFF4F0E6, 0xFFFFE97A, 0xFFFF9CC8, 0xFF9CDCFF, 0xFFB8F59A};
        int ty = H - MARCO - REPISA - 1, n = Math.min(5, 2 + W / 48);
        for (int i = 0; i < n; i++) {
            int x = MARCO + 3 + i * 5;
            for (int k = 0; k < 3; k++) px(im, x + k, ty, k == 2 ? tono(tizas[i], -45) : tizas[i]);
        }
        int bx = W - MARCO - 11;
        for (int k = 0; k < 8; k++) {
            px(im, bx + k, ty - 1, 0xFFD8C090);
            px(im, bx + k, ty, 0xFF3A3A44);
        }
        px(im, bx + 7, ty - 1, 0xFFA89060);
        return im;
    }
}
