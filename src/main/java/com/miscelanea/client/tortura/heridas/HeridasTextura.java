package com.miscelanea.client.tortura.heridas;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.heridas.Herida;
import com.miscelanea.tortura.heridas.HeridasDatos;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Pinta las heridas de cada jugador en una textura 64x64 con la MISMA distribución de UV que una skin, para que la
 * capa (un modelo de jugador un pelo más hinchado) las ponga justo encima de cada pieza, píxel a píxel.
 * Las calcomanías salen de textures/heridas/calcomanias.png (cada píxel = un "rol"; ver gen_heridas.py) y se colorean
 * aquí según el tipo y lo curada que esté la herida.
 */
public final class HeridasTextura {
    private static final ResourceLocation ATLAS = new ResourceLocation(Miscelanea.MOD_ID, "textures/heridas/calcomanias.png");
    private static final int CELDA = 8, COLS = 8;
    private static byte[] roles;          // null = sin cargar; longitud 0 = no se pudo cargar
    private static int atlasW, atlasH;
    private static int[][] tam;           // [celda] = {ancho, alto}

    private static final Map<Integer, Lienzo> LIENZOS = new HashMap<>();

    private static final class Lienzo {
        DynamicTexture tex;
        ResourceLocation rl;
        int firma = Integer.MIN_VALUE;
        boolean fino;
    }

    private HeridasTextura() {}

    // ------------------------------------------------------------------ paletas (rol 1..5), por fotogramas clave
    // {progreso, r1,g1,b1,a1, r2,..., r5,g5,b5,a5}
    private static final float[][] CORTE = {
            {0.00F, 96, 6, 14, 255, 146, 14, 22, 255, 198, 58, 52, 235, 240, 120, 108, 255, 128, 8, 16, 230},
            {0.30F, 84, 12, 14, 255, 122, 26, 22, 255, 176, 64, 54, 220, 150, 36, 28, 255, 110, 16, 14, 0},
            {0.55F, 62, 24, 18, 255, 92, 40, 28, 250, 150, 86, 70, 150, 98, 44, 30, 250, 0, 0, 0, 0},
            {0.80F, 176, 112, 104, 190, 196, 136, 124, 150, 210, 160, 146, 0, 196, 136, 124, 150, 0, 0, 0, 0},
            {1.00F, 200, 150, 138, 0, 210, 160, 146, 0, 0, 0, 0, 0, 210, 160, 146, 0, 0, 0, 0, 0}};
    private static final float[][] RASGUNO = {
            {0.00F, 150, 14, 20, 255, 184, 46, 44, 235, 214, 104, 94, 190, 184, 46, 44, 235, 0, 0, 0, 0},
            {0.45F, 110, 36, 28, 240, 150, 70, 58, 200, 190, 120, 104, 120, 150, 70, 58, 200, 0, 0, 0, 0},
            {1.00F, 190, 130, 118, 0, 200, 150, 136, 0, 210, 160, 146, 0, 200, 150, 136, 0, 0, 0, 0, 0}};
    private static final float[][] MORATON = {
            {0.00F, 108, 22, 50, 235, 112, 46, 100, 215, 150, 78, 118, 140, 112, 46, 100, 215, 0, 0, 0, 0},
            {0.25F, 62, 26, 88, 235, 88, 50, 122, 215, 118, 86, 150, 140, 88, 50, 122, 215, 0, 0, 0, 0},
            {0.55F, 60, 66, 108, 220, 92, 108, 94, 200, 138, 148, 80, 120, 92, 108, 94, 200, 0, 0, 0, 0},
            {0.80F, 136, 136, 60, 170, 166, 158, 78, 140, 188, 174, 98, 80, 166, 158, 78, 140, 0, 0, 0, 0},
            {1.00F, 170, 160, 90, 0, 180, 170, 100, 0, 190, 176, 104, 0, 180, 170, 100, 0, 0, 0, 0, 0}};
    private static final float[][] QUEMADURA = {
            {0.00F, 30, 18, 16, 255, 128, 26, 18, 255, 206, 92, 62, 225, 238, 206, 164, 255, 52, 42, 40, 170},
            {0.35F, 64, 26, 22, 255, 150, 52, 40, 250, 212, 116, 94, 190, 226, 176, 148, 250, 52, 42, 40, 0},
            {0.70F, 176, 96, 86, 220, 200, 126, 112, 190, 216, 150, 132, 90, 210, 140, 124, 190, 0, 0, 0, 0},
            {1.00F, 200, 140, 126, 0, 210, 150, 136, 0, 220, 160, 146, 0, 210, 150, 136, 0, 0, 0, 0, 0}};

    private static float[][] paleta(Tipo t) {
        return switch (t) {
            case CORTE -> CORTE;
            case RASGUNO -> RASGUNO;
            case MORATON -> MORATON;
            case QUEMADURA -> QUEMADURA;
        };
    }

    /** Color ABGR (formato de NativeImage) de un rol con el progreso dado; 0 si no se ve. */
    private static int color(Tipo t, int rol, float p, float brillo) {
        float[][] k = paleta(t);
        int i = 0;
        while (i < k.length - 2 && p > k[i + 1][0]) i++;
        float[] a = k[i], b = k[i + 1];
        float f = Math.max(0, Math.min(1, (p - a[0]) / Math.max(0.0001F, b[0] - a[0])));
        int o = 1 + (rol - 1) * 4;
        float r = (a[o] + (b[o] - a[o]) * f) * brillo;
        float g = (a[o + 1] + (b[o + 1] - a[o + 1]) * f) * brillo;
        float bl = (a[o + 2] + (b[o + 2] - a[o + 2]) * f) * brillo;
        float al = a[o + 3] + (b[o + 3] - a[o + 3]) * f;
        al *= Math.min(1F, (1F - p) / 0.15F);       // se desvanece del todo al final
        if (al < 12) return 0;
        return abgr(r, g, bl, al);
    }

    private static int abgr(float r, float g, float b, float a) {
        int ri = Math.max(0, Math.min(255, Math.round(r))), gi = Math.max(0, Math.min(255, Math.round(g)));
        int bi = Math.max(0, Math.min(255, Math.round(b))), ai = Math.max(0, Math.min(255, Math.round(a)));
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    // ------------------------------------------------------------------ atlas

    private static void cargarAtlas() {
        if (roles != null) return;
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(ATLAS).open(); NativeImage img = NativeImage.read(in)) {
            atlasW = img.getWidth();
            atlasH = img.getHeight();
            byte[] r = new byte[atlasW * atlasH];
            for (int y = 0; y < atlasH; y++)
                for (int x = 0; x < atlasW; x++) {
                    int c = img.getPixelRGBA(x, y);          // ABGR
                    int a = (c >>> 24) & 255, rojo = c & 255;
                    r[y * atlasW + x] = (byte) (a < 128 ? 0 : Math.max(0, Math.min(5, (rojo + 20) / 40)));
                }
            roles = r;
            int celdas = (atlasW / CELDA) * (atlasH / CELDA);
            tam = new int[celdas][2];
            for (int c = 0; c < celdas; c++) {
                int cx = (c % COLS) * CELDA, cy = (c / COLS) * CELDA, w = 0, h = 0;
                for (int y = 0; y < CELDA; y++)
                    for (int x = 0; x < CELDA; x++)
                        if (roles[(cy + y) * atlasW + cx + x] != 0) {
                            w = Math.max(w, x + 1);
                            h = Math.max(h, y + 1);
                        }
                tam[c][0] = w;
                tam[c][1] = h;
            }
        } catch (Exception ex) {
            Miscelanea.LOGGER.warn("Heridas: no se pudo leer {}", ATLAS, ex);
            roles = new byte[0];
            tam = new int[0][2];
        }
    }

    // ------------------------------------------------------------------ UV de las caras (skin 64x64, capa base)

    /** {u, v, ancho, alto} de la cara de la pieza en la skin. */
    static int[] cara(Parte parte, int cara, boolean fino) {
        int u0 = 16, v0 = 48, w = 4, h = 12, d = 4;
        switch (parte) {
            case CABEZA -> { u0 = 0; v0 = 0; w = 8; h = 8; d = 8; }
            case TORSO -> { u0 = 16; v0 = 16; w = 8; }
            case BRAZO_DER -> { u0 = 40; v0 = 16; w = fino ? 3 : 4; }
            case BRAZO_IZQ -> { u0 = 32; v0 = 48; w = fino ? 3 : 4; }
            case PIERNA_DER -> { u0 = 0; v0 = 16; w = 4; }
            default -> { }   // PIERNA_IZQ: los valores iniciales
        }
        return switch (cara) {
            case Herida.DERECHA -> new int[]{u0, v0 + d, d, h};
            case Herida.IZQUIERDA -> new int[]{u0 + d + w, v0 + d, d, h};
            case Herida.ESPALDA -> new int[]{u0 + 2 * d + w, v0 + d, w, h};
            default -> new int[]{u0 + d, v0 + d, w, h};
        };
    }

    // ------------------------------------------------------------------ lienzos por jugador

    public static ResourceLocation de(int id, HeridasDatos d, boolean fino) {
        cargarAtlas();
        Lienzo l = LIENZOS.get(id);
        if (l == null) {
            l = new Lienzo();
            l.tex = new DynamicTexture(64, 64, true);
            l.rl = Minecraft.getInstance().getTextureManager().register("miscelanea_heridas", l.tex);
            LIENZOS.put(id, l);
        }
        int f = firma(d) * 2 + (fino ? 1 : 0);
        if (f != l.firma || l.fino != fino) {
            l.firma = f;
            l.fino = fino;
            pintar(l.tex, d, fino);
        }
        return l.rl;
    }

    private static int firma(HeridasDatos d) {
        int h = 17;
        for (Herida w : d.lista) {
            h = h * 31 + w.semilla;
            h = h * 31 + (w.gravedad | (w.tipo.ordinal() << 2) | (w.cara << 4) | (w.especial ? 64 : 0));
            h = h * 31 + (int) (w.progreso() * 40);       // ~40 pasos de color a lo largo de la curación
        }
        for (int v : d.vendas) h = h * 31 + (v > 0 ? 1 + v / 400 : 0);
        return h;
    }

    public static void soltar(int id) {
        Lienzo l = LIENZOS.remove(id);
        if (l != null) Minecraft.getInstance().getTextureManager().release(l.rl);
    }

    public static void soltarTodo() {
        for (Iterator<Map.Entry<Integer, Lienzo>> it = LIENZOS.entrySet().iterator(); it.hasNext(); ) {
            Minecraft.getInstance().getTextureManager().release(it.next().getValue().rl);
            it.remove();
        }
    }

    public static Iterable<Integer> ids() {
        return new java.util.ArrayList<>(LIENZOS.keySet());
    }

    // ------------------------------------------------------------------ pintar

    private static void pintar(DynamicTexture tex, HeridasDatos d, boolean fino) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        img.fillRect(0, 0, 64, 64, 0);
        if (roles != null && roles.length > 0) {
            for (Herida h : d.lista) estampar(img, h, fino);
        }
        for (Parte p : Parte.V) if (d.vendada(p)) venda(img, p, fino, d.vendas[p.ordinal()]);
        tex.upload();
    }

    private static void estampar(NativeImage img, Herida h, boolean fino) {
        int[] c = cara(h.parte, h.cara, fino);
        int fw = c[2], fh = c[3];
        int fila = h.tipo.ordinal() * 3 + (h.gravedad - 1);
        int s = h.semilla & 0x7FFFFFFF;
        int col = h.especial ? 7 : s % 7;
        // si la calcomanía no cabe a lo ancho (brazos finos), se busca otra variante que sí quepa
        if (!h.especial && celda(fila, col)[0] > fw) {
            for (int k = 1; k < 7; k++) {
                int alt = (col + k) % 7;
                if (celda(fila, alt)[0] <= fw) {
                    col = alt;
                    break;
                }
            }
        }
        int[] t = celda(fila, col);
        int sw = t[0], sh = t[1];
        if (sw == 0) return;
        boolean espejo = ((s >> 5) & 1) == 1 && !(h.especial && h.tipo == Tipo.MORATON);
        int cx = Math.round(h.fx / 255F * (fw - 1)), cy = Math.round(h.fy / 255F * (fh - 1));
        int x0 = sw >= fw ? (fw - sw) / 2 : Math.max(0, Math.min(fw - sw, cx - sw / 2));
        int y0 = sh >= fh ? (fh - sh) / 2 : Math.max(0, Math.min(fh - sh, cy - sh / 2));
        float p = h.progreso();
        float brillo = 0.94F + ((s >> 9) & 15) / 15F * 0.12F;      // cada herida un pelín distinta
        int ax = col * CELDA, ay = fila * CELDA;
        for (int y = 0; y < sh; y++) {
            int py = y0 + y;
            if (py < 0 || py >= fh) continue;
            for (int x = 0; x < sw; x++) {
                int px = x0 + x;
                if (px < 0 || px >= fw) continue;
                int sx = espejo ? sw - 1 - x : x;
                int rol = roles[(ay + y) * atlasW + ax + sx];
                if (rol == 0) continue;
                int argb = color(h.tipo, rol, p, brillo);
                if (argb == 0) continue;
                int u = c[0] + px, v = c[1] + py;
                // si ya había otra herida, gana la más opaca (las recientes tapan a las que se están curando)
                int previo = img.getPixelRGBA(u, v);
                if (((previo >>> 24) & 255) > ((argb >>> 24) & 255) + 30) continue;
                img.setPixelRGBA(u, v, argb);
            }
        }
    }

    private static int[] celda(int fila, int col) {
        int i = fila * COLS + col;
        return i >= 0 && i < tam.length ? tam[i] : new int[]{0, 0};
    }

    /** Venda de gasa alrededor de la pieza (las cuatro caras), con una mancha de sangre que va secándose. */
    private static void venda(NativeImage img, Parte parte, boolean fino, int quedan) {
        int y0 = 6, alto = 3;
        switch (parte) {
            case CABEZA -> { y0 = 1; alto = 2; }        // cinta en la frente, a lo Rambo
            case TORSO -> { y0 = 5; alto = 4; }
            case PIERNA_DER, PIERNA_IZQ -> { y0 = 4; alto = 3; }   // la rodilla
            default -> { }                              // el antebrazo (valores iniciales)
        }
        int claro = abgr(244, 240, 228, 255), base = abgr(232, 226, 210, 255), trama = abgr(212, 204, 186, 255), sombra = abgr(196, 188, 170, 255);
        int[] caras = {Herida.DERECHA, Herida.FRENTE, Herida.IZQUIERDA, Herida.ESPALDA};
        int vuelta = 0;      // la trama diagonal sigue alrededor de la pieza sin cortes
        for (int k : caras) {
            int[] c = cara(parte, k, fino);
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < c[2]; x++) {
                    int col;
                    if (y == 0) col = claro;
                    else if (y == alto - 1) col = sombra;
                    else col = ((vuelta + x + y) % 3 == 0) ? trama : base;
                    img.setPixelRGBA(c[0] + x, c[1] + y0 + y, col);
                }
            }
            vuelta += c[2];
            // nudo con dos puntas colgando en la nuca
            if (parte == Parte.CABEZA && k == Herida.ESPALDA) {
                int mx = c[0] + c[2] / 2;
                img.setPixelRGBA(mx - 1, c[1] + y0 + alto, sombra);
                img.setPixelRGBA(mx, c[1] + y0 + alto, base);
                img.setPixelRGBA(mx, c[1] + y0 + alto + 1, sombra);
            }
        }
        // sangre que traspasa la gasa: roja al principio, marrón cuando se seca
        float seca = 1F - Math.max(0, Math.min(1, quedan / (float) HeridasDatos.DURACION_VENDA));
        int sangre = abgr(176 - 60 * seca, 34 + 20 * seca, 38 + 4 * seca, 255);
        int halo = abgr(214 - 30 * seca, 132 - 10 * seca, 120 - 20 * seca, 255);
        int[] f = cara(parte, Herida.FRENTE, fino);
        int mx = f[0] + f[2] / 2 - (f[2] > 3 ? 1 : 0), my = f[1] + y0 + (alto > 2 ? 1 : 0);
        img.setPixelRGBA(mx, my, sangre);
        if (f[2] > 3) img.setPixelRGBA(mx + 1, my, halo);
        if (alto > 2) img.setPixelRGBA(mx, my + 1, halo);
    }
}
