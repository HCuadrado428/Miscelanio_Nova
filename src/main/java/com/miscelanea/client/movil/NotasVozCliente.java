package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import com.miscelanea.movil.MovilVoz;
import com.miscelanea.movil.NotasVoz;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Notas de voz en el cliente.
 * <p>
 * <b>Grabar</b>: mientras se mantiene pulsado el micro del chat se guarda el micro del jugador a 48 kHz mono. La fuente
 * principal es Simple Voice Chat ({@code ClientSoundEvent}, a través de {@link MovilVoz#microCliente}); como el voicechat
 * solo da audio mientras transmite (pulsar para hablar o activación por voz), si en 250 ms no llega nada se abre el micro
 * directamente con OpenAL (captura de LWJGL, la misma que usa el voicechat). Al soltar se comprime en Opus con el códec del
 * voicechat, se calcula la forma de onda y se sube en trozos al servidor.
 * <p>
 * <b>Oír</b>: se pide el archivo al servidor, se decodifica y se reproduce con un sonido propio en streaming (categoría
 * Voz, sin posición), así que solo lo oye quien lo reproduce.
 */
public final class NotasVozCliente {
    private NotasVozCliente() {}

    private static final int TRAMA = 960, HZ = 48000;
    private static final AudioFormat FORMATO = new AudioFormat(HZ, 16, 1, true, false);

    // ------------------------------------------------------------------ grabación
    private static boolean grabando, alcIntentado;
    private static int chatGrabando;
    private static long tInicio, ultimoSvc;
    private static final ConcurrentLinkedQueue<short[]> COLA = new ConcurrentLinkedQueue<>();
    private static final List<short[]> TRAMAS = new ArrayList<>();
    private static long alc;
    private static short[] resto = new short[0];
    /** Nivel del micro (0..1) para la animación mientras se graba. */
    private static float nivel;

    public static boolean grabando() {
        return grabando;
    }

    public static long msGrabados() {
        return grabando ? Util.getMillis() - tInicio : 0;
    }

    public static float nivel() {
        return nivel;
    }

    static boolean empezar(int chat) {
        if (grabando) return false;
        if (!MovilCliente.notasVoz()) {
            MovilCliente.aviso(Component.translatable("movil.miscelanea.necesitas_svc_notas"));
            return false;
        }
        parar();
        grabando = true;
        alcIntentado = false;
        chatGrabando = chat;
        tInicio = Util.getMillis();
        ultimoSvc = 0;
        COLA.clear();
        TRAMAS.clear();
        resto = new short[0];
        nivel = 0;
        MovilVoz.microCliente = a -> {
            if (COLA.size() < 400) COLA.add(a);
        };
        return true;
    }

    /** Termina la grabación: enviar = mandarla al chat; si no, se descarta. */
    static void terminar(boolean enviar) {
        if (!grabando) return;
        grabando = false;
        MovilVoz.microCliente = null;
        recoger();
        cerrarAlc();
        long ms = TRAMAS.size() * 20L;
        List<short[]> tramas = new ArrayList<>(TRAMAS);
        TRAMAS.clear();
        COLA.clear();
        if (!enviar) return;
        if (ms < 600) {
            MovilCliente.aviso(Component.translatable("movil.miscelanea.nota_corta"));
            return;
        }
        byte[] blob = codificar(tramas);
        if (blob == null) {
            MovilCliente.aviso(Component.translatable("movil.miscelanea.necesitas_svc_notas"));
            return;
        }
        if (blob.length > MovilCliente.vozMaxBytes) {
            MovilCliente.aviso(Component.translatable("movil.miscelanea.aviso.nota_grande"));
            return;
        }
        byte[] onda = onda(tramas);
        int chat = chatGrabando, dur = (int) ms;
        for (int i = 0; i < blob.length; i += MovilRed.TROZO) {
            int fin = Math.min(blob.length, i + MovilRed.TROZO);
            byte[] t = java.util.Arrays.copyOfRange(blob, i, fin);
            boolean ultimo = fin >= blob.length;
            MovilRed.alServidor(MovilRed.C_VOZ_TROZO, b -> {
                b.writeVarInt(chat);
                b.writeBoolean(ultimo);
                b.writeByteArray(t);
                if (ultimo) {
                    b.writeVarInt(dur);
                    b.writeByteArray(onda);
                }
            });
        }
        MovilCliente.sonido(MovilRegistro.CLIC.get(), 0.6F, 1.4F);
    }

    /** Pasa lo que haya llegado del micro a TRAMAS (y rellena con silencio los huecos del voicechat). */
    private static void recoger() {
        short[] a;
        boolean svc = false;
        while ((a = COLA.poll()) != null) {
            svc = true;
            if (alc == 0) meter(a);
        }
        if (svc) ultimoSvc = Util.getMillis();
        if (alc != 0) {
            try {
                int n = ALC10.alcGetInteger(alc, ALC11.ALC_CAPTURE_SAMPLES);
                if (n > 0) {
                    ShortBuffer sb = BufferUtils.createShortBuffer(n);
                    ALC11.alcCaptureSamples(alc, sb, n);
                    short[] s = new short[n];
                    sb.get(s);
                    meter(s);
                }
            } catch (Throwable t) {
                cerrarAlc();
            }
        } else {
            // el voicechat no manda nada mientras no transmites: el tiempo que falta va como silencio
            long esperadas = (Util.getMillis() - tInicio) / 20;
            while (TRAMAS.size() < esperadas - 15) TRAMAS.add(new short[TRAMA]);
        }
    }

    private static void meter(short[] s) {
        short[] todo = new short[resto.length + s.length];
        System.arraycopy(resto, 0, todo, 0, resto.length);
        System.arraycopy(s, 0, todo, resto.length, s.length);
        int i = 0;
        float pico = 0;
        for (; i + TRAMA <= todo.length; i += TRAMA) {
            short[] f = java.util.Arrays.copyOfRange(todo, i, i + TRAMA);
            TRAMAS.add(f);
            for (short v : f) pico = Math.max(pico, Math.abs(v) / 32768F);
        }
        resto = java.util.Arrays.copyOfRange(todo, i, todo.length);
        nivel = Math.max(pico, nivel * 0.8F);
    }

    private static void abrirAlc() {
        alcIntentado = true;
        try {
            long d = ALC11.alcCaptureOpenDevice((ByteBuffer) null, HZ, AL10.AL_FORMAT_MONO16, HZ);
            if (d == 0) return;
            ALC11.alcCaptureStart(d);
            alc = d;
            COLA.clear();
        } catch (Throwable t) {
            alc = 0;
        }
    }

    private static void cerrarAlc() {
        if (alc == 0) return;
        try {
            ALC11.alcCaptureStop(alc);
            ALC11.alcCaptureCloseDevice(alc);
        } catch (Throwable ignored) {
        }
        alc = 0;
    }

    private static byte[] codificar(List<short[]> tramas) {
        var f = MovilVoz.nuevoCodificador;
        if (f == null) return null;
        MovilVoz.Codificador c = null;
        try {
            c = f.get();
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            out.write(NotasVoz.MAGIA);
            int n = tramas.size();
            out.write(n >>> 24);
            out.write(n >>> 16);
            out.write(n >>> 8);
            out.write(n);
            for (short[] t : tramas) {
                byte[] e = c.codificar(t);
                if (e == null) e = new byte[0];
                out.write(e.length >>> 8);
                out.write(e.length);
                out.write(e);
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.cerrar();
        }
    }

    /** Forma de onda: {@link NotasVoz#ONDA} barras (0..255), normalizadas al pico de la nota. */
    private static byte[] onda(List<short[]> tramas) {
        int n = NotasVoz.ONDA;
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            int a = i * tramas.size() / n, b = Math.max(a + 1, (i + 1) * tramas.size() / n);
            double s = 0;
            int k = 0;
            for (int j = a; j < b && j < tramas.size(); j++)
                for (short x : tramas.get(j)) {
                    s += (double) x * x;
                    k++;
                }
            v[i] = k == 0 ? 0 : Math.sqrt(s / k);
        }
        double max = 1;
        for (double x : v) max = Math.max(max, x);
        byte[] o = new byte[n];
        for (int i = 0; i < n; i++) o[i] = (byte) Math.min(255, (int) (Math.sqrt(v[i] / max) * 255));
        return o;
    }

    // ------------------------------------------------------------------ reproducción
    private static final LinkedHashMap<Integer, byte[]> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<Integer> PEDIDAS = new HashSet<>(), FALTAN = new HashSet<>();
    private static int quiero = -1, sonando = -1, msSonando;
    private static long tSonando;
    private static SoundInstance sonido;

    static void tick() {
        if (grabando) {
            recoger();
            if (alc == 0 && !alcIntentado && ultimoSvc == 0 && Util.getMillis() - tInicio > 250) abrirAlc();
            if (Util.getMillis() - tInicio >= MovilCliente.vozMaxSeg * 1000L) terminar(true);
            if (!(Minecraft.getInstance().screen instanceof MovilScreen)) terminar(false);
        }
        if (sonando >= 0 && (sonido == null || !Minecraft.getInstance().getSoundManager().isActive(sonido))
                && Util.getMillis() - tSonando > 600) {
            sonando = -1;
            sonido = null;
        }
    }

    static void recibir(int id, byte[] datos) {
        PEDIDAS.remove(id);
        if (datos.length == 0) {
            FALTAN.add(id);
            if (quiero == id) quiero = -1;
            return;
        }
        CACHE.put(id, datos);
        while (CACHE.size() > 24) {
            Iterator<Map.Entry<Integer, byte[]>> it = CACHE.entrySet().iterator();
            it.next();
            it.remove();
        }
        if (quiero == id) {
            quiero = -1;
            sonar(id, datos);
        }
    }

    public static boolean falta(int id) {
        return FALTAN.contains(id);
    }

    public static boolean cargando(int id) {
        return quiero == id;
    }

    public static boolean sonando(int id) {
        return sonando == id;
    }

    /** Progreso (0..1) de la nota que suena. */
    public static float progreso(int id) {
        if (sonando != id || msSonando <= 0) return 0;
        return Math.min(1, (Util.getMillis() - tSonando) / (float) msSonando);
    }

    /** Tocar la burbuja: reproduce o para. */
    static void alternar(int id) {
        if (sonando == id) {
            parar();
            return;
        }
        if (!MovilCliente.vozLocal() || !MovilVoz.codecDisponible()) {
            MovilCliente.aviso(Component.translatable("movil.miscelanea.necesitas_svc_notas"));
            return;
        }
        parar();
        byte[] d = CACHE.get(id);
        if (d != null) sonar(id, d);
        else if (!FALTAN.contains(id)) {
            quiero = id;
            if (PEDIDAS.add(id)) MovilRed.alServidor(MovilRed.C_VOZ_PEDIR, b -> b.writeVarInt(id));
        }
    }

    static void parar() {
        if (sonido != null) Minecraft.getInstance().getSoundManager().stop(sonido);
        sonido = null;
        sonando = -1;
        quiero = -1;
    }

    private static void sonar(int id, byte[] blob) {
        short[] pcm = decodificar(blob);
        if (pcm == null || pcm.length == 0) {
            FALTAN.add(id);
            return;
        }
        sonido = new Sonido(pcm);
        sonando = id;
        msSonando = pcm.length * 1000 / HZ;
        tSonando = Util.getMillis();
        Minecraft.getInstance().getSoundManager().play(sonido);
    }

    private static short[] decodificar(byte[] b) {
        var f = MovilVoz.nuevoDecodificador;
        if (f == null || b.length < 8) return null;
        MovilVoz.Decodificador d = null;
        try {
            d = f.get();
            int n = ((b[4] & 0xFF) << 24) | ((b[5] & 0xFF) << 16) | ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
            short[] out = new short[n * TRAMA];
            int i = 8, pos = 0;
            for (int k = 0; k < n && i + 2 <= b.length; k++) {
                int len = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
                i += 2;
                byte[] e = java.util.Arrays.copyOfRange(b, i, Math.min(b.length, i + len));
                i += len;
                short[] s = d.decodificar(e.length == 0 ? null : e);
                if (s == null) continue;
                int q = Math.min(s.length, out.length - pos);
                System.arraycopy(s, 0, out, pos, q);
                pos += q;
            }
            return pos == out.length ? out : java.util.Arrays.copyOf(out, pos);
        } catch (Throwable t) {
            return null;
        } finally {
            if (d != null) d.cerrar();
        }
    }

    static void limpiar() {
        terminar(false);
        parar();
        CACHE.clear();
        PEDIDAS.clear();
        FALTAN.clear();
    }

    /** Sonido sin posición que lee el PCM ya decodificado (Forge deja que un SoundInstance dé su propio stream). */
    private static final class Sonido extends AbstractSoundInstance {
        private final short[] pcm;

        Sonido(short[] pcm) {
            super(MovilRegistro.NOTA_VOZ.get(), SoundSource.VOICE, RandomSource.create());
            this.pcm = pcm;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 1.0F;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(new Flujo(pcm));
        }
    }

    private static final class Flujo implements AudioStream {
        private final short[] pcm;
        private int pos;

        Flujo(short[] pcm) {
            this.pcm = pcm;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMATO;
        }

        @Override
        public ByteBuffer read(int size) {
            int n = Math.max(0, Math.min(size / 2, pcm.length - pos));
            ByteBuffer b = BufferUtils.createByteBuffer(n * 2);
            for (int i = 0; i < n; i++) b.putShort(pcm[pos + i]);
            pos += n;
            b.flip();
            return b;
        }

        @Override
        public void close() {}
    }
}
