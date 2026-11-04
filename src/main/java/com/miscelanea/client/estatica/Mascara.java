package com.miscelanea.client.estatica;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qué trozos del cuerpo son estática y su textura animada, en el ESPACIO DE LA PIEL del jugador (su tamaño real, brazos
 * finos de Alex, y solo donde la piel tiene píxeles: así sigue a gorros, pelo y chaquetas de la capa exterior y a las
 * piezas de CPM que usen las UV de la piel). La disposición sale de la semilla de cada transformación
 * (= scratchpad/estatica_comun.py y estatica_viva.py): media cara siempre (el lado cambia) y un trozo CONECTADO que baja
 * desde el cuello. Cada texel tiene un "orden" (0..1) que dice cuándo aparece al extenderse durante la transformación.
 * <p>
 * Los ojos se buscan en la cara de la piel (dos grupos de píxeles en espejo que contrastan con el tono de la cara); si no
 * se encuentran, van donde los de Steve. Se pintan en una segunda textura, aditiva (brillan en la oscuridad).
 * <p>
 * La nieve se repinta 20 veces por segundo: grano fino y grumos, una nube que se desliza (coherencia entre fotogramas),
 * banda que rueda hacia abajo, líneas de barrido, chispazos, filas que se desplazan, borde que se deshace con fleco de
 * color, píxeles que se sueltan y gotas que escurren desde el borde de abajo.
 */
public final class Mascara {
    private static final Logger LOG = LogUtils.getLogger();

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
    /** Con brazos finos (Alex): 3 de ancho; el derecho empieza en x=4 y el izquierdo en x=-7. */
    private static final int[][] PARTES_FINO;

    static {
        PARTES_FINO = new int[PARTES.length][];
        for (int i = 0; i < PARTES.length; i++) {
            PARTES_FINO[i] = PARTES[i].clone();
            if (i == 4 || i == 5) {
                PARTES_FINO[i][2] = 3;
                PARTES_FINO[i][5] = 4;
            } else if (i == 6 || i == 7) {
                PARTES_FINO[i][2] = 3;
                PARTES_FINO[i][5] = -7;
            }
        }
    }

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

    /** Punto (bu, bv) en unidades de la piel de 64 (centro del texel) → {x, y, z, capa, cabeza, parte} del cuerpo, o null. */
    static float[] punto(float bu, float bv, boolean fino) {
        int[][] partes = fino ? PARTES_FINO : PARTES;
        for (int k = 0; k < partes.length; k++) {
            int[] p = partes[k];
            float a = bu - p[0], b = bv - p[1];
            int w = p[2], h = p[3], d = p[4];
            if (a < 0 || b < 0 || a >= 2 * d + 2 * w || b >= d + h) continue;
            float x0 = p[5], y0 = p[6], z0 = p[7], x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
            if (b < d) {
                if (a >= d && a < d + w) return new float[]{x1 - (a - d), y1, z1 - b, p[8], p[9], k};
                if (a >= d + w && a < d + 2 * w) return new float[]{x1 - (a - d - w), y0, z1 - b, p[8], p[9], k};
                continue;
            }
            float y = y1 - (b - d);
            if (a < d) return new float[]{x1, y, z1 - a, p[8], p[9], k};
            if (a < d + w) return new float[]{x1 - (a - d), y, z0, p[8], p[9], k};
            if (a < 2 * d + w) return new float[]{x0, y, z0 + (a - d - w), p[8], p[9], k};
            return new float[]{x0 + (a - 2 * d - w), y, z1, p[8], p[9], k};
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

    // ------------------------------------------------------------------ la piel leída (cacheada por textura + modelo)
    /** Posiciones de los ojos de Steve, en unidades de 64 (u, v, u, v...). */
    private static final int[] OJOS_STEVE = {9, 12, 10, 12, 13, 12, 14, 12};

    /** Lo que hace falta de una piel: tamaño (64 o 128), alfa de cada texel y dónde están los ojos. */
    static final class Piel {
        final int tam;
        final boolean fino, leida, ojosCapa;
        /** Alfa por texel (null = sin leer: la capa base cuenta como opaca y la exterior como vacía). */
        final boolean[] alfa;
        final int[] ojos;

        Piel(int tam, boolean fino, boolean leida, boolean[] alfa, int[] ojos, boolean ojosCapa) {
            this.tam = tam;
            this.fino = fino;
            this.leida = leida;
            this.alfa = alfa;
            this.ojos = ojos;
            this.ojosCapa = ojosCapa;
        }

        static Piel defecto(boolean fino) {
            return new Piel(64, fino, false, null, OJOS_STEVE, false);
        }
    }

    private static final Map<String, Piel> PIELES = new HashMap<>();
    private static final Map<String, Long> REINTENTO = new HashMap<>();

    /** La piel ya leída, o la lee ahora (hilo de dibujo); null si aún no está (se reintenta cada segundo). */
    static Piel piel(ResourceLocation rl, boolean fino) {
        String k = rl + (fino ? "#fino" : "");
        Piel p = PIELES.get(k);
        if (p != null) return p;
        long ahora = Util.getMillis();
        Long r = REINTENTO.get(k);
        if (r != null && ahora < r) return null;
        try {
            p = leer(rl, fino);
        } catch (Throwable t) {
            LOG.debug("Estática: no se pudo leer la piel {}", rl, t);
            p = null;
        }
        if (p == null) {
            REINTENTO.put(k, ahora + 1000);
            return null;
        }
        if (PIELES.size() > 256) PIELES.clear();
        PIELES.put(k, p);
        REINTENTO.remove(k);
        return p;
    }

    static void olvidarPieles() {
        PIELES.clear();
        REINTENTO.clear();
    }

    /** Baja la textura de la piel de la tarjeta gráfica y saca su alfa y sus ojos. */
    private static Piel leer(ResourceLocation rl, boolean fino) {
        if (!RenderSystem.isOnRenderThread()) return null;
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(rl, null);
        if (t == null) return null;
        RenderSystem.bindTexture(t.getId());
        int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w < 64 || w != h || w % 64 != 0 || w > 1024) return null;
        try (NativeImage im = new NativeImage(w, h, false)) {
            im.downloadTexture(0, false);
            int tam = Math.min(w, 128), paso = w / tam;
            boolean[] alfa = new boolean[tam * tam];
            int opacos = 0;
            for (int v = 0; v < tam; v++)
                for (int u = 0; u < tam; u++) {
                    int a = (im.getPixelRGBA(u * paso + paso / 2, v * paso + paso / 2) >>> 24) & 0xFF;
                    alfa[v * tam + u] = a > 0;
                    if (a > 0) opacos++;
                }
            if (opacos < 64) return null;                 // aún vacía (sin subir)
            int k = w / 64;
            int[] ojos = detectar(im, k, 40);
            boolean capa = ojos != null;
            if (ojos == null) ojos = detectar(im, k, 8);
            if (ojos == null) ojos = OJOS_STEVE;
            return new Piel(tam, fino, true, alfa, ojos, capa);
        }
    }

    /** {r, g, b, a} del texel (u, v) en unidades de 64 (el del centro, en pieles HD). */
    private static int[] muestra(NativeImage im, int k, int u, int v) {
        int c = im.getPixelRGBA(u * k + k / 2, v * k + k / 2);        // ABGR
        return new int[]{c & 0xFF, (c >>> 8) & 0xFF, (c >>> 16) & 0xFF, (c >>> 24) & 0xFF};
    }

    /**
     * Busca los ojos en la cara (u0..u0+8, 8..16): la fila (10..13) con más píxeles que contrastan con el tono de la cara
     * EN PAREJA con su espejo (columnas 1..6; los bordes suelen ser pelo). Si la fila de debajo contrasta igual, también
     * (ojos de dos filas). Devuelve posiciones de la capa base (u, v, u, v...) o null (= estatica_viva.detectar_ojos).
     */
    private static int[] detectar(NativeImage im, int k, int u0) {
        int[][] cara = new int[64][];
        for (int j = 0; j < 8; j++)
            for (int i = 0; i < 8; i++) cara[j * 8 + i] = muestra(im, k, u0 + i, 8 + j);
        if (u0 >= 32) {
            int op = 0;
            for (int j = 2; j < 6; j++)
                for (int i = 0; i < 8; i++) if (cara[j * 8 + i][3] > 128) op++;
            if (op < 24) return null;                     // la capa de fuera no tapa la cara: los ojos están debajo
        }
        float[] ref = new float[3];
        int n = 0;
        int[][] canal = new int[3][40];
        for (int j = 3; j < 8; j++)
            for (int i = 0; i < 8; i++) {
                int[] c = cara[j * 8 + i];
                if (c[3] <= 128) continue;
                for (int q = 0; q < 3; q++) canal[q][n] = c[q];
                n++;
            }
        for (int q = 0; q < 3; q++) {
            if (n == 0) break;
            int[] s = Arrays.copyOf(canal[q], n);
            Arrays.sort(s);
            ref[q] = (n & 1) == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2F;
        }
        float refMedia = (ref[0] + ref[1] + ref[2]) / 3F;
        boolean[] con = new boolean[64];
        for (int idx = 0; idx < 64; idx++) {
            int[] c = cara[idx];
            if (c[3] <= 128) continue;
            float dif = Math.max(Math.abs(c[0] - ref[0]), Math.max(Math.abs(c[1] - ref[1]), Math.abs(c[2] - ref[2])));
            con[idx] = dif > 55 || Math.abs((c[0] + c[1] + c[2]) / 3F - refMedia) > 40;
        }
        float mejor = -1;
        int fila = -1;
        boolean[] cols = null;
        for (int j = 2; j < 6; j++) {
            boolean[] izq = new boolean[8];
            int sim = 0;
            for (int i = 1; i < 4; i++)
                if (con[j * 8 + i] && con[j * 8 + 7 - i]) {
                    izq[i] = izq[7 - i] = true;
                    sim++;
                }
            if (sim == 0) continue;
            boolean toda = true;
            for (int i = 0; i < 8; i++) toda &= con[j * 8 + i];
            if (sim > 2 && toda) continue;
            float nota = sim * 10 - Math.abs(j - 4) * 3;
            if (nota > mejor) {
                mejor = nota;
                fila = j;
                cols = izq;
            }
        }
        if (cols == null) return null;
        List<Integer> pts = new ArrayList<>();
        int nc = 0, abajo = 0;
        for (int i = 0; i < 8; i++)
            if (cols[i]) {
                pts.add(8 + i);
                pts.add(8 + fila);
                nc++;
                if (fila + 1 < 6 && con[(fila + 1) * 8 + i]) abajo++;
            }
        if (fila + 1 < 6 && abajo >= Math.max(2, nc - 1))
            for (int i = 0; i < 8; i++)
                if (cols[i] && con[(fila + 1) * 8 + i]) {
                    pts.add(8 + i);
                    pts.add(9 + fila);
                }
        int[] r = new int[pts.size()];
        for (int i = 0; i < r.length; i++) r[i] = pts.get(i);
        return r;
    }

    // ------------------------------------------------------------------ textura viva
    public final int semilla;
    private final int id;
    DynamicTexture tex, texOjos;
    public ResourceLocation rl, rlOjos;
    /** Puntos del cuerpo (x, y, z en píxeles del cuerpo, con su orden) de donde caen las gotas. */
    final List<float[]> puntos = new ArrayList<>();
    private Piel piel;
    private String pielClave;
    private int tam, k;
    private float[] orden, px, py, pz;
    private boolean[] valido, capa, alfa, ojo, on, onb;
    private int[] abajo, din, dout, dx, camino;
    private long cuadro = Long.MIN_VALUE;
    private float cobPintada = -1;
    long usada;

    public Mascara(int id, int semilla) {
        this.id = id;
        this.semilla = semilla;
        disponer(Piel.defecto(false));
    }

    /** Se ajusta a la piel y al modelo del jugador (cuando la piel ya está cargada; mientras, la de Steve/Alex). */
    public void adaptar(AbstractClientPlayer p) {
        ResourceLocation r = p.getSkinTextureLocation();
        boolean fino = "slim".equals(p.getModelName());
        String clave = r + (fino ? "#fino" : "");
        if (clave.equals(pielClave)) return;
        Piel pl = piel(r, fino);
        if (pl == null) {
            if (piel.fino != fino || piel.leida) disponer(Piel.defecto(fino));
            return;
        }
        pielClave = clave;
        disponer(pl);
    }

    private void disponer(Piel pl) {
        this.piel = pl;
        int S = pl.tam;
        if (tex == null || S != tam) {
            if (rl != null) cerrar();
            tex = new DynamicTexture(S, S, true);
            texOjos = new DynamicTexture(S, S, true);
            var tm = Minecraft.getInstance().getTextureManager();
            rl = tm.register("miscelanea_estatica_" + (id & 0x7FFFFFFF), tex);
            rlOjos = tm.register("miscelanea_estatica_ojos_" + (id & 0x7FFFFFFF), texOjos);
        }
        tam = S;
        k = S / 64;
        int n = S * S;
        orden = new float[n];
        px = new float[n];
        py = new float[n];
        pz = new float[n];
        valido = new boolean[n];
        capa = new boolean[n];
        alfa = new boolean[n];
        ojo = new boolean[n];
        on = new boolean[n];
        onb = new boolean[n];
        abajo = new int[n];
        din = new int[n];
        dout = new int[n];
        dx = new int[S];
        camino = new int[16 * k + 1];
        int[] parte = new int[n];
        puntos.clear();
        for (int v = 0; v < S; v++)
            for (int u = 0; u < S; u++) {
                int i = v * S + u;
                float[] p = punto((u + 0.5F) / k, (v + 0.5F) / k, pl.fino);
                orden[i] = -1;
                parte[i] = -1;
                if (p == null) continue;
                valido[i] = true;
                capa[i] = p[3] > 0;
                parte[i] = (int) p[5];
                px[i] = p[0];
                py[i] = p[1];
                pz[i] = p[2];
                orden[i] = orden(semilla, p[4] > 0, p[0], p[1], p[2]);
                alfa[i] = pl.alfa == null ? !capa[i] : pl.alfa[i];
                if (orden[i] >= 0 && !capa[i] && alfa[i] && ((u / k + v / k) & 1) == 0 && u % k == 0 && v % k == 0)
                    puntos.add(new float[]{p[0], p[1], p[2], orden[i]});
            }
        Arrays.fill(abajo, -1);
        for (int v = 0; v + 1 < S; v++)
            for (int u = 0; u < S; u++) {
                int i = v * S + u, j = i + S;
                if (!valido[i] || !valido[j] || parte[i] != parte[j]) continue;
                if (Math.abs(px[i] - px[j]) < 1e-3F && Math.abs(pz[i] - pz[j]) < 1e-3F && py[j] < py[i]) abajo[i] = j;
            }
        int[] oj = pl.ojos;
        for (int q = 0; q + 1 < oj.length; q += 2)
            for (int a = 0; a < k; a++)
                for (int b = 0; b < k; b++) {
                    ojo[(oj[q + 1] * k + b) * S + oj[q] * k + a] = true;
                    if (pl.ojosCapa) ojo[(oj[q + 1] * k + b) * S + (oj[q] + 32) * k + a] = true;
                }
        pintarOjos();
        cuadro = Long.MIN_VALUE;
        cobPintada = -1;
    }

    /** Ojos (aditivos): blancos, con un halo tenue alrededor y un resplandor en la capa de fuera (= textura_ojos). */
    private void pintarOjos() {
        NativeImage im = texOjos.getPixels();
        if (im == null) return;
        int S = tam;
        im.fillRect(0, 0, S, S, 0);
        int[] oj = piel.ojos;
        for (int q = 0; q + 1 < oj.length; q += 2) {
            int u = oj[q], v = oj[q + 1];
            for (int du = -1; du <= 1; du++)
                for (int dv = -1; dv <= 1; dv++) {
                    int uu = u + du, vv = v + dv;
                    if (esOjo(uu, vv) || uu < 8 || uu >= 16 || vv < 8 || vv >= 16) continue;
                    int g = du == 0 || dv == 0 ? 34 : 14;
                    gris(im, uu, vv, g, true);
                    gris(im, uu + 32, vv, g / 3, true);
                }
        }
        for (int q = 0; q + 1 < oj.length; q += 2) {
            int u = oj[q], v = oj[q + 1];
            gris(im, u, v, 255, false);
            boolean tapa = piel.ojosCapa || alfa[(v * k + k / 2) * S + (u + 32) * k + k / 2];
            gris(im, u + 32, v, tapa ? 255 : 90, false);
        }
        texOjos.upload();
    }

    private boolean esOjo(int u, int v) {
        int[] oj = piel.ojos;
        for (int q = 0; q + 1 < oj.length; q += 2) if (oj[q] == u && oj[q + 1] == v) return true;
        return false;
    }

    /** Pinta el bloque k×k del texel (u, v) de 64 con un gris (con max = se queda el más claro). */
    private void gris(NativeImage im, int u, int v, int g, boolean max) {
        for (int a = 0; a < k; a++)
            for (int b = 0; b < k; b++) {
                int x = u * k + a, y = v * k + b;
                int gg = g;
                if (max) gg = Math.max(gg, im.getPixelRGBA(x, y) & 0xFF);
                im.setPixelRGBA(x, y, 0xFF000000 | (gg << 16) | (gg << 8) | gg);
            }
    }

    /** Grises de la nieve y su reparto acumulado (sacados de la skin de referencia «Hombre de estática»). */
    private static final float[] GRIS = {62, 84, 106, 128, 150, 170, 190, 208, 224};
    private static final float[] ACUM = {0.029F, 0.12F, 0.27F, 0.473F, 0.667F, 0.814F, 0.9F, 0.944F, 1F};

    private static int nivel(float q) {
        for (int n = 0; n < ACUM.length; n++) if (q < ACUM[n]) return n;
        return ACUM.length - 1;
    }

    private static int argb(float r, float g, float b, float a) {
        int ri = Mth.clamp((int) r, 0, 255), gi = Mth.clamp((int) g, 0, 255), bi = Mth.clamp((int) b, 0, 255), ai = Mth.clamp((int) a, 0, 255);
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;             // NativeImage: ABGR
    }

    /** Repinta (20 veces por segundo) la nieve hasta la cobertura dada (0..1) (= estatica_viva.Disp.pintar). */
    public void actualizar(float cobertura, long ms) {
        long cl = ms / 50;
        if (cl == cuadro && Math.abs(cobertura - cobPintada) < 0.01F) return;
        cuadro = cl;
        cobPintada = cobertura;
        NativeImage im = tex.getPixels();
        if (im == null) return;
        final int S = tam, seed = semilla, c = (int) cl;
        final float tt = ms / 1000F;
        // 1. qué lleva estática (con el borde que repta) y las filas desplazadas
        for (int i = 0; i < S * S; i++) {
            on[i] = false;
            if (!valido[i] || orden[i] < 0 || !alfa[i] || ojo[i]) continue;
            float jit = 0.06F * (ruido(px[i] * 0.5F, py[i] * 0.5F + tt * 1.3F, pz[i] * 0.5F, seed + 21) - 0.5F);
            on[i] = orden[i] + jit <= cobertura;
        }
        for (int v = 0; v < S; v++) {
            dx[v] = 0;
            if (hash(v, c >> 1, 11, seed) < 0.07F)
                dx[v] = (hash(v, c >> 1, 12, seed) < 0.5F ? 1 : -1) * (1 + (int) (hash(v, c >> 1, 13, seed) * 2)) * k;
        }
        for (int v = 0; v < S; v++)
            for (int u = 0; u < S; u++) {
                int i = v * S + u;
                boolean base = valido[i] && !capa[i];
                int su = u - dx[v];
                boolean b = on[i];
                if (su >= 0 && su < S && valido[v * S + su] && !capa[v * S + su]) b = on[v * S + su];
                onb[i] = b && base && alfa[i] && !ojo[i];
            }
        // 2. distancias al borde (manhattan, en la capa base): dentro y fuera
        for (int i = 0; i < S * S; i++) {
            boolean base = valido[i] && !capa[i];
            din[i] = !base || onb[i] ? 99 : 0;
            dout[i] = !base || !onb[i] ? 99 : 0;
        }
        chaflan(din, S);
        chaflan(dout, S);
        // 3. color
        float yb = 34 - (tt * 7) % 44;
        boolean destello = hash(c, 0, 7, seed) < 0.12F;
        float yf = 2 + hash(c, 1, 7, seed) * 30;
        for (int v = 0; v < S; v++)
            for (int u = 0; u < S; u++) {
                int i = v * S + u;
                int su = Mth.clamp(u - dx[v], 0, S - 1);
                float white = hash(su, v, c, seed + 1);
                if (onb[i]) {
                    // nieve clara y fina, como la skin de referencia del hombre de estática: 9 grises algo azulados
                    // (62..224, media ~145), grano de un texel, poco contraste, sin negros ni blancos puros
                    float nube = ruido(px[i] * 0.45F, py[i] * 0.45F - tt * 2F, pz[i] * 0.45F, seed + 4);
                    float q = white * 0.86F + (nube - 0.5F) * 0.28F;
                    float db = py[i] - yb;
                    if (Math.abs(db) < 1.5F) q += 0.16F * (1 - Math.abs(db) / 1.5F);
                    else if (db > -3.5F && db <= -1.5F) q -= 0.08F;
                    if (destello && Math.abs(py[i] - yf) < 1) q += 0.12F;
                    if (((v / k) & 1) == 0) q -= 0.035F;
                    if (din[i] == 2) q -= 0.05F;
                    if (hash(su, v, c >> 1, seed + 9) < 0.012F) q = 0.99F;
                    float g = GRIS[nivel(Mth.clamp(q, 0F, 0.999F))];
                    float r = g, gg = g, b = Math.min(255, g + 6), a = 240;
                    if (din[i] == 1) {
                        boolean hueco = hash(u, v, c, seed + 12) < 0.2F;
                        boolean rojo = hash(u >> 1, v, c >> 2, seed + 14) < 0.5F;
                        float gb = g * 0.8F;
                        r = rojo ? gb + 40 : gb;
                        gg = rojo ? gb : gb + 20;
                        b = rojo ? gb : gb + 44;
                        a = hueco ? 0 : 215;
                    }
                    im.setPixelRGBA(u, v, argb(r, gg, b, a));
                    continue;
                }
                boolean base = valido[i] && !capa[i];
                if (base && alfa[i] && !ojo[i] && (dout[i] == 1 || dout[i] == 2)) {
                    float h = hash(u, v, c >> 2, seed + 13);
                    if (h < (dout[i] == 1 ? 0.16F : 0.05F)) {
                        float w2 = 140 + 100 * white;
                        im.setPixelRGBA(u, v, argb(w2, w2, w2 + 6, dout[i] == 1 ? 180 : 140));
                        continue;
                    }
                }
                im.setPixelRGBA(u, v, 0);
            }
        // 4. gotas que escurren desde el borde de abajo
        for (int i = 0; i < S * S; i++) {
            int j = abajo[i];
            if (!onb[i] || j < 0 || onb[j]) continue;
            int u = i % S, v = i / S;
            if (hash(u, v, 0, seed + 20) >= 0.5F) continue;
            float P = 1.3F + 1.4F * hash(u, v, 1, seed + 20);
            float ph = tt / P + hash(u, v, 2, seed + 20);
            ph -= Mth.floor(ph);
            int L = (2 + (int) (hash(u, v, 3, seed + 20) * 4)) * k;
            int nc = 0;
            camino[nc++] = j;
            while (nc < 16 * k && abajo[camino[nc - 1]] >= 0 && !onb[abajo[camino[nc - 1]]]) {
                camino[nc] = abajo[camino[nc - 1]];
                nc++;
            }
            if (ph < 0.65F) {
                int nn = (int) (L * ph / 0.65F + 0.5F);
                for (int s = 0; s < nn && s < nc; s++) gota(im, camino[s], s == nn - 1 ? 240 : 170, s == nn - 1 ? 235 : 225);
            } else {
                float q = (ph - 0.65F) / 0.35F;
                int off = L + (int) (q * 7 * k), al = (int) (200 * (1 - q));
                if (off < nc) gota(im, camino[off], 230, al);
                if (off + 1 < nc) gota(im, camino[off + 1], 200, al);
            }
        }
        // 5. capa de fuera: nieve rala solo donde la piel tiene algo (sombreros, pelo, chaquetas)
        for (int i = 0; i < S * S; i++) {
            if (!capa[i] || !valido[i] || orden[i] < 0 || !alfa[i] || ojo[i]) continue;
            int u = i % S, v = i / S;
            float jit = 0.06F * (ruido(px[i] * 0.5F, py[i] * 0.5F + tt * 1.3F, pz[i] * 0.5F, seed + 21) - 0.5F);
            if (orden[i] + jit > cobertura || hash(u, v, c >> 1, seed + 30) >= 0.45F) continue;
            int su = Mth.clamp(u - dx[v], 0, S - 1);
            float gc = (0.45F + 0.55F * hash(su, v, c, seed + 1)) * 255;
            im.setPixelRGBA(u, v, argb(gc, gc, gc + 8, 150));
        }
        tex.upload();
    }

    private void gota(NativeImage im, int j, int gr, int al) {
        if (ojo[j] || !alfa[j]) return;
        int u = j % tam, v = j / tam;
        int a = Math.max((im.getPixelRGBA(u, v) >>> 24) & 0xFF, al);
        im.setPixelRGBA(u, v, argb(gr, gr, gr + 8, a));
    }

    /** Distancia manhattan exacta a la celda 0 más cercana (dos pasadas). */
    private static void chaflan(int[] d, int S) {
        for (int v = 0; v < S; v++)
            for (int u = 0; u < S; u++) {
                int i = v * S + u;
                if (v > 0) d[i] = Math.min(d[i], d[i - S] + 1);
                if (u > 0) d[i] = Math.min(d[i], d[i - 1] + 1);
            }
        for (int v = S - 1; v >= 0; v--)
            for (int u = S - 1; u >= 0; u--) {
                int i = v * S + u;
                if (v < S - 1) d[i] = Math.min(d[i], d[i + S] + 1);
                if (u < S - 1) d[i] = Math.min(d[i], d[i + 1] + 1);
            }
    }

    public void cerrar() {
        var tm = Minecraft.getInstance().getTextureManager();
        if (rl != null) tm.release(rl);
        if (rlOjos != null) tm.release(rlOjos);
    }
}
