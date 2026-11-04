package com.miscelanea.client.estatica;

import com.miscelanea.estatica.EstaticaRed;
import com.miscelanea.estatica.EstaticaRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.BufferUtils;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/** Los sonidos que siguen a alguien o que pone el propio cliente: la tele del hombre de estática, el zumbido propio y la voz imitada. */
public final class EstaticaSonidos {
    private EstaticaSonidos() {}

    /** Nieve de televisión que sale de otro jugador: empieza a oírse a 10 bloques y a 2 suena fuerte. */
    public static class Bucle extends AbstractTickableSoundInstance {
        private final Player p;

        public Bucle(Player p) {
            super(EstaticaRegistro.BUCLE.get(), SoundSource.PLAYERS, RandomSource.create());
            this.p = p;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = false;
            this.volume = 0.001F;
            this.x = p.getX();
            this.y = p.getEyeY();
            this.z = p.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || p.isRemoved() || !EstaticaCliente.conEstatica(p.getId()) || p.distanceToSqr(mc.player) > 20 * 20) {
                stop();
                return;
            }
            x = p.getX();
            y = p.getY() + 1.1;
            z = p.getZ();
            double d = p.distanceTo(mc.player);
            float k = (float) Mth.clamp((10.0 - d) / 8.0, 0, 1);
            k = k * k * (3 - 2 * k);
            if (EstaticaCliente.bandera(p.getId(), EstaticaRed.Estado.DENTRO)) k = 0;
            else if (EstaticaCliente.bandera(p.getId(), EstaticaRed.Estado.FUNDIDO)) k *= 0.45F;
            volume = Math.max(0.001F, k * 0.9F);
            pitch = 0.95F + 0.1F * Mth.sin(p.tickCount * 0.05F);
        }
    }

    /** El zumbido que se oye uno mismo (flojo, en la cabeza). */
    public static class Propio extends AbstractTickableSoundInstance {
        public Propio() {
            super(EstaticaRegistro.PROPIA.get(), SoundSource.PLAYERS, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.12F;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (!EstaticaCliente.yo()) stop();
            else volume = EstaticaCine.localActiva() ? 0.3F : PantallaCliente.dentro() ? 0.35F : 0.12F;
        }
    }

    /** Una voz imitada: el audio lo pone el cliente (PCM 16 bits mono), no un archivo. */
    public static class Voz extends AbstractSoundInstance {
        private final short[] pcm;
        private final int sr;

        public Voz(double x, double y, double z, short[] pcm, int sr) {
            super(EstaticaRegistro.IMITACION.get(), SoundSource.PLAYERS, RandomSource.create());
            this.pcm = pcm;
            this.sr = sr;
            this.x = x;
            this.y = y;
            this.z = z;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 1.4F;
            this.relative = false;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound s, boolean bucle) {
            return CompletableFuture.completedFuture(new Pcm(pcm, sr));
        }
    }

    /** Flujo de audio desde un array en memoria. */
    static final class Pcm implements AudioStream {
        private final AudioFormat formato;
        private final ByteBuffer datos;

        Pcm(short[] pcm, int sr) {
            formato = new AudioFormat(sr, 16, 1, true, false);
            datos = BufferUtils.createByteBuffer(pcm.length * 2);
            for (short v : pcm) {
                datos.put((byte) (v & 0xFF));
                datos.put((byte) ((v >> 8) & 0xFF));
            }
            datos.flip();
        }

        @Override
        public AudioFormat getFormat() {
            return formato;
        }

        @Override
        @Nullable
        public ByteBuffer read(int n) {
            if (!datos.hasRemaining()) return null;
            int k = Math.min(n - (n & 1), datos.remaining());
            if (k <= 0) k = datos.remaining();
            ByteBuffer out = BufferUtils.createByteBuffer(k);
            int lim = datos.limit();
            datos.limit(datos.position() + k);
            out.put(datos);
            datos.limit(lim);
            out.flip();
            return out;
        }

        @Override
        public void close() {}
    }
}
