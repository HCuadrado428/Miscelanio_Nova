package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilVoz;
import com.miscelanea.rave.MesaDjBlockEntity;
import com.miscelanea.rave.RaveRed;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Captura el audio del ordenador del DJ para la mesa: lee de una entrada de sonido (la "Mezcla estéreo" de Windows, un cable
 * de audio virtual o un micrófono), lo pasa a mono, lo comprime con el Opus de Simple Voice Chat en trozos de 20 ms y lo
 * manda al servidor. Para que salga lo que suena en Spotify hay que elegir una entrada que recoja el sonido del ordenador.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CapturaPc {
    private CapturaPc() {}

    private static volatile Thread hilo;
    private static volatile boolean seguir;
    private static volatile BlockPos mesa;
    private static volatile float ganancia = 1F;
    private static volatile float nivel;
    private static int elegida = -1;

    /** Entradas de sonido del ordenador; primero las que suelen recoger lo que suena (mezcla estéreo, cable virtual...). */
    public static List<Mixer.Info> entradas() {
        List<Mixer.Info> buenas = new ArrayList<>(), resto = new ArrayList<>();
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer m = AudioSystem.getMixer(mi);
                if (!m.isLineSupported(new Line.Info(TargetDataLine.class))) continue;
            } catch (Throwable t) {
                continue;
            }
            String n = (mi.getName() + " " + mi.getDescription()).toLowerCase(Locale.ROOT);
            boolean pc = n.contains("mezcla") || n.contains("stereo mix") || n.contains("cable") || n.contains("loopback") || n.contains("blackhole")
                    || n.contains("monitor of") || n.contains("what u hear") || n.contains("virtual");
            (pc ? buenas : resto).add(mi);
        }
        buenas.addAll(resto);
        return buenas;
    }

    public static String nombreEntrada() {
        List<Mixer.Info> l = entradas();
        if (l.isEmpty()) return "-";
        return l.get(Math.floorMod(elegida < 0 ? 0 : elegida, l.size())).getName();
    }

    public static void siguienteEntrada() {
        elegida = elegida < 0 ? 1 : elegida + 1;
    }

    public static boolean emitiendo() {
        return hilo != null && seguir;
    }

    /** Volumen de lo que se está mandando (0 a 1), para el medidor de la pantalla. */
    public static float nivel() {
        return emitiendo() ? nivel : 0;
    }

    public static void ganancia(float g) {
        ganancia = Math.max(0, Math.min(2, g));
    }

    /** Empieza a emitir por esa mesa. Devuelve un error para enseñar, o null si ha ido bien. */
    public static String empezar(BlockPos p) {
        parar();
        if (MovilVoz.nuevoCodificador == null) return "rave.miscelanea.dj.sin_voicechat";
        List<Mixer.Info> l = entradas();
        if (l.isEmpty()) return "rave.miscelanea.dj.sin_entrada";
        Mixer.Info mi = l.get(Math.floorMod(elegida < 0 ? 0 : elegida, l.size()));
        TargetDataLine linea = null;
        int canales = 2;
        for (int c : new int[]{2, 1}) {
            try {
                AudioFormat f = new AudioFormat(48000F, 16, c, true, false);
                linea = (TargetDataLine) AudioSystem.getMixer(mi).getLine(new DataLine.Info(TargetDataLine.class, f));
                linea.open(f, 960 * 2 * c * 8);
                canales = c;
                break;
            } catch (Throwable t) {
                linea = null;
            }
        }
        if (linea == null) return "rave.miscelanea.dj.entrada_no_abre";
        mesa = p.immutable();
        seguir = true;
        RaveRed.dj(p, RaveRed.DJ_EMITIR, "", 1);
        final TargetDataLine ln = linea;
        final int ch = canales;
        Thread t = new Thread(() -> bucle(ln, ch), "Miscelanea-DJ-captura");
        t.setDaemon(true);
        hilo = t;
        t.start();
        return null;
    }

    public static void parar() {
        BlockPos p = mesa;
        boolean estaba = emitiendo();
        seguir = false;
        hilo = null;
        mesa = null;
        if (estaba && p != null && Minecraft.getInstance().getConnection() != null) RaveRed.dj(p, RaveRed.DJ_EMITIR, "", 0);
    }

    private static void bucle(TargetDataLine linea, int canales) {
        MovilVoz.Codificador cod = null;
        try {
            cod = MovilVoz.nuevoCodificador.get();
            linea.start();
            byte[] buf = new byte[960 * 2 * canales];
            short[] mono = new short[960];
            while (seguir) {
                int leidos = 0;
                while (leidos < buf.length && seguir) {
                    int n = linea.read(buf, leidos, buf.length - leidos);
                    if (n <= 0) break;
                    leidos += n;
                }
                if (!seguir) break;
                double suma = 0;
                float g = ganancia;
                for (int i = 0; i < 960; i++) {
                    int v = 0;
                    for (int c = 0; c < canales; c++) {
                        int k = (i * canales + c) * 2;
                        v += (short) ((buf[k] & 0xFF) | (buf[k + 1] << 8));
                    }
                    v = Math.round(v / (float) canales * g);
                    v = Math.max(-32768, Math.min(32767, v));
                    mono[i] = (short) v;
                    suma += (double) v * v;
                }
                nivel = (float) Math.min(1, Math.sqrt(suma / 960) / 12000);
                byte[] opus = cod.codificar(mono);
                BlockPos p = mesa;
                if (opus != null && opus.length > 0 && p != null) Minecraft.getInstance().execute(() -> RaveRed.audio(p, opus));
            }
        } catch (Throwable t) {
            Miscelanea.LOGGER.warn("[Rave] La captura del audio del ordenador se ha parado: {}", t.toString());
        } finally {
            try {
                linea.stop();
                linea.close();
            } catch (Throwable ignored) {
            }
            if (cod != null) cod.cerrar();
            seguir = false;
        }
    }

    /** Si la mesa ya no dice que emito yo (otro la ha parado, me he alejado...), se para la captura. */
    @SubscribeEvent
    public static void tic(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !emitiendo()) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos p = mesa;
        if (mc.level == null || mc.player == null || p == null) {
            seguir = false;
            return;
        }
        if (mc.level.getGameTime() % 20 != 0) return;
        if (mc.level.getBlockEntity(p) instanceof MesaDjBlockEntity m && m.emisor() != null && !mc.player.getUUID().equals(m.emisor())) seguir = false;
        if (!(mc.level.getBlockEntity(p) instanceof MesaDjBlockEntity)) seguir = false;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        seguir = false;
        hilo = null;
        mesa = null;
    }
}
