package com.miscelanea.client.tortura.castigo;

import com.miscelanea.tortura.castigo.Mancha;
import com.miscelanea.tortura.heridas.Herida;
import com.miscelanea.tortura.heridas.Parte;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pinta las manchas de cada jugador en una textura con la MISMA distribución de UV que una skin pero a 4x (256x256): la
 * capa usa un modelo de jugador (UV normalizadas), así que cada píxel de la skin lleva 4x4 de mancha y el tomate
 * reventado se ve con su pulpa, sus pepitas, las gotas que salpican y los chorretones hacia abajo; el huevo, con la yema,
 * la clara brillante y trocitos de cáscara. Todo procedural a partir de la semilla de cada mancha; se seca y se desvanece.
 */
public final class ManchasTextura {
    private ManchasTextura() {}

    static final int K = 4, LADO = 64 * K;

    private static final class Lienzo {
        DynamicTexture tex;
        ResourceLocation rl;
        int firma = Integer.MIN_VALUE;
    }

    private static final Map<Integer, Lienzo> LIENZOS = new HashMap<>();

    /** {u, v, ancho, alto} de la cara de la pieza en la skin (64x64, capa base). Igual que HeridasTextura.cara. */
    static int[] cara(Parte parte, int cara, boolean fino) {
        int u0 = 16, v0 = 48, w = 4, h = 12, d = 4;
        switch (parte) {
            case CABEZA -> { u0 = 0; v0 = 0; w = 8; h = 8; d = 8; }
            case TORSO -> { u0 = 16; v0 = 16; w = 8; }
            case BRAZO_DER -> { u0 = 40; v0 = 16; w = fino ? 3 : 4; }
            case BRAZO_IZQ -> { u0 = 32; v0 = 48; w = fino ? 3 : 4; }
            case PIERNA_DER -> { u0 = 0; v0 = 16; w = 4; }
            default -> { }
        }
        return switch (cara) {
            case Herida.DERECHA -> new int[]{u0, v0 + d, d, h};
            case Herida.IZQUIERDA -> new int[]{u0 + d + w, v0 + d, d, h};
            case Herida.ESPALDA -> new int[]{u0 + 2 * d + w, v0 + d, w, h};
            default -> new int[]{u0 + d, v0 + d, w, h};
        };
    }

    public static ResourceLocation de(int id, List<Mancha> l, boolean fino) {
        Lienzo c = LIENZOS.get(id);
        if (c == null) {
            c = new Lienzo();
            c.tex = new DynamicTexture(LADO, LADO, true);
            c.rl = Minecraft.getInstance().getTextureManager().register("miscelanea_manchas", c.tex);
            LIENZOS.put(id, c);
        }
        int f = firma(l) * 2 + (fino ? 1 : 0);
        if (f != c.firma) {
            c.firma = f;
            pintar(c.tex, l, fino);
        }
        return c.rl;
    }

    private static int firma(List<Mancha> l) {
        int h = 23;
        for (Mancha m : l) {
            h = h * 31 + m.semilla;
            h = h * 31 + (m.parte.ordinal() | (m.cara << 3) | (m.tipo << 5));
            h = h * 31 + (int) (m.progreso() * 24);
        }
        return h;
    }

    public static void soltar(int id) {
        Lienzo c = LIENZOS.remove(id);
        if (c != null) Minecraft.getInstance().getTextureManager().release(c.rl);
    }

    public static void soltarTodo() {
        for (Iterator<Map.Entry<Integer, Lienzo>> it = LIENZOS.entrySet().iterator(); it.hasNext(); ) {
            Minecraft.getInstance().getTextureManager().release(it.next().getValue().rl);
            it.remove();
        }
    }

    public static Iterable<Integer> ids() {
        return new ArrayList<>(LIENZOS.keySet());
    }

    // ------------------------------------------------------------------ pintar
    private static void pintar(DynamicTexture tex, List<Mancha> l, boolean fino) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        img.fillRect(0, 0, LADO, LADO, 0);
        for (Mancha m : l) {
            int[] c = cara(m.parte, m.cara, fino);
            new Pincel(img, c[0] * K, c[1] * K, c[2] * K, c[3] * K, m).pintar();
        }
        tex.upload();
    }

    /** Pinta una mancha recortada a su cara. */
    private static final class Pincel {
        final NativeImage img;
        final int x0, y0, w, h;
        final Mancha m;
        final Random r;
        final float p, alfa;

        Pincel(NativeImage img, int x0, int y0, int w, int h, Mancha m) {
            this.img = img;
            this.x0 = x0;
            this.y0 = y0;
            this.w = w;
            this.h = h;
            this.m = m;
            this.r = new Random(m.semilla * 2654435761L);
            this.p = m.progreso();
            this.alfa = p < 0.7F ? 1F : Math.max(0F, 1F - (p - 0.7F) / 0.3F);
        }

        void pintar() {
            float cx = m.fx / 255F * (w - 1), cy = m.fy / 255F * (h - 1);
            if (m.tipo == Mancha.HUEVO) huevo(cx, cy);
            else tomate(cx, cy);
        }

        // ---- tomate: pulpa roja irregular, pepitas, trocitos de piel, brillo, gotas y chorretones
        void tomate(float cx, float cy) {
            float R = K * (1.5F + r.nextFloat() * 0.9F);
            float s1 = r.nextFloat() * 6.3F, s2 = r.nextFloat() * 6.3F, s3 = r.nextFloat() * 6.3F;
            float seco = Math.min(1F, p * 1.3F);
            int rr = (int) Math.ceil(R * 1.6F);
            for (int y = (int) (cy - rr); y <= cy + rr; y++)
                for (int x = (int) (cx - rr); x <= cx + rr; x++) {
                    float dx = x - cx, dy = y - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = (float) Math.atan2(dy, dx);
                    float borde = R * (1F + 0.22F * (float) Math.sin(3 * a + s1) + 0.14F * (float) Math.sin(5 * a + s2) + 0.08F * (float) Math.sin(9 * a + s3));
                    if (d > borde) continue;
                    float t = d / borde;
                    float[] c = t < 0.42F ? new float[]{212, 62, 38} : t > 0.84F ? new float[]{128, 14, 12} : new float[]{176, 26, 18};
                    if (t >= 0.42F && t < 0.55F) c = mezcla(new float[]{212, 62, 38}, new float[]{176, 26, 18}, (t - 0.42F) / 0.13F);
                    int hsh = hash(x, y);
                    if (t > 0.18F && t < 0.82F && (hsh & 63) < 4) c = new float[]{228, 198, 120};          // pepitas
                    else if ((hsh & 127) < 4) c = new float[]{150, 12, 10};                                // piel
                    else if (((hsh >> 7) & 15) == 0) c = mul(c, 0.86F);                                    // grumos
                    c = mezcla(c, new float[]{104, 34, 22}, seco * 0.55F);                                 // se seca y oscurece
                    poner(x, y, c, 236 * (t > 0.92F ? 0.8F : 1F));
                    // brillo húmedo
                    float bx = dx + R * 0.32F, by = dy + R * 0.34F;
                    if (bx * bx + by * by < R * R * 0.035F && seco < 0.6F) poner(x, y, new float[]{255, 214, 200}, 210 * (1 - seco));
                }
            int gotas = 4 + r.nextInt(4);
            for (int i = 0; i < gotas; i++) {
                float a = r.nextFloat() * 6.3F, dist = R * (1.15F + r.nextFloat() * 0.8F), rad = Math.max(0.8F, R * (0.1F + r.nextFloat() * 0.18F));
                gota(cx + (float) Math.cos(a) * dist, cy + (float) Math.sin(a) * dist, rad, mezcla(new float[]{170, 24, 16}, new float[]{104, 34, 22}, seco * 0.55F), 230);
            }
            int chorros = 1 + r.nextInt(3);
            for (int i = 0; i < chorros; i++) {
                float xs = cx + (r.nextFloat() - 0.5F) * R * 1.2F;
                float largo = R * (0.9F + r.nextFloat() * 1.6F) * (0.6F + 0.4F * Math.min(1F, p * 3 + 0.3F));
                chorro(xs, cy + R * 0.55F, largo, K * (0.5F + r.nextFloat() * 0.5F), mezcla(new float[]{160, 20, 14}, new float[]{104, 34, 22}, seco * 0.55F), 226);
            }
        }

        // ---- huevo: yema, clara translúcida y brillante, cáscara y chorretones de clara
        void huevo(float cx, float cy) {
            float R = K * (1.6F + r.nextFloat() * 0.8F);
            float s1 = r.nextFloat() * 6.3F, s2 = r.nextFloat() * 6.3F;
            float seco = Math.min(1F, p * 1.3F);
            int rr = (int) Math.ceil(R * 1.7F);
            float yx = cx + (r.nextFloat() - 0.5F) * R * 0.4F, yy = cy + (r.nextFloat() - 0.5F) * R * 0.3F;
            float ry = R * (0.42F + r.nextFloat() * 0.12F);
            for (int y = (int) (cy - rr); y <= cy + rr; y++)
                for (int x = (int) (cx - rr); x <= cx + rr; x++) {
                    float dx = x - cx, dy = y - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = (float) Math.atan2(dy, dx);
                    float borde = R * 1.25F * (1F + 0.2F * (float) Math.sin(4 * a + s1) + 0.12F * (float) Math.sin(7 * a + s2));
                    if (d > borde) continue;
                    float t = d / borde;
                    // clara: casi transparente por dentro, con el borde más marcado y brillante
                    float[] c = t > 0.86F ? new float[]{226, 226, 200} : new float[]{238, 236, 214};
                    float al = (t > 0.86F ? 175 : 120) * (1F - seco * 0.45F);
                    poner(x, y, mezcla(c, new float[]{206, 196, 150}, seco * 0.6F), al);
                    // yema
                    float ex = x - yx, ey = y - yy, de = (float) Math.sqrt(ex * ex + ey * ey);
                    if (de < ry) {
                        float tt = de / ry;
                        float[] y0 = tt < 0.5F ? new float[]{250, 196, 46} : tt > 0.85F ? new float[]{214, 138, 18} : new float[]{242, 172, 30};
                        y0 = mezcla(y0, new float[]{196, 150, 40}, seco * 0.5F);
                        poner(x, y, y0, 245);
                        if ((ex + ry * 0.3F) * (ex + ry * 0.3F) + (ey + ry * 0.35F) * (ey + ry * 0.35F) < ry * ry * 0.06F && seco < 0.6F)
                            poner(x, y, new float[]{255, 248, 214}, 220);
                    }
                    // brillo de la clara
                    if (t > 0.6F && t < 0.75F && Math.sin(a * 2 + s1) > 0.85 && seco < 0.5F) poner(x, y, new float[]{255, 255, 250}, 150);
                }
            // trocitos de cáscara
            int trozos = 2 + r.nextInt(4);
            for (int i = 0; i < trozos; i++) {
                float a = r.nextFloat() * 6.3F, dist = R * (0.5F + r.nextFloat() * 0.9F);
                int tx = Math.round(cx + (float) Math.cos(a) * dist), ty = Math.round(cy + (float) Math.sin(a) * dist);
                int tw = 2 + r.nextInt(3), th = 2 + r.nextInt(2);
                for (int y = 0; y < th; y++)
                    for (int x = 0; x < tw; x++) {
                        if ((x == tw - 1 && y == 0) || (hash(tx + x, ty + y) & 7) == 0) continue;
                        boolean b = y == th - 1 || x == 0;
                        poner(tx + x, ty + y, b ? new float[]{168, 156, 132} : new float[]{236, 226, 202}, 250);
                    }
            }
            int chorros = 1 + r.nextInt(2);
            for (int i = 0; i < chorros; i++) {
                float xs = cx + (r.nextFloat() - 0.5F) * R;
                chorro(xs, cy + R * 0.7F, R * (0.8F + r.nextFloat() * 1.4F), K * 0.6F, new float[]{232, 230, 206}, 150 * (1F - seco * 0.4F));
            }
        }

        void gota(float gx, float gy, float rad, float[] c, float al) {
            for (int y = (int) (gy - rad - 1); y <= gy + rad + 1; y++)
                for (int x = (int) (gx - rad - 1); x <= gx + rad + 1; x++) {
                    float dx = x - gx, dy = y - gy;
                    if (dx * dx + dy * dy <= rad * rad) poner(x, y, c, al);
                }
        }

        /** Chorretón que baja (v creciente) con un poco de ondulación y una gota gorda al final. */
        void chorro(float xs, float ys, float largo, float ancho, float[] c, float al) {
            float fase = r.nextFloat() * 6.3F;
            int n = Math.max(2, Math.round(largo));
            for (int i = 0; i < n; i++) {
                float y = ys + i;
                float x = xs + (float) Math.sin(i * 0.35F + fase) * 0.8F;
                float a = ancho * (1F - 0.35F * i / n);
                for (int k = (int) Math.floor(x - a / 2); k <= x + a / 2; k++) poner(k, Math.round(y), c, al);
            }
            gota(xs + (float) Math.sin(n * 0.35F + fase) * 0.8F, ys + n, ancho * 0.85F, c, al);
        }

        void poner(int x, int y, float[] c, float al) {
            if (x < 0 || y < 0 || x >= w || y >= h) return;
            int u = x0 + x, v = y0 + y;
            float a = Math.max(0F, Math.min(255F, al * alfa)) / 255F;
            if (a < 0.03F) return;
            int previo = img.getPixelRGBA(u, v);                    // ABGR
            float pa = ((previo >>> 24) & 255) / 255F;
            float pr = previo & 255, pg = (previo >> 8) & 255, pb = (previo >> 16) & 255;
            float oa = a + pa * (1 - a);
            float or = (c[0] * a + pr * pa * (1 - a)) / Math.max(1.0E-4F, oa);
            float og = (c[1] * a + pg * pa * (1 - a)) / Math.max(1.0E-4F, oa);
            float ob = (c[2] * a + pb * pa * (1 - a)) / Math.max(1.0E-4F, oa);
            img.setPixelRGBA(u, v, abgr(or, og, ob, oa * 255));
        }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static float[] mezcla(float[] a, float[] b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float[] mul(float[] a, float k) {
        return new float[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static int abgr(float r, float g, float b, float a) {
        int ri = Math.max(0, Math.min(255, Math.round(r))), gi = Math.max(0, Math.min(255, Math.round(g)));
        int bi = Math.max(0, Math.min(255, Math.round(b))), ai = Math.max(0, Math.min(255, Math.round(a)));
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }
}
