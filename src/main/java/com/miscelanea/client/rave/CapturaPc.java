package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilVoz;
import com.miscelanea.rave.MesaDjBlockEntity;
import com.miscelanea.rave.RaveRed;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
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
 * Captura el audio del ordenador del DJ para la mesa, lo pasa a mono, le sube el volumen, lo comprime con el Opus de Simple
 * Voice Chat en trozos de 20 ms y lo manda al servidor. De dónde sale el sonido:
 * <ul>
 *   <li><b>Sonido del PC</b> (solo Windows, la primera opción): lo que suena por los altavoces, tal cual (Spotify, YouTube...),
 *       sin configurar nada ({@link SonidoPcWindows}).</li>
 *   <li>Una entrada de sonido que recoja lo que suena (la "Mezcla estéreo", un cable virtual, un "monitor" en Linux).</li>
 *   <li>Un micrófono: se puede, pero no recoge la música del ordenador (solo lo que le llegue por los altavoces), así que la
 *       pantalla avisa.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CapturaPc {
    private CapturaPc() {}

    /** Una fuente de sonido para emitir: el sonido del PC (sin mezclador) o una entrada de sonido de Java. */
    public record Entrada(@Nullable Mixer.Info mezclador, boolean delPc) {
        /** ¿Es el sonido del PC en directo (Windows)? */
        public boolean directo() {
            return mezclador == null;
        }
    }

    private static volatile Thread hilo;
    private static volatile boolean seguir;
    private static volatile BlockPos mesa;
    private static volatile float nivel;
    /** Error que ha dado la captura después de empezar (clave de traducción), para enseñarlo en la pantalla. */
    private static volatile String fallo;
    private static int elegida;

    /** De dónde se puede emitir; primero lo que recoge lo que suena en el ordenador y al final los micrófonos. */
    public static List<Entrada> entradas() {
        // preguntar al sistema por sus entradas de sonido tarda, y la pantalla lo mira en cada fotograma: se guarda unos segundos
        long ahora = System.currentTimeMillis();
        List<Entrada> guardadas = vistas;
        if (guardadas != null && ahora - vistasEn < 5000) return guardadas;
        List<Entrada> buenas = new ArrayList<>(), resto = new ArrayList<>();
        if (SonidoPcWindows.disponible()) buenas.add(new Entrada(null, true));
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
            (pc ? buenas : resto).add(new Entrada(mi, pc));
        }
        buenas.addAll(resto);
        vistas = List.copyOf(buenas);
        vistasEn = ahora;
        return vistas;
    }

    private static volatile List<Entrada> vistas;
    private static volatile long vistasEn;

    @Nullable
    private static Entrada elegida() {
        List<Entrada> l = entradas();
        return l.isEmpty() ? null : l.get(Math.floorMod(elegida, l.size()));
    }

    /** Nombre de la fuente elegida, para el botón. */
    public static Component nombreEntrada() {
        Entrada e = elegida();
        if (e == null) return Component.literal("-");
        if (e.directo()) return Component.translatable("rave.miscelanea.dj.entrada_pc");
        return Component.literal(e.mezclador().getName());
    }

    /** Aviso para la pantalla si lo elegido no va a recoger la música del ordenador (un micrófono), o null. */
    @Nullable
    public static String aviso() {
        Entrada e = elegida();
        if (e == null || e.delPc()) return null;
        return SonidoPcWindows.disponible() ? "rave.miscelanea.dj.entrada_micro" : "rave.miscelanea.dj.entrada_micro_otro";
    }

    /** Error de la captura en marcha (clave de traducción) o null. */
    @Nullable
    public static String fallo() {
        return fallo;
    }

    public static void siguienteEntrada() {
        elegida++;
        fallo = null;
    }

    /** Tics que lleva emitiendo esta captura (para darle tiempo al servidor a apuntarme como emisor). */
    private static int ticsEmitiendo;

    public static boolean emitiendo() {
        return hilo != null && seguir;
    }

    /** Volumen de lo que se está mandando (0 a 1), para el medidor de la pantalla. */
    public static float nivel() {
        return emitiendo() ? nivel : 0;
    }

    /** De dónde sale el sonido, de 20 en 20 ms (960 muestras mono a 48 kHz). */
    private interface Lector extends AutoCloseable {
        /** Rellena el trozo. Devuelve false si aún no hay sonido (hay que esperar un poco y volver a pedir). */
        boolean leer(short[] mono) throws Exception;

        @Override
        void close();
    }

    /** Empieza a emitir por esa mesa. Devuelve un error para enseñar, o null si ha ido bien. */
    public static String empezar(BlockPos p) {
        parar();
        fallo = null;
        if (MovilVoz.nuevoCodificador == null) return "rave.miscelanea.dj.sin_voicechat";
        Entrada e = elegida();
        if (e == null) return "rave.miscelanea.dj.sin_entrada";
        java.util.concurrent.Callable<Lector> abrir;
        if (e.directo()) {
            // el sonido del PC se abre en el propio hilo de la captura (Windows lo quiere todo en el mismo hilo)
            abrir = () -> {
                SonidoPcWindows w = new SonidoPcWindows();
                Miscelanea.LOGGER.info("[Rave] Emitiendo el sonido del PC ({})", w.descripcion());
                return new Lector() {
                    @Override
                    public boolean leer(short[] mono) {
                        return w.leer(mono);
                    }

                    @Override
                    public void close() {
                        w.close();
                    }
                };
            };
        } else {
            TargetDataLine linea = null;
            int canales = 2;
            for (int c : new int[]{2, 1}) {
                try {
                    AudioFormat f = new AudioFormat(48000F, 16, c, true, false);
                    linea = (TargetDataLine) AudioSystem.getMixer(e.mezclador()).getLine(new DataLine.Info(TargetDataLine.class, f));
                    linea.open(f, 960 * 2 * c * 8);
                    canales = c;
                    break;
                } catch (Throwable t) {
                    linea = null;
                }
            }
            if (linea == null) return "rave.miscelanea.dj.entrada_no_abre";
            final TargetDataLine ln = linea;
            final int ch = canales;
            abrir = () -> {
                ln.start();
                byte[] buf = new byte[960 * 2 * ch];
                return new Lector() {
                    @Override
                    public boolean leer(short[] mono) {
                        int leidos = 0;
                        while (leidos < buf.length && seguir) {
                            int n = ln.read(buf, leidos, buf.length - leidos);
                            if (n <= 0) return false;
                            leidos += n;
                        }
                        if (leidos < buf.length) return false;
                        for (int i = 0; i < 960; i++) {
                            int v = 0;
                            for (int c = 0; c < ch; c++) {
                                int k = (i * ch + c) * 2;
                                v += (short) ((buf[k] & 0xFF) | (buf[k + 1] << 8));
                            }
                            mono[i] = (short) (v / ch);
                        }
                        return true;
                    }

                    @Override
                    public void close() {
                        try {
                            ln.stop();
                            ln.close();
                        } catch (Throwable ignored) {
                        }
                    }
                };
            };
        }
        mesa = p.immutable();
        seguir = true;
        ticsEmitiendo = 0;
        RaveRed.dj(p, RaveRed.DJ_EMITIR, "", 1);
        final boolean directo = e.directo();
        Thread t = new Thread(() -> bucle(abrir, directo), "Miscelanea-DJ-captura");
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

    private static void bucle(java.util.concurrent.Callable<Lector> abrir, boolean directo) {
        MovilVoz.Codificador cod = null;
        Lector lector = null;
        Thread yo = Thread.currentThread();
        try {
            lector = abrir.call();
            var nuevo = MovilVoz.nuevoCodificadorMusica != null ? MovilVoz.nuevoCodificadorMusica : MovilVoz.nuevoCodificador;
            cod = nuevo.get();
            // la música floja (o un micro lejos de los altavoces) se sube hasta 4 veces; lo que ya va fuerte se deja como está
            Compresor compresor = new Compresor(48000F, 4F);
            short[] mono = new short[960];
            int esperas = 0;
            while (seguir && hilo == yo) {
                if (!lector.leer(mono)) {
                    // no suena nada (o aún no ha llegado): no se manda nada
                    if (++esperas > 20) nivel = 0;
                    Thread.sleep(5);
                    continue;
                }
                esperas = 0;
                double suma = 0;
                for (int i = 0; i < 960; i++) {
                    float v = compresor.procesar(mono[i] / 32768F);
                    mono[i] = (short) Math.round(v * 32767F);
                    suma += (double) v * v;
                }
                nivel = (float) Math.min(1, Math.sqrt(suma / 960) / 0.35);
                byte[] opus = cod.codificar(mono);
                BlockPos p = mesa;
                if (opus != null && opus.length > 0 && p != null) Minecraft.getInstance().execute(() -> RaveRed.audio(p, opus));
            }
        } catch (Throwable t) {
            Miscelanea.LOGGER.warn("[Rave] La captura del audio del ordenador se ha parado: {}", t.toString());
            if (hilo == yo) fallo = directo ? "rave.miscelanea.dj.pc_no_abre" : "rave.miscelanea.dj.entrada_no_abre";
        } finally {
            if (lector != null) lector.close();
            if (cod != null) cod.cerrar();
            // si esta captura sigue siendo la de ahora (no la ha sustituido otra), se da por parada y se avisa al servidor
            if (hilo == yo) {
                BlockPos p = mesa;
                boolean avisar = seguir && p != null;
                seguir = false;
                if (avisar) Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().getConnection() != null) RaveRed.dj(p, RaveRed.DJ_EMITIR, "", 0);
                });
            }
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
        ticsEmitiendo++;
        if (mc.level.getGameTime() % 20 != 0) return;
        // la mesa tiene que decir que emito yo; si dice otro o nadie (alguien ha puesto música, me he alejado, el servidor no
        // tiene Simple Voice Chat...), se para. Al empezar se deja un par de segundos para que llegue la respuesta del servidor.
        if (!(mc.level.getBlockEntity(p) instanceof MesaDjBlockEntity m)) seguir = false;
        else if (!mc.player.getUUID().equals(m.emisor()) && (m.emisor() != null || ticsEmitiendo > 60)) seguir = false;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        seguir = false;
        hilo = null;
        mesa = null;
    }
}
