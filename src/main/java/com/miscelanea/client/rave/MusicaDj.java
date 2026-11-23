package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.rave.MesaDjBlockEntity;
import com.miscelanea.rave.Pistas;
import net.minecraft.client.Minecraft;
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
        var mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
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
        if (mejor == null) return 0;
        Pistas.Pista pi = Pistas.de(mejor.pista);
        float bpm = pi == null ? 120 : pi.bpm();
        float seg = (mc.level.getGameTime() - mejor.inicio + pt) / 20F;
        float fase = (seg * bpm / 60F) % 1F;
        float k = 1 - fase;
        return k * k * k;
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
        if (s != null && !s.isStopped() && s.pista.equals(be.pista()) && s.inicio == be.inicio()) return;
        if (s != null) s.parar();
        Pistas.Pista pi = Pistas.de(be.pista());
        if (pi == null) return;
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

        Sonido(MesaDjBlockEntity mesa, Pistas.Pista pi) {
            super(Pistas.evento(pi), SoundSource.RECORDS, RandomSource.create());
            this.mesa = mesa;
            this.pista = mesa.pista();
            this.inicio = mesa.inicio();
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
            // casi igual de fuerte en la pista (los primeros bloques) y baja hasta el borde del alcance
            double f = d < 6 ? 1 : 1 - (d - 6) / (a - 6);
            return (float) (mesa.volumen() / 100.0 * Math.pow(f, 1.3));
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
}
