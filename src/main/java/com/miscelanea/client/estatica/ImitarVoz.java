package com.miscelanea.client.estatica;

import com.miscelanea.estatica.EstaticaRed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imitar voces (solo con Simple Voice Chat): mientras es de estática, el cliente guarda en silencio trozos de 0,6 a 3,5 s
 * de lo que dicen los demás (el audio tal cual llega, antes de cambiarle la voz), bajados a 8 kHz. Al usar el poder se
 * manda el trozo siguiente (mu-law de 8 bits) al servidor, que lo reparte a los de alrededor del bloque elegido; ellos lo
 * oyen desde allí, con estática ({@link #reproducir}).
 */
public final class ImitarVoz {
    private ImitarVoz() {}

    public static final int SR = 8000, MIN = SR * 6 / 10, MAX = EstaticaRed.MAX_GRABACION;

    record Trozo(UUID quien, short[] pcm) {}

    private static final class Grabando {
        final short[] buf = new short[MAX];
        int n;
        long ultimo;
    }

    private static volatile boolean grabando;
    private static final Map<UUID, Grabando> EN_CURSO = new HashMap<>();
    private static final Deque<Trozo> TROZOS = new ArrayDeque<>();
    private static int siguiente;

    /** Hilo de audio del voicechat: 960 muestras a 48 kHz → 160 a 8 kHz. */
    public static void grabar(UUID quien, short[] audio) {
        if (!grabando || audio == null) return;
        synchronized (ImitarVoz.class) {
            Grabando g = EN_CURSO.get(quien);
            long ahora = System.currentTimeMillis();
            if (g != null && ahora - g.ultimo > 400) {
                cerrar(quien, g);
                g = null;
            }
            if (g == null) {
                g = new Grabando();
                EN_CURSO.put(quien, g);
            }
            g.ultimo = ahora;
            for (int i = 0; i + 5 < audio.length && g.n < MAX; i += 6) {
                int s = 0;
                for (int k = 0; k < 6; k++) s += audio[i + k];
                g.buf[g.n++] = (short) (s / 6);
            }
            if (g.n >= MAX) {
                cerrar(quien, g);
                EN_CURSO.remove(quien);
            }
        }
    }

    private static void cerrar(UUID quien, Grabando g) {
        if (g.n < MIN) return;
        short[] c = new short[g.n];
        System.arraycopy(g.buf, 0, c, 0, g.n);
        TROZOS.addFirst(new Trozo(quien, c));
        while (TROZOS.size() > 6) TROZOS.removeLast();
        siguiente = 0;
    }

    /** Hilo del juego: activar/desactivar la grabación y cerrar trozos que ya han terminado. */
    static void tick(boolean activo) {
        grabando = activo;
        synchronized (ImitarVoz.class) {
            if (!activo) {
                EN_CURSO.clear();
                if (!TROZOS.isEmpty() && !EstaticaCliente.yo()) TROZOS.clear();
                return;
            }
            long ahora = System.currentTimeMillis();
            List<UUID> fin = new ArrayList<>();
            for (Map.Entry<UUID, Grabando> en : EN_CURSO.entrySet()) if (ahora - en.getValue().ultimo > 450) fin.add(en.getKey());
            for (UUID u : fin) cerrar(u, EN_CURSO.remove(u));
        }
    }

    static synchronized int cuantos() {
        return TROZOS.size();
    }

    /** Nombre de quien dijo el próximo trozo que se usará (para la rueda / HUD). */
    static synchronized String proximo() {
        if (TROZOS.isEmpty()) return "";
        Trozo t = new ArrayList<>(TROZOS).get(Math.min(siguiente, TROZOS.size() - 1));
        var con = Minecraft.getInstance().getConnection();
        PlayerInfo pi = con == null ? null : con.getPlayerInfo(t.quien());
        return (pi == null ? "?" : pi.getProfile().getName()) + String.format(" %.1fs", t.pcm().length / (float) SR);
    }

    static void usar(BlockPos donde) {
        Trozo t;
        synchronized (ImitarVoz.class) {
            if (TROZOS.isEmpty()) t = null;
            else {
                List<Trozo> l = new ArrayList<>(TROZOS);
                t = l.get(siguiente % l.size());
                siguiente = (siguiente + 1) % l.size();
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (t == null) {
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("estatica.miscelanea.imitar_nada"), true);
            return;
        }
        EstaticaRed.CANAL.sendToServer(new EstaticaRed.Grabacion(donde, codificar(t.pcm())));
    }

    // ------------------------------------------------------------------ mu-law (G.711)
    static byte[] codificar(short[] pcm) {
        byte[] out = new byte[Math.min(pcm.length, MAX)];
        for (int i = 0; i < out.length; i++) {
            int s = pcm[i];
            int signo = (s >> 8) & 0x80;
            if (signo != 0) s = -s;
            if (s > 32635) s = 32635;
            s += 0x84;
            int exp = 7;
            for (int m = 0x4000; (s & m) == 0 && exp > 0; m >>= 1) exp--;
            int man = (s >> (exp + 3)) & 0x0F;
            out[i] = (byte) ~(signo | (exp << 4) | man);
        }
        return out;
    }

    static short[] decodificar(byte[] d) {
        short[] out = new short[d.length];
        for (int i = 0; i < d.length; i++) {
            int u = ~d[i] & 0xFF;
            int signo = u & 0x80, exp = (u >> 4) & 7, man = u & 0x0F;
            int s = ((man << 3) + 0x84) << exp;
            s -= 0x84;
            out[i] = (short) (signo != 0 ? -s : s);
        }
        return out;
    }

    /** Reproduce una voz imitada: con nieve de fondo, cortes y chasquidos, como por una radio rota. */
    static void reproducir(EstaticaRed.Imitar p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        short[] pcm = decodificar(p.datos);
        int s = 0x1234567 ^ pcm.length;
        int corte = 0;
        float env = 0;
        for (int i = 0; i < pcm.length; i++) {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            float x = pcm[i] / 32768F;
            env = Math.max(Math.abs(x), env * 0.995F);
            if (corte > 0) {
                corte--;
                x = 0;
            } else if ((s & 0x3FF) == 7) corte = 160 + ((s >>> 10) & 0x1FF);
            float ruido = ((s >>> 8) & 0xFFFF) / 32768F - 1F;
            x = Math.round(x * 10F) / 10F + ruido * (0.06F + env * 0.35F);
            if (((s >>> 4) & 0xFFF) == 3) x += 0.5F;
            out(pcm, i, x);
        }
        mc.getSoundManager().play(new EstaticaSonidos.Voz(p.x, p.y, p.z, pcm, SR));
    }

    private static void out(short[] pcm, int i, float x) {
        if (x > 1F) x = 1F;
        else if (x < -1F) x = -1F;
        pcm[i] = (short) (x * 32000);
    }
}
