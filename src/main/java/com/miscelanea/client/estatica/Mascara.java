package com.miscelanea.client.estatica;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué trozos del cuerpo son estática y su textura animada (64x64, la distribución de la piel del jugador). La disposición
 * sale de la semilla de cada transformación (= scratchpad/estatica_comun.py): media cara siempre (el lado cambia), y un
 * trozo CONECTADO que baja desde el cuello (brazo, costado y pierna, o el pecho hasta el otro brazo). Cada texel tiene un
 * "orden" (0..1) que dice cuándo aparece al extenderse durante la transformación.
 */
public final class Mascara {
    // ------------------------------------------------------------------ ruido (= estatica_comun.py)
    public static float hash(int ix, int iy, int iz, int seed) {
        int h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 0x9E3779B1);
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216F;
    }

    public static float ruido(float x, float y, float z, int seed) {
        int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
        float fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float c000 = hash(ix, iy, iz, seed), c100 = hash(ix + 1, iy, iz, seed), c010 = hash(ix, iy + 1, iz, seed), c110 = hash(ix + 1, iy + 1, iz, seed);
        float c001 = hash(ix, iy, iz + 1, seed), c101 = hash(ix + 1, iy, iz + 1, seed), c011 = hash(ix, iy + 1, iz + 1, seed), c111 = hash(ix + 1, iy + 1, iz + 1, seed);
        float x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx, x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        float y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    // ------------------------------------------------------------------ partes de la piel: u, v, w, h, d, x0, y0, z0, capa exterior, cabeza
    private static final int[][] PARTES = {
            {0, 0, 8, 8, 8, -4, 24, -4, 0, 1}, {32, 0, 8, 8, 8, -4, 24, -4, 1, 1},
            {16, 16, 8, 12, 4, -4, 12, -2, 0, 0}, {16, 32, 8, 12, 4, -4, 12, -2, 1, 0},
            {40, 16, 4, 12, 4, 4, 12, -2, 0, 0}, {40, 32, 4, 12, 4, 4, 12, -2, 1, 0},
            {32, 48, 4, 12, 4, -8, 12, -2, 0, 0}, {48, 48, 4, 12, 4, -8, 12, -2, 1, 0},
            {0, 16, 4, 12, 4, 0, 0, -2, 0, 0}, {0, 32, 4, 12, 4, 0, 0, -2, 1, 0},
            {16, 48, 4, 12, 4, -4, 0, -2, 0, 0}, {0, 48, 4, 12, 4, -4, 0, -2, 1, 0},
    };

    private static final float[][][] CADENAS = {
            {{2, 24, 0}, {5, 23.5F, 0}, {6, 18, 0}, {6, 12.5F, 0}},
            {{2, 24, 0}, {3, 19, -0.5F}, {2.2F, 12, 0}, {2, 6, 0}, {2, 0.5F, 0}},
            {{2, 24, 0}, {0.5F, 20, -2}, {-3, 22, -1}, {-5.5F, 23, 0}, {-6, 17, 0}},
    };
    private static final float[] RADIOS = {3.2F, 2.7F, 2.6F};

    /** Lado de la cara (+1 derecha del personaje, -1 izquierda), cadena (0..2) y alcance (0..1). */
    public static int lado(int seed) {
        return hash(seed, 1, 0, 77) < 0.5F ? 1 : -1;
    }

    public static int cadena(int seed) {
        return Math.min(2, (int) (hash(seed, 2, 0, 77) * 3));
    }

    public static float alcance(int seed) {
        return 0.65F + 0.35F * hash(seed, 3, 0, 77);
    }

    /** Texel → {x, y, z, capa, cabeza} del cuerpo, o null. */
    static float[] punto(int pu, int pv) {
        for (int[] p : PARTES) {
            float a = pu + 0.5F - p[0], b = pv + 0.5F - p[1];
            int w = p[2], h = p[3], d = p[4];
            if (a < 0 || b < 0 || a >= 2 * d + 2 * w || b >= d + h) continue;
            float x0 = p[5], y0 = p[6], z0 = p[7], x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
            if (b < d) {
                if (a >= d && a < d + w) return new float[]{x1 - (a - d), y1, z1 - b, p[8], p[9]};
                if (a >= d + w && a < d + 2 * w) return new float[]{x1 - (a - d - w), y0, z1 - b, p[8], p[9]};
                continue;
            }
            float y = y1 - (b - d);
            if (a < d) return new float[]{x1, y, z1 - a, p[8], p[9]};
            if (a < d + w) return new float[]{x1 - (a - d), y, z0, p[8], p[9]};
            if (a < 2 * d + w) return new float[]{x0, y, z0 + (a - d - w), p[8], p[9]};
            return new float[]{x0 + (a - 2 * d - w), y, z1, p[8], p[9]};
        }
        return null;
    }

    /** Orden (0..1) de un punto del cuerpo, o -1 si no lleva estática (= estatica_comun.orden). */
    static float orden(int seed, boolean cabeza, float x, float y, float z) {
        int lado = lado(seed);
        if (cabeza) {
            float borde = -0.6F + 1.6F * (ruido(y * 0.35F, z * 0.35F, 1.3F, seed) - 0.5F);
            if (x * lado <= borde) return -1;
            float dx = x - lado * 2, dy = y - 28.5F, dz = z + 4;
            return 0.25F * Math.min(1F, Mth.sqrt(dx * dx + dy * dy + dz * dz) / 9F);
        }
        float[][] pts = CADENAS[cadena(seed)];
        float r = RADIOS[cadena(seed)], alc = alcance(seed);
        float total = 0;
        float[] largos = new float[pts.length - 1];
        for (int i = 0; i + 1 < pts.length; i++) {
            float ax = pts[i][0] * lado, bx = pts[i + 1][0] * lado;
            float dx = bx - ax, dy = pts[i + 1][1] - pts[i][1], dz = pts[i + 1][2] - pts[i][2];
            largos[i] = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            total += largos[i];
        }
        float mejor = 1e9F, tm = 0, acum = 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            float ax = pts[i][0] * lado, ay = pts[i][1], az = pts[i][2];
            float bx = pts[i + 1][0] * lado, by = pts[i + 1][1], bz = pts[i + 1][2];
            float abx = bx - ax, aby = by - ay, abz = bz - az, L = largos[i];
            float k = Mth.clamp(((x - ax) * abx + (y - ay) * aby + (z - az) * abz) / (L * L), 0F, 1F);
            float qx = ax + abx * k - x, qy = ay + aby * k - y, qz = az + abz * k - z;
            float dd = Mth.sqrt(qx * qx + qy * qy + qz * qz);
            if (dd < mejor) {
                mejor = dd;
                tm = (acum + k * L) / total;
            }
            acum += L;
        }
        float rr = r * (0.7F + 0.6F * ruido(x * 0.3F, y * 0.3F, z * 0.3F, seed + 5));
        float corte = alc + 0.08F * (ruido(x * 0.5F, y * 0.5F, z * 0.5F, seed + 9) - 0.5F);
        if (mejor >= rr || tm > corte) return -1;
        return 0.25F + 0.75F * Math.min(1F, tm / alc);
    }

    // ------------------------------------------------------------------ textura viva
    public final int semilla;
    public final DynamicTexture tex;
    public final ResourceLocation rl;
    private final float[] orden = new float[64 * 64];
    private final boolean[] capa = new boolean[64 * 64];
    /** Puntos del cuerpo (x, y, z en píxeles del cuerpo, con su orden) de donde caen las gotas. */
    final List<float[]> puntos = new ArrayList<>();
    private long cuadro = Long.MIN_VALUE;
    private float cobPintada = -1;
    long usada;
    private int azar;

    public Mascara(int id, int semilla) {
        this.semilla = semilla;
        this.azar = semilla ^ 0x5DEECE6;
        for (int v = 0; v < 64; v++)
            for (int u = 0; u < 64; u++) {
                int i = v * 64 + u;
                float[] p = punto(u, v);
                orden[i] = p == null ? -1 : orden(semilla, p[4] > 0, p[0], p[1], p[2]);
                capa[i] = p != null && p[3] > 0;
                if (p != null && orden[i] >= 0 && !capa[i] && ((u + v) & 1) == 0) puntos.add(new float[]{p[0], p[1], p[2], orden[i]});
            }
        tex = new DynamicTexture(64, 64, true);
        rl = Minecraft.getInstance().getTextureManager().register("miscelanea_estatica_" + (id & 0x7FFFFFFF), tex);
    }

    private int azar() {
        azar ^= azar << 13;
        azar ^= azar >>> 17;
        azar ^= azar << 5;
        return azar;
    }

    /** Repinta (unas 15 veces por segundo) la nieve hasta la cobertura dada (0..1). */
    public void actualizar(float cobertura, long ms) {
        long c = ms / 66;
        if (c == cuadro && Math.abs(cobertura - cobPintada) < 0.01F) return;
        cuadro = c;
        cobPintada = cobertura;
        NativeImage im = tex.getPixels();
        if (im == null) return;
        int banda = (int) ((c * 3) % 23);
        for (int v = 0; v < 64; v++)
            for (int u = 0; u < 64; u++) {
                int i = v * 64 + u;
                float o = orden[i];
                if (o < 0 || o > cobertura) {
                    im.setPixelRGBA(u, v, 0);
                    continue;
                }
                int r = azar();
                int g = r & 0xFF;
                g = g < 46 ? g / 3 : 90 + (g * 165 >> 8);
                if (Math.floorMod(v - banda, 23) < 2) g = Math.min(255, g + 70);
                int a = capa[i] ? 150 : 235;
                // el borde que avanza parpadea y se deshace
                if (cobertura - o < 0.05F && ((r >>> 9) & 3) != 0) a = (r >>> 12) & 0x7F;
                else if (((r >>> 16) & 31) == 0) a = 60;                   // algún hueco suelto
                int b = Math.min(255, g + 8);
                // NativeImage: ABGR
                im.setPixelRGBA(u, v, (a << 24) | (b << 16) | (g << 8) | g);
            }
        tex.upload();
    }

    public void cerrar() {
        Minecraft.getInstance().getTextureManager().release(rl);
    }
}
