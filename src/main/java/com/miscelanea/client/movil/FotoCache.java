package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Texturas de las fotos que se han pedido al servidor (como mucho {@link #MAX}; se sueltan las menos usadas) y la del vídeo del otro. */
public final class FotoCache {
    private FotoCache() {}

    private static final int MAX = 48;

    public static final class Tex {
        final DynamicTexture t;
        public final ResourceLocation rl;
        public final int w, h;

        Tex(NativeImage img, String nombre) {
            t = new DynamicTexture(img);
            rl = Minecraft.getInstance().getTextureManager().register("miscelanea_movil_" + nombre, t);
            w = img.getWidth();
            h = img.getHeight();
        }

        void soltar() {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
    }

    private static final LinkedHashMap<String, Tex> MAPA = new LinkedHashMap<>(64, 0.75F, true);
    /** Pedidas y cuándo (si el servidor no contesta en unos segundos se vuelven a pedir: antes se quedaban "cargando"). */
    private static final Map<String, Long> PEDIDAS = new java.util.HashMap<>();
    /** Las que el servidor dijo que no existen y cuándo (se vuelve a probar pasado un rato, por si acaban de subirse). */
    private static final Map<String, Long> FALTAN = new java.util.HashMap<>();
    private static final long ESPERA = 6000, REINTENTO = 15000;
    private static Tex video;
    private static int pedidasAhora;

    private static String clave(int tipo, int id) {
        return tipo + ":" + id;
    }

    /** La textura si ya está; si no, la pide (pocas a la vez) y devuelve null. */
    public static Tex get(int tipo, int id) {
        String k = clave(tipo, id);
        Tex t = MAPA.get(k);
        if (t != null) return t;
        long ahora = net.minecraft.Util.getMillis();
        // las que llevan mucho sin respuesta dejan sitio
        if (!PEDIDAS.isEmpty()) {
            PEDIDAS.values().removeIf(c -> ahora - c > ESPERA);
            pedidasAhora = PEDIDAS.size();
        }
        Long f = FALTAN.get(k);
        if (f != null && ahora - f < REINTENTO) return null;
        if (PEDIDAS.containsKey(k) || pedidasAhora >= 6) return null;
        FALTAN.remove(k);
        PEDIDAS.put(k, ahora);
        pedidasAhora++;
        MovilRed.alServidor(MovilRed.C_FOTO_PEDIR, b -> {
            b.writeByte(tipo);
            b.writeVarInt(id);
        });
        return null;
    }

    public static boolean falta(int tipo, int id) {
        return FALTAN.containsKey(clave(tipo, id));
    }

    static void recibir(int tipo, int id, byte[] datos) {
        String k = clave(tipo, id);
        if (PEDIDAS.remove(k) != null) pedidasAhora = Math.max(0, pedidasAhora - 1);
        NativeImage img = MovilCaptura.decodificar(datos);
        if (img == null) {
            FALTAN.put(k, net.minecraft.Util.getMillis());
            return;
        }
        Tex viejo = MAPA.put(k, new Tex(img, tipo + "_" + id));
        if (viejo != null) viejo.soltar();
        while (MAPA.size() > MAX) {
            Iterator<Map.Entry<String, Tex>> it = MAPA.entrySet().iterator();
            it.next().getValue().soltar();
            it.remove();
        }
    }

    public static void olvidar(int tipo, int id) {
        Tex t = MAPA.remove(clave(tipo, id));
        if (t != null) t.soltar();
    }

    // ------------------------------------------------------------------ vídeo del otro
    static void video(byte[] datos) {
        if (datos.length == 0) {
            quitarVideo();
            return;
        }
        NativeImage img = MovilCaptura.decodificar(datos);
        if (img == null) return;
        if (video != null && video.w == img.getWidth() && video.h == img.getHeight() && video.t.getPixels() != null) {
            NativeImage dst = video.t.getPixels();
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) dst.setPixelRGBA(x, y, img.getPixelRGBA(x, y));
            img.close();
            video.t.upload();
        } else {
            quitarVideo();
            video = new Tex(img, "video");
        }
    }

    public static Tex video() {
        return video;
    }

    public static void quitarVideo() {
        if (video != null) video.soltar();
        video = null;
    }

    public static void limpiar() {
        for (Tex t : MAPA.values()) t.soltar();
        MAPA.clear();
        PEDIDAS.clear();
        FALTAN.clear();
        pedidasAhora = 0;
        quitarVideo();
    }

    /** Dibuja t llenando el rectángulo (recorta lo que sobre para no deformar). */
    public static void cubrir(GuiGraphics g, Tex t, int x, int y, int w, int h) {
        float ar = (float) w / h, src = (float) t.w / t.h;
        float uw = t.w, vh = t.h, u = 0, v = 0;
        if (ar > src) {
            vh = t.w / ar;
            v = (t.h - vh) / 2;
        } else {
            uw = t.h * ar;
            u = (t.w - uw) / 2;
        }
        g.blit(t.rl, x, y, w, h, u, v, (int) uw, (int) vh, t.w, t.h);
    }
}
