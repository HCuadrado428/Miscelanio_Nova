package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.rave.MesaDjBlockEntity;
import com.miscelanea.rave.Pistas;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * La música de las mesas de DJ en este cliente: una por mesa, que empieza cuando la mesa se pone a sonar y para cuando
 * para. El volumen baja con la distancia hasta el alcance de la mesa (con sus altavoces), sin el límite de 16 bloques de
 * los sonidos normales.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MusicaDj {
    private MusicaDj() {}

    private static final Map<BlockPos, Sonido> SONANDO = new HashMap<>();

    /** ¿Está sonando esta mesa en este cliente? (para las pruebas) */
    public static boolean suena(BlockPos p) {
        Sonido s = SONANDO.get(p);
        return s != null && !s.isStopped();
    }

    /** Volumen que le llega ahora a este cliente de esa mesa (0 a 1). */
    public static float volumen(BlockPos p) {
        Sonido s = SONANDO.get(p);
        return s == null ? 0 : s.volumenCalculado();   // (getVolume() falla si el sonido aún no está cargado)
    }

    /**
     * Latido (1 en cada pulso y baja enseguida a 0) de la mesa que suena más cerca de {@code p}, a menos de 24 bloques; 0 si
     * no suena ninguna. Lo usan los altavoces para mover el cono al ritmo.
     */
    public static float latido(BlockPos p, float pt) {
        float b = pulsos(p, pt);
        if (b < 0) return 0;
        float k = 1 - (b % 1F);
        return k * k * k;
    }

    /** Pulsos que lleva la canción de la mesa que suena más cerca (a menos de 24 bloques), o -1 si no suena ninguna. */
    public static float pulsos(BlockPos p, float pt) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return -1;
        Sonido mejor = null;
        double md = 24 * 24;
        for (Map.Entry<BlockPos, Sonido> e : SONANDO.entrySet()) {
            if (e.getValue().isStopped()) continue;
            double d = e.getKey().distSqr(p);
            if (d < md) {
                md = d;
                mejor = e.getValue();
            }
        }
        if (mejor == null) return -1;
        Pistas.Pista pi = Pistas.de(mejor.pista);
        float bpm = pi == null ? 120 : pi.bpm();
        float seg = (mc.level.getGameTime() - mejor.inicio + pt) / 20F;
        return seg * bpm / 60F;
    }

    public static void tick(MesaDjBlockEntity be) {
        BlockPos p = be.getBlockPos();
        Sonido s = SONANDO.get(p);
        if (!be.sonando()) {
            if (s != null) {
                s.parar();
                SONANDO.remove(p);
            }
            return;
        }
        var mc = Minecraft.getInstance();
        boolean misma = s != null && !s.isStopped() && s.mesa == be && s.pista.equals(be.pista()) && s.inicio == be.inicio();
        // al cambiar de dimensión o con F3+T el juego corta todos los sonidos sin marcarlos parados: si ya no suena de verdad,
        // se vuelve a poner por donde va (no cada tic, por si el juego no tiene canales libres)
        if (misma && (mc.getSoundManager().isActive(s) || be.getLevel() == null || be.getLevel().getGameTime() - s.puesto < 40)) return;
        Pistas.Pista pi = Pistas.de(be.pista());
        if (pi == null) return;
        long lleva = be.getLevel() == null ? 0 : be.getLevel().getGameTime() - be.inicio();
        if (misma && lleva > pi.ticks() - 40) return;   // se ha acabado sola: la mesa pasará a la siguiente
        if (s != null) s.parar();
        Sonido n = new Sonido(be, pi);
        SONANDO.put(p.immutable(), n);
        Minecraft.getInstance().getSoundManager().play(n);
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        for (Sonido s : SONANDO.values()) s.parar();
        SONANDO.clear();
    }

    static final class Sonido extends AbstractTickableSoundInstance {
        private final MesaDjBlockEntity mesa;
        final String pista;
        final long inicio;
        /** Tic del mundo en que se puso a sonar en este cliente. */
        final long puesto;
        /** Ticks de canción que se salta al empezar (quien llega a mitad la oye por donde va, no desde el principio). */
        private final long saltar;

        Sonido(MesaDjBlockEntity mesa, Pistas.Pista pi) {
            super(Pistas.evento(pi), SoundSource.RECORDS, RandomSource.create());
            this.mesa = mesa;
            this.pista = mesa.pista();
            this.inicio = mesa.inicio();
            long ahora = mesa.getLevel() == null ? inicio : mesa.getLevel().getGameTime();
            this.puesto = ahora;
            this.saltar = Math.max(0, Math.min(ahora - inicio, pi.ticks()));
            BlockPos p = mesa.getBlockPos();
            x = p.getX() + 0.5;
            y = p.getY() + 0.5;
            z = p.getZ() + 0.5;
            attenuation = SoundInstance.Attenuation.NONE;
            looping = false;
            relative = false;
            volume = calcular();
        }

        void parar() {
            stop();
        }

        float volumenCalculado() {
            return volume;
        }

        private float calcular() {
            var pl = Minecraft.getInstance().player;
            if (pl == null) return 0;
            double d = Math.sqrt(pl.distanceToSqr(x, y, z));
            double a = Math.max(8, mesa.alcance());
            if (d >= a) return 0;
            // a tope en la pista (el primer tercio del alcance, 8 bloques como poco) y baja poco a poco hasta el borde
            double llano = Math.min(a - 1, Math.max(8, a * 0.35));
            double f = d < llano ? 1 : 1 - (d - llano) / (a - llano);
            return (float) (mesa.volumen() / 100.0 * f);
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound sonido, boolean bucle) {
            CompletableFuture<AudioStream> f = lib.getStream(sonido.getPath(), bucle);
            long t = saltar;
            // adelantada hasta por donde va y con el volumen subido (las canciones grabadas flojas se oían muy bajo)
            return f.thenApplyAsync(a -> new Fuerte(t < 20 ? a : Salto.saltar(a, t)), Util.backgroundExecutor());
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (mesa.isRemoved() || !mesa.sonando() || !mesa.pista().equals(pista) || mesa.inicio() != inicio) {
                stop();
                return;
            }
            volume = calcular();
        }
    }

    /** Un flujo de audio con el volumen subido por el {@link Compresor} (solo si es de 16 bits, que es lo que da el juego). */
    static final class Fuerte implements AudioStream {
        private final AudioStream base;
        private final Compresor compresor;

        Fuerte(AudioStream base) {
            this.base = base;
            var fmt = base.getFormat();
            compresor = fmt.getSampleSizeInBits() == 16 ? new Compresor(fmt.getSampleRate() * fmt.getChannels(), 2.8F) : null;
        }

        @Override
        public javax.sound.sampled.AudioFormat getFormat() {
            return base.getFormat();
        }

        @Override
        public java.nio.ByteBuffer read(int tam) throws java.io.IOException {
            java.nio.ByteBuffer b = base.read(tam);
            if (b == null || compresor == null) return b;
            java.nio.ShortBuffer s = b.duplicate().order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();
            for (int i = 0, n = s.limit(); i < n; i++) s.put(i, (short) Math.round(compresor.procesar(s.get(i) / 32768F) * 32767F));
            return b;
        }

        @Override
        public void close() throws java.io.IOException {
            base.close();
        }
    }

    /** Adelanta un flujo de audio: lee y tira los primeros {@code ticks} de canción. */
    static final class Salto {
        private Salto() {}

        static AudioStream saltar(AudioStream a, long ticks) {
            try {
                var fmt = a.getFormat();
                long bytes = (long) (ticks / 20.0 * fmt.getSampleRate()) * fmt.getFrameSize();
                while (bytes > 0) {
                    var b = a.read((int) Math.min(bytes, 1 << 16));
                    if (b == null || !b.hasRemaining()) break;
                    bytes -= b.remaining();
                }
            } catch (java.io.IOException e) {
                Miscelanea.LOGGER.debug("No se pudo adelantar la canción de la mesa de DJ", e);
            }
            return a;
        }
    }
}
