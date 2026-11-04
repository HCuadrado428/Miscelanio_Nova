package com.miscelanea.client.tortura.muertes;

import com.miscelanea.client.tortura.EjecucionCliente;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.muertes.TipoMuerte;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Una cinemática de muerte, por datos: duración, pistas de fotogramas clave (cámara, cuerpo, pantalla y canales de los
 * aparatos; ver scratchpad/muertes.py → {@link MuertesDatos}) y sucesos con tiempo (sonidos, partículas, sangre,
 * destellos, temblores) que se disparan una vez en su tick ({@link #en}) o cada pocos ticks en un tramo ({@link #cada}).
 */
public final class Cinematica {
    public final TipoMuerte tipo;
    /** Tick de la muerte (= TipoMuerte.muerte) y fin de la cinemática en el cliente (la cámara sigue hasta reaparecer). */
    public final int muerte, fin;
    /** Distancia desde el centro a la que empieza a contar la colisión de la cámara (para que el propio aparato no la tape). */
    public final float margen;
    /** Desde qué tick la víctima se dibuja sin cabeza (-1 = nunca). */
    public final int sinCabeza;
    /**
     * Muere tumbada en el suelo (el veneno): al morir no se apaga la animación (se queda boca abajo, sin la vuelta de
     * lado de vanilla) y, mientras dura, el cliente de la víctima le fija el giro al del principio.
     */
    public boolean yace;
    final Map<String, Pista> pistas = new HashMap<>();
    final List<Suceso> sucesos = new ArrayList<>();
    final List<Emisor> emisores = new ArrayList<>();

    public interface Accion {
        void hacer(Ctx c);
    }

    record Suceso(int tick, Accion a) {}

    record Emisor(int desde, int hasta, int cada, Accion a) {}

    public Cinematica(TipoMuerte tipo, int muerte, int fin, float margen, int sinCabeza, String[] nombres, float[][][] k) {
        this.tipo = tipo;
        this.muerte = muerte;
        this.fin = fin;
        this.margen = margen;
        this.sinCabeza = sinCabeza;
        for (int i = 0; i < nombres.length; i++) pistas.put(nombres[i], new Pista(k[i]));
    }

    public Cinematica yace() {
        this.yace = true;
        return this;
    }

    public Cinematica en(int tick, Accion a) {
        sucesos.add(new Suceso(tick, a));
        return this;
    }

    /** Cada 'cada' ticks entre 'desde' y 'hasta' (incluidos); en la acción, {@link Ctx#k} va de 0 a 1 en el tramo. */
    public Cinematica cada(int desde, int hasta, int cada, Accion a) {
        emisores.add(new Emisor(desde, hasta, Math.max(1, cada), a));
        return this;
    }

    @Nullable
    public Pista pista(String n) {
        return pistas.get(n);
    }

    public boolean tiene(String n) {
        return pistas.containsKey(n);
    }

    /** Valor escalar de una pista (o 'def' si no la tiene). */
    public float x(String n, float t, float def) {
        Pista p = pistas.get(n);
        return p == null ? def : p.x(t);
    }

    // ------------------------------------------------------------------ contexto de los sucesos
    /** Lo que tiene a mano un suceso: la escena, la víctima (si se ve), si es el jugador local, y utilidades. */
    public static final class Ctx {
        public final Minecraft mc;
        public final ClientLevel level;
        @Nullable public final LivingEntity ent;
        public final MuertesCliente.Activa a;
        public final boolean local;
        public final RandomSource r;
        public int tick;
        /** Progreso dentro del tramo de un emisor (0..1). */
        public float k;

        Ctx(Minecraft mc, ClientLevel level, @Nullable LivingEntity ent, MuertesCliente.Activa a, boolean local) {
            this.mc = mc;
            this.level = level;
            this.ent = ent;
            this.a = a;
            this.local = local;
            this.r = level.getRandom();
        }

        public Vec3 foco() {
            return a.foco;
        }

        public Vec3 aux() {
            return a.aux;
        }

        /** Punto (derecha, arriba, delante) respecto al foco y al frente de la escena. */
        public Vec3 punto(double der, double arriba, double delante) {
            return a.punto(der, arriba, delante);
        }

        /** Los ojos de la víctima (o, si no se ve, 1,5 sobre el foco). */
        public Vec3 cabeza() {
            return ent != null ? ent.getEyePosition() : a.foco.add(0, 1.5, 0);
        }

        /** Dirección (derecha, arriba, delante) en el mundo. */
        public Vec3 dir(double der, double arriba, double delante) {
            return a.punto(der, arriba, delante).subtract(a.foco);
        }

        // ---- sonido
        public void sonido(SoundEvent s, Vec3 p, float vol, float tono) {
            level.playLocalSound(p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, tono, false);
        }

        /** Solo lo oye la víctima, "dentro de la cabeza" (latidos...). */
        public void sonidoVictima(SoundEvent s, float vol, float tono) {
            if (local) mc.getSoundManager().play(SimpleSoundInstance.forUI(s, tono, vol));
        }

        public void latido(float vol, float tono) {
            sonidoVictima(TorturaRegistro.TRAMPA_LATIDO.get(), vol, tono);
        }

        // ---- partículas
        @Nullable
        public Particle particula(ParticleOptions o, Vec3 p, double vx, double vy, double vz) {
            Particle q = mc.particleEngine.createParticle(o, p.x, p.y, p.z, 0, 0, 0);
            if (q != null) q.setParticleSpeed(vx, vy, vz);
            return q;
        }

        /** n partículas repartidas (gaussiana de 'disp') alrededor de p, con velocidad v y un poco de azar 'va'. */
        public void particulas(ParticleOptions o, Vec3 p, int n, double disp, Vec3 v, double va) {
            for (int i = 0; i < n; i++)
                particula(o, p.add(r.nextGaussian() * disp, r.nextGaussian() * disp, r.nextGaussian() * disp),
                        v.x + r.nextGaussian() * va, v.y + r.nextGaussian() * va, v.z + r.nextGaussian() * va);
        }

        /** Sangre: polvo rojo y trozos que caen, saliendo hacia 'dir' (más o menos abierta). */
        public void sangre(Vec3 p, int n, Vec3 dir, double abierta) {
            for (int i = 0; i < n; i++) {
                ParticleOptions o = i % 3 == 0 ? SANGRE : i % 3 == 1 ? TROZO_ROJO : TROZO_OSCURO;
                double f = 0.4 + r.nextDouble() * 0.8;
                particula(o, p.add(r.nextGaussian() * 0.05, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05),
                        dir.x * f + r.nextGaussian() * abierta, dir.y * f + r.nextGaussian() * abierta, dir.z * f + r.nextGaussian() * abierta);
            }
        }

        /** Una gota que cae. */
        public void gota(Vec3 p) {
            particula(r.nextBoolean() ? TROZO_ROJO : TROZO_OSCURO, p, r.nextGaussian() * 0.01, -0.02, r.nextGaussian() * 0.01);
        }

        /** Mancha de sangre en el primer bloque que encuentre desde p hacia dir (se borra sola; ver EjecucionCliente). */
        public void mancha(Vec3 p, Vec3 dir, float tam) {
            if (ent != null) EjecucionCliente.manchar(ent, p, dir, tam, level.getGameTime() + 2, r);
        }

        /** La cabeza revienta (como la trampa de oso). */
        public void reventon(Vec3 p) {
            if (ent != null) EjecucionCliente.reventon(ent, p);
        }

        /** Chorro de sangre que se va apagando (k de 1 a 0). */
        public void chorro(Vec3 p, float fuerza) {
            if (ent != null) EjecucionCliente.chorroEn(ent, p, fuerza);
        }

        // ---- pantalla
        /** Destello de color (ARGB) en la pantalla de la víctima que se apaga en 'dur' ticks. */
        public void destello(int argb, int dur) {
            if (local) MuertesCliente.destello(argb, dur);
        }

        /** Sacudida de cámara: la víctima entera; los de cerca, según la distancia. */
        public void temblor(float fuerza, int dur) {
            if (local) {
                com.miscelanea.client.armas.TemblorCliente.empezar(fuerza, dur);
                return;
            }
            if (mc.player == null) return;
            double d = mc.player.position().distanceTo(a.foco);
            if (d < 14) com.miscelanea.client.armas.TemblorCliente.empezar(fuerza * 0.6F * (float) (1 - d / 14), dur);
        }
    }

    public static final DustParticleOptions ESPUMA = new DustParticleOptions(new Vector3f(0.13F, 0.3F, 0.06F), 0.9F);
    public static final DustParticleOptions ESPUMA_OSCURA = new DustParticleOptions(new Vector3f(0.05F, 0.14F, 0.03F), 1.2F);
    public static final BlockParticleOption TROZO_VERDE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GREEN_TERRACOTTA.defaultBlockState());
    public static final DustParticleOptions SANGRE = new DustParticleOptions(new Vector3f(0.62F, 0.02F, 0.03F), 1.6F);
    public static final BlockParticleOption TROZO_ROJO = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
    public static final BlockParticleOption TROZO_OSCURO = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());

    static float suave(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }
}
