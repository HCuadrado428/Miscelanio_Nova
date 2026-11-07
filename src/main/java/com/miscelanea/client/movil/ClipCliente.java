package com.miscelanea.client.movil;

import com.miscelanea.movil.Clips;
import com.miscelanea.movil.MovilRed;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vídeos cortos en el cliente: empaquetarlos y subirlos al grabar, descargarlos (con una caché pequeña) y reproducirlos
 * (una imagen JPEG cada 1/fps s en una sola textura, con su sonido si lo tiene). Formato en {@link Clips}.
 */
public final class ClipCliente {
    private ClipCliente() {}

    /** Un vídeo ya descargado y separado en imágenes. */
    public static final class Clip {
        final int fps, w, h;
        final List<byte[]> imagenes;
        final byte[] audio;

        Clip(int fps, int w, int h, List<byte[]> imagenes, byte[] audio) {
            this.fps = fps;
            this.w = w;
            this.h = h;
            this.imagenes = imagenes;
            this.audio = audio;
        }

        int ms() {
            return imagenes.size() * 1000 / fps;
        }
    }

    // ------------------------------------------------------------------ empaquetar y subir
    static byte[] empaquetar(int fps, int w, int h, List<byte[]> imagenes, byte[] audio) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(64 * 1024);
        o.writeBytes(Clips.MAGIA);
        o.write(fps);
        o.write(w >>> 8);
        o.write(w);
        o.write(h >>> 8);
        o.write(h);
        int n = imagenes.size();
        o.write(n >>> 24);
        o.write(n >>> 16);
        o.write(n >>> 8);
        o.write(n);
        for (byte[] f : imagenes) {
            o.write(f.length >>> 8);
            o.write(f.length);
            o.writeBytes(f);
        }
        int a = audio == null ? 0 : audio.length;
        o.write(a >>> 24);
        o.write(a >>> 16);
        o.write(a >>> 8);
        o.write(a);
        if (a > 0) o.writeBytes(audio);
        return o.toByteArray();
    }

    static void subir(byte[] blob) {
        for (int i = 0; i < blob.length; i += MovilRed.TROZO) {
            int fin = Math.min(blob.length, i + MovilRed.TROZO);
            byte[] t = java.util.Arrays.copyOfRange(blob, i, fin);
            boolean ultimo = fin >= blob.length;
            MovilRed.alServidor(MovilRed.C_CLIP_TROZO, b -> {
                b.writeBoolean(ultimo);
                b.writeByteArray(t);
            });
        }
    }

    // ------------------------------------------------------------------ descargar
    private static final LinkedHashMap<String, Clip> CACHE = new LinkedHashMap<>(8, 0.75F, true);
    private static final Map<String, Long> PEDIDOS = new HashMap<>();
    private static final java.util.Set<String> FALTAN = new java.util.HashSet<>();

    private static String clave(int tipo, int id) {
        return tipo + ":" + id;
    }

    /** El vídeo si ya está; si no, lo pide y devuelve null. */
    static Clip get(int tipo, int id) {
        String k = clave(tipo, id);
        Clip c = CACHE.get(k);
        if (c != null || FALTAN.contains(k)) return c;
        Long p = PEDIDOS.get(k);
        if (p == null || Util.getMillis() - p > 8000) {
            PEDIDOS.put(k, Util.getMillis());
            MovilRed.alServidor(MovilRed.C_CLIP_PEDIR, b -> {
                b.writeByte(tipo);
                b.writeVarInt(id);
            });
        }
        return null;
    }

    static boolean falta(int tipo, int id) {
        return FALTAN.contains(clave(tipo, id));
    }

    static void recibir(int tipo, int id, byte[] datos) {
        String k = clave(tipo, id);
        PEDIDOS.remove(k);
        Clip c = leer(datos);
        if (c == null) {
            FALTAN.add(k);
            return;
        }
        CACHE.put(k, c);
        while (CACHE.size() > 4) {
            Iterator<Map.Entry<String, Clip>> it = CACHE.entrySet().iterator();
            it.next();
            it.remove();
        }
    }

    private static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static int i32(byte[] b, int i) {
        return ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    static Clip leer(byte[] b) {
        try {
            if (b == null || b.length < 13) return null;
            for (int i = 0; i < 4; i++) if (b[i] != Clips.MAGIA[i]) return null;
            int fps = b[4] & 0xFF, w = u16(b, 5), h = u16(b, 7), n = i32(b, 9);
            if (fps < 1 || n < 1 || n > 2000) return null;
            int i = 13;
            List<byte[]> l = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                int len = u16(b, i);
                i += 2;
                l.add(java.util.Arrays.copyOfRange(b, i, i + len));
                i += len;
            }
            int a = i32(b, i);
            i += 4;
            byte[] audio = a > 0 ? java.util.Arrays.copyOfRange(b, i, i + a) : new byte[0];
            return new Clip(fps, w, h, l, audio);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ reproducir
    /** Reproductor de un vídeo: una textura que se rellena con la imagen que toca. */
    static final class Reproductor {
        private final Clip clip;
        private DynamicTexture tex;
        ResourceLocation rl;
        private int mostrada = -1;
        private long inicio = -1, pausadoEn;
        private boolean conSonido;

        Reproductor(Clip clip) {
            this.clip = clip;
        }

        boolean reproduciendo() {
            return inicio >= 0;
        }

        void play() {
            if (reproduciendo()) return;
            long desde = pausadoEn >= clip.ms() ? 0 : pausadoEn;
            inicio = Util.getMillis() - desde;
            conSonido = desde == 0 && NotasVozCliente.sonarBlob(clip.audio);
        }

        void pausa() {
            if (!reproduciendo()) return;
            pausadoEn = Util.getMillis() - inicio;
            inicio = -1;
            if (conSonido) NotasVozCliente.parar();
            conSonido = false;
        }

        /** 0..1 */
        float progreso() {
            long t = reproduciendo() ? Util.getMillis() - inicio : pausadoEn;
            return Math.min(1, t / (float) Math.max(1, clip.ms()));
        }

        int w() {
            return clip.w;
        }

        int h() {
            return clip.h;
        }

        int ms() {
            return clip.ms();
        }

        /** Pone en la textura la imagen que toca ahora (y para al final). */
        void actualizar() {
            int i;
            if (reproduciendo()) {
                long t = Util.getMillis() - inicio;
                i = (int) (t * clip.fps / 1000);
                if (i >= clip.imagenes.size()) {
                    i = clip.imagenes.size() - 1;
                    pausadoEn = clip.ms();
                    inicio = -1;
                    conSonido = false;
                }
            } else i = Math.min(clip.imagenes.size() - 1, (int) (pausadoEn * clip.fps / 1000));
            if (i == mostrada && tex != null) return;
            NativeImage img = MovilCaptura.decodificar(clip.imagenes.get(Math.max(0, i)));
            if (img == null) return;
            mostrada = i;
            if (tex != null && tex.getPixels() != null && tex.getPixels().getWidth() == img.getWidth() && tex.getPixels().getHeight() == img.getHeight()) {
                tex.getPixels().copyFrom(img);
                img.close();
                tex.upload();
            } else {
                soltar();
                tex = new DynamicTexture(img);
                rl = Minecraft.getInstance().getTextureManager().register("miscelanea_clip", tex);
            }
        }

        void soltar() {
            if (rl != null) Minecraft.getInstance().getTextureManager().release(rl);
            rl = null;
            tex = null;
            if (conSonido) NotasVozCliente.parar();
            conSonido = false;
        }
    }

    static void limpiar() {
        CACHE.clear();
        PEDIDOS.clear();
        FALTAN.clear();
    }
}
