package com.miscelanea.client.tortura.muertes;

import com.miscelanea.tortura.aparatos.AparatosRegistro;
import com.miscelanea.voz.VozRegistro;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.miscelanea.tortura.muertes.TipoMuerte;
import com.miscelanea.tortura.saw.SawRegistro;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

import static com.miscelanea.client.tortura.muertes.MuertesDatos.*;

/**
 * Las cinemáticas de cada aparato: las pistas salen de {@link MuertesDatos} (scratchpad/muertes.py) y aquí se les añaden
 * los sucesos (sonidos, partículas, sangre, destellos...). Puntos con {@code c.punto(derecha, arriba, delante)} desde
 * el foco de la escena (ver Muertes: los pies, el centro de la mesa, los ojos...).
 */
public final class Cinematicas {
    private Cinematicas() {}

    private static final Map<TipoMuerte, Cinematica> TODAS = new EnumMap<>(TipoMuerte.class);

    @Nullable
    public static Cinematica de(TipoMuerte t) {
        if (TODAS.isEmpty()) crear();
        return TODAS.get(t);
    }

    private static void crear() {
        TODAS.put(TipoMuerte.DONCELLA, doncella());
        TODAS.put(TipoMuerte.POTRO, potro());
        TODAS.put(TipoMuerte.SILLA, silla());
        TODAS.put(TipoMuerte.PENDULO, pendulo());
        TODAS.put(TipoMuerte.INMERSION, inmersion());
        TODAS.put(TipoMuerte.COLLAR, collar());
        TODAS.put(TipoMuerte.CAJA, caja());
        TODAS.put(TipoMuerte.PARO, paro());
        TODAS.put(TipoMuerte.COLGADO, colgado());
        TODAS.put(TipoMuerte.VENENO, veneno());
    }

    private static boolean jugador(Cinematica.Ctx c) {
        return c.ent instanceof Player;
    }

    // ------------------------------------------------------------------ 1. doncella de hierro
    private static Cinematica doncella() {
        Cinematica m = new Cinematica(TipoMuerte.DONCELLA, DONCELLA_MUERTE, DONCELLA_FIN, DONCELLA_MARGEN, DONCELLA_SIN_CABEZA, DONCELLA_N, DONCELLA_K);
        m.en(DONCELLA_CHIRRIDO, c -> c.sonido(AparatosRegistro.DONCELLA_CHIRRIDO.get(), c.punto(0, 1.2, 0.5), 1.1F, 0.6F));
        m.en(DONCELLA_PORTAZO, c -> {
            Vec3 p = c.punto(0, 1.1, 0.5);
            c.sonido(AparatosRegistro.DONCELLA_CIERRE.get(), p, 1.7F, 0.82F);
            c.sonido(AparatosRegistro.DONCELLA_PINCHOS.get(), p, 1.5F, 0.75F);
            c.temblor(1.9F, 14);
            c.destello(0xE0A00000, 7);
            c.particulas(ParticleTypes.CRIT, p, 14, 0.4, c.dir(0, 0, 0.15), 0.12);
            // el golpe escupe sangre por la rendija
            for (int i = 0; i < 5; i++) c.sangre(c.punto(0, 0.5 + i * 0.3, 0.6), 4, c.dir(0, -0.02, 0.12), 0.04);
        });
        m.en(DONCELLA_PINCHOS, c -> c.sonido(AparatosRegistro.DONCELLA_PINCHOS.get(), c.punto(0, 1.1, 0.5), 1.0F, 0.6F));
        for (int t : new int[]{28, 44, 62, 80}) m.en(t, c -> c.latido(0.9F - c.tick / 200F, 0.85F - c.tick / 400F));
        // la sangre rezuma por la juntura de las puertas y gotea al suelo, primero mucha y luego cada vez menos
        m.cada(20, 104, 1, c -> {
            float ritmo = c.k < 0.25F ? 0.6F + c.k * 1.6F : 1F - (c.k - 0.25F) * 1.1F;
            if (c.r.nextFloat() > ritmo) return;
            Vec3 p = c.punto((c.r.nextFloat() - 0.5F) * 0.06, 0.15 + c.r.nextFloat() * 1.7, 0.6);
            c.particula(Cinematica.SANGRE, p, 0, -0.015, 0.004);
            if (c.r.nextFloat() < 0.35F) c.gota(c.punto((c.r.nextFloat() - 0.5F) * 0.1, 0.1 + c.r.nextFloat() * 0.3, 0.62));
        });
        m.en(26, c -> c.mancha(c.punto(0, 0.4, 0.75), new Vec3(0, -2, 0), 0.55F));
        m.en(42, c -> c.mancha(c.punto(0.1, 0.4, 0.85), new Vec3(0, -2, 0), 0.85F));
        m.en(60, c -> c.mancha(c.punto(-0.1, 0.4, 0.9), new Vec3(0, -2, 0), 1.15F));
        m.en(84, c -> c.mancha(c.punto(0, 0.4, 1.0), new Vec3(0, -2, 0), 1.35F));
        return m;
    }

    // ------------------------------------------------------------------ 2. potro
    private static Cinematica potro() {
        Cinematica m = new Cinematica(TipoMuerte.POTRO, POTRO_MUERTE, POTRO_FIN, POTRO_MARGEN, POTRO_SIN_CABEZA, POTRO_N, POTRO_K);
        for (int t : new int[]{POTRO_CARRACA1, POTRO_CARRACA2, POTRO_CARRACA3})
            m.en(t, c -> c.sonido(AparatosRegistro.POTRO_CARRACA.get(), c.punto(0, 0.3, 1.3), 1.0F, 0.75F + c.tick * 0.01F));
        m.en(POTRO_ESFUERZO, c -> c.sonido(AparatosRegistro.POTRO_CRUJIDO.get(), c.foco(), 0.9F, 0.7F));
        // la vuelta final, de golpe
        for (int i = 0; i < 3; i++)
            m.en(POTRO_FINAL + i * 2, c -> c.sonido(AparatosRegistro.POTRO_CARRACA.get(), c.punto(0, 0.3, 1.3), 1.3F, 1.0F + (c.tick - POTRO_FINAL) * 0.06F));
        m.en(POTRO_CRACK, c -> {
            c.sonido(AparatosRegistro.POTRO_CRUJIDO.get(), c.foco(), 2.0F, 0.5F);
            c.sonido(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, c.foco(), 0.7F, 1.7F);
            if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_GRITO.get(), c.punto(0, 0.3, -0.6), 1.2F, 0.9F);
            c.temblor(1.6F, 12);
            c.destello(0xC8FFFFFF, 5);
            // hombros y caderas
            for (double z : new double[]{-0.4, 0.35})
                for (double x : new double[]{-0.25, 0.25}) c.sangre(c.punto(x, 0.3, z), 6, new Vec3(0, 0.12, 0), 0.05);
            c.particulas(ParticleTypes.CRIT, c.punto(0, 0.35, 0), 12, 0.3, new Vec3(0, 0.1, 0), 0.15);
        });
        for (int t : new int[]{32, 46, 62}) m.en(t, c -> c.latido(0.8F - (c.tick - 32) / 120F, 0.8F - (c.tick - 32) / 200F));
        m.cada(26, 90, 4, c -> {
            if (c.r.nextFloat() < 0.6F - c.k * 0.5F) c.gota(c.punto((c.r.nextBoolean() ? -1 : 1) * 0.42, 0.0, -0.4 + c.r.nextFloat() * 0.8));
        });
        return m;
    }

    // ------------------------------------------------------------------ 3. silla eléctrica
    private static Cinematica silla() {
        Cinematica m = new Cinematica(TipoMuerte.SILLA, SILLA_MUERTE, SILLA_FIN, SILLA_MARGEN, SILLA_SIN_CABEZA, SILLA_N, SILLA_K);
        m.en(SILLA_ZUMBIDO, c -> c.sonido(AparatosRegistro.SILLA_PALANCA.get(), c.punto(0.8, 1.0, 0), 1.0F, 0.8F));
        for (int t : new int[]{SILLA_DESCARGA1, SILLA_DESCARGA2, SILLA_DESCARGA3})
            m.en(t, c -> {
                Vec3 h = c.punto(0, 1.45, 0);
                c.sonido(AparatosRegistro.SILLA_DESCARGA.get(), h, 1.7F, 0.85F - (c.tick - SILLA_DESCARGA1) * 0.004F);
                c.destello(0xA0E8F4FF, 4);
                c.temblor(1.0F, 10);
                c.particulas(ParticleTypes.ELECTRIC_SPARK, h, 24, 0.15, Vec3.ZERO, 0.35);
            });
        // chispas por todo el cuerpo y del casco mientras dura la descarga
        m.cada(SILLA_DESCARGA1, 68, 1, c -> {
            for (int i = 0; i < 3; i++)
                c.particula(ParticleTypes.ELECTRIC_SPARK, c.punto((c.r.nextFloat() - 0.5F) * 0.6, 0.6 + c.r.nextFloat() * 1.0, (c.r.nextFloat() - 0.5F) * 0.4),
                        c.r.nextGaussian() * 0.15, c.r.nextGaussian() * 0.15, c.r.nextGaussian() * 0.15);
        });
        m.cada(SILLA_DESCARGA1, 68, 2, c -> c.particulas(ParticleTypes.ELECTRIC_SPARK, c.punto(0, 1.85, 0), 3, 0.15, new Vec3(0, 0.1, 0), 0.25));
        m.cada(20, 120, 2, c -> c.particulas(ParticleTypes.SMOKE, c.punto(0, 1.55, 0), 1 + Math.round(c.k * 2), 0.12, new Vec3(0, 0.04, 0), 0.01));
        m.cada(60, 120, 5, c -> c.particulas(ParticleTypes.LARGE_SMOKE, c.punto(0, 1.5, 0), 1, 0.15, new Vec3(0, 0.03, 0), 0.01));
        m.cada(40, 62, 3, c -> c.particulas(ParticleTypes.SMALL_FLAME, c.punto(0, 1.6, 0), 2, 0.12, new Vec3(0, 0.02, 0), 0.01));
        m.en(SILLA_FUNDIDO, c -> {
            c.sonido(SoundEvents.REDSTONE_TORCH_BURNOUT, c.punto(0, 1.4, 0), 1.3F, 0.6F);
            c.particulas(ParticleTypes.LARGE_SMOKE, c.punto(0, 1.1, 0), 18, 0.3, new Vec3(0, 0.03, 0), 0.02);
        });
        m.en(SILLA_CHISPORROTEO, c -> c.sonido(SoundEvents.FIRE_EXTINGUISH, c.punto(0, 1.4, 0), 1.0F, 0.7F));
        m.cada(74, 114, 20, c -> c.sonido(SoundEvents.FIRE_AMBIENT, c.punto(0, 1.4, 0), 0.7F, 0.8F));
        m.en(SILLA_ESPASMO, c -> {
            c.sonido(AparatosRegistro.SILLA_DESCARGA.get(), c.punto(0, 1.2, 0), 0.5F, 1.3F);
            c.particulas(ParticleTypes.ELECTRIC_SPARK, c.punto(0, 1.1, 0), 8, 0.25, Vec3.ZERO, 0.2);
        });
        for (int t : new int[]{74, 90}) m.en(t, c -> c.latido(0.6F, 0.65F));
        return m;
    }

    // ------------------------------------------------------------------ 4. péndulo
    private static Cinematica pendulo() {
        Cinematica m = new Cinematica(TipoMuerte.PENDULO, PENDULO_MUERTE, PENDULO_FIN, PENDULO_MARGEN, PENDULO_SIN_CABEZA, PENDULO_N, PENDULO_K);
        m.en(PENDULO_TICTAC, c -> c.sonido(AparatosRegistro.PENDULO_TICTAC.get(), c.punto(0, 2.5, -0.2), 1.2F, 0.5F));
        // cámara lenta: latidos graves y lentos
        for (int t : new int[]{4, 20, 34, 46, 55}) m.en(t, c -> c.latido(0.7F + c.tick / 150F, 0.55F));
        m.en(PENDULO_SILBIDO, c -> c.sonido(AparatosRegistro.PENDULO_SILBIDO.get(), c.punto(0, 0.9, -0.3), 1.6F, 0.45F));
        m.en(PENDULO_CORTE, c -> {
            Vec3 pecho = c.punto(0, 0.32, -0.3);
            c.sonido(AparatosRegistro.PENDULO_CORTE.get(), pecho, 1.9F, 0.72F);
            if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_GRITO.get(), c.punto(0, 0.3, -0.6), 1.0F, 0.8F);
            c.temblor(1.5F, 14);
            c.destello(0xE8C00000, 6);
            // sale disparada a los dos lados del corte
            c.sangre(pecho, 30, c.dir(0.22, 0.12, 0), 0.06);
            c.sangre(pecho, 30, c.dir(-0.22, 0.12, 0), 0.06);
            c.particulas(ParticleTypes.CRIT, pecho, 10, 0.2, new Vec3(0, 0.1, 0), 0.2);
            for (int i = 0; i < 10; i++)
                c.mancha(pecho, new Vec3(c.r.nextGaussian() * 1.4, -1.2 - c.r.nextDouble(), c.r.nextGaussian() * 1.4), 0.3F + c.r.nextFloat() * 0.4F);
        });
        m.cada(PENDULO_CORTE + 1, 86, 1, c -> c.chorro(c.punto(0, 0.3, -0.3), 1F - c.k));
        m.cada(62, 125, 3, c -> {
            if (c.r.nextFloat() < 0.8F - c.k * 0.6F) c.gota(c.punto((c.r.nextBoolean() ? -1 : 1) * 0.47, -0.02, -0.5 + c.r.nextFloat() * 0.5));
        });
        m.en(80, c -> c.mancha(c.punto(0.5, 0, -0.3), new Vec3(0, -2, 0), 0.9F));
        return m;
    }

    // ------------------------------------------------------------------ 5. silla de inmersión
    private static Cinematica inmersion() {
        Cinematica m = new Cinematica(TipoMuerte.INMERSION, INMERSION_MUERTE, INMERSION_FIN, INMERSION_MARGEN, INMERSION_SIN_CABEZA, INMERSION_N, INMERSION_K);
        int[] b = {INMERSION_BURBUJAS1, INMERSION_BURBUJAS2, INMERSION_BURBUJAS3};
        for (int i = 0; i < b.length; i++) {
            float vol = 1.0F - i * 0.15F;
            m.en(b[i], c -> c.sonido(AparatosRegistro.INMERSION_BURBUJAS.get(), c.foco(), vol, 0.9F));
        }
        // burbujas por la boca: muchas al principio, cada vez menos
        m.cada(0, 36, 1, c -> c.particulas(ParticleTypes.BUBBLE, c.punto(0, -0.1, 0.2), 3, 0.05, new Vec3(0, 0.12, 0), 0.04));
        m.cada(37, 64, 3, c -> c.particulas(ParticleTypes.BUBBLE, c.punto(0, -0.1, 0.2), 1, 0.04, new Vec3(0, 0.1, 0), 0.03));
        m.en(INMERSION_ULTIMO, c -> {
            c.sonido(AparatosRegistro.INMERSION_BURBUJAS.get(), c.foco(), 1.3F, 0.6F);
            c.particulas(ParticleTypes.BUBBLE, c.punto(0, -0.1, 0.2), 30, 0.08, new Vec3(0, 0.18, 0), 0.06);
            c.temblor(0.6F, 8);
        });
        m.cada(72, 84, 6, c -> c.particula(ParticleTypes.BUBBLE, c.punto(0, -0.1, 0.2), 0, 0.08, 0));
        for (int t : new int[]{40, 56, 76}) m.en(t, c -> c.latido(0.75F, 0.5F));
        return m;
    }

    // ------------------------------------------------------------------ 6. collar explosivo
    private static Cinematica collar() {
        Cinematica m = new Cinematica(TipoMuerte.COLLAR, COLLAR_MUERTE, COLLAR_FIN, COLLAR_MARGEN, COLLAR_SIN_CABEZA, COLLAR_N, COLLAR_K);
        for (int i = 0; i < COLLAR_PITIDOS.length; i++) {
            int n = i;
            m.en(COLLAR_PITIDOS[i], c -> {
                c.sonido(EfectosRegistro.COLLAR_PITIDO.get(), c.cabeza().subtract(0, 0.3, 0), 1.0F + n * 0.04F, 1.0F + n * 0.06F);
                c.destello(0x40FF0000 + (n * 6 << 24), 3);
            });
        }
        m.en(COLLAR_TONO, c -> c.sonido(EfectosRegistro.COLLAR_TONO.get(), c.cabeza().subtract(0, 0.3, 0), 1.6F, 1.0F));
        for (int t : new int[]{5, 30, 52}) m.en(t, c -> {
            if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_JADEO.get(), c.cabeza(), 0.9F, 1.1F);
        });
        m.cada(0, 70, 6, c -> c.latido(0.7F + c.k * 0.4F, 0.9F + c.k * 0.3F));
        m.en(COLLAR_ESTALLIDO, c -> {
            c.reventon(c.cabeza().subtract(0, 0.12, 0));
            c.destello(0xFFFFFFFF, 3);
            c.destello(0xD0C00000, 9);
        });
        m.cada(COLLAR_ESTALLIDO + 1, COLLAR_ESTALLIDO + 40, 1, c -> {
            if (c.ent != null) c.chorro(c.ent.getEyePosition().subtract(0, 0.35, 0), 1F - c.k);
        });
        return m;
    }

    // ------------------------------------------------------------------ 7. caja de agua
    private static Cinematica caja() {
        Cinematica m = new Cinematica(TipoMuerte.CAJA, CAJA_MUERTE, CAJA_FIN, CAJA_MARGEN, CAJA_SIN_CABEZA, CAJA_N, CAJA_K);
        for (int i = 0; i < CAJA_GOLPES.length; i++) {
            float f = 1F - i * 0.09F;
            m.en(CAJA_GOLPES[i], c -> {
                c.sonido(SoundEvents.GLASS_HIT, c.cabeza(), 1.3F * f, 0.7F + c.r.nextFloat() * 0.1F);
                c.sonido(SoundEvents.GLASS_HIT, c.cabeza(), 0.8F * f, 0.55F);
                c.particulas(ParticleTypes.BUBBLE, c.cabeza(), 4, 0.08, new Vec3(0, 0.1, 0), 0.05);
                if (c.local) c.temblor(0.5F * f, 5);
            });
        }
        m.cada(0, 50, 2, c -> c.particulas(ParticleTypes.BUBBLE, c.cabeza().subtract(0, 0.12, 0), 2, 0.05, new Vec3(0, 0.1, 0), 0.03));
        m.cada(51, 76, 4, c -> c.particula(ParticleTypes.BUBBLE, c.cabeza().subtract(0, 0.12, 0), 0, 0.08, 0));
        m.en(CAJA_ULTIMO, c -> {
            c.sonido(SawRegistro.CAJA_BURBUJAS.get(), c.cabeza(), 1.2F, 0.6F);
            c.particulas(ParticleTypes.BUBBLE, c.cabeza().subtract(0, 0.12, 0), 25, 0.08, new Vec3(0, 0.15, 0), 0.05);
        });
        for (int t : new int[]{44, 60, 78, 94}) m.en(t, c -> c.latido(0.8F - (c.tick - 44) / 150F, 0.7F - (c.tick - 44) / 250F));
        return m;
    }

    // ------------------------------------------------------------------ 8. paro cardíaco (silla de interrogatorio)
    private static Cinematica paro() {
        Cinematica m = new Cinematica(TipoMuerte.PARO, PARO_MUERTE, PARO_FIN, PARO_MARGEN, PARO_SIN_CABEZA, PARO_N, PARO_K);
        int[] p = {PARO_PITIDO1, PARO_PITIDO2, PARO_PITIDO3};
        for (int i = 0; i < p.length; i++) {
            float tono = 1.0F - i * 0.06F;
            m.en(p[i], c -> {
                c.sonido(AparatosRegistro.MONITOR_PITIDO.get(), c.aux(), 0.9F, tono);
                c.latido(0.5F, 0.7F);
            });
        }
        m.en(3, c -> { if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_JADEO.get(), c.punto(0, 1.4, 0), 0.7F, 0.8F); });
        m.en(14, c -> { if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_JADEO.get(), c.punto(0, 1.4, 0), 0.45F, 0.7F); });
        m.en(PARO_PLANO, c -> {
            c.sonido(AparatosRegistro.MONITOR_PLANO.get(), c.aux(), 1.4F, 1.0F);
            c.destello(0x60000000, 20);
        });
        return m;
    }

    // ------------------------------------------------------------------ 9. colgado boca abajo
    private static Cinematica colgado() {
        Cinematica m = new Cinematica(TipoMuerte.COLGADO, COLGADO_MUERTE, COLGADO_FIN, COLGADO_MARGEN, COLGADO_SIN_CABEZA, COLGADO_N, COLGADO_K);
        int[] cu = {COLGADO_CUERDA1, COLGADO_CUERDA2, COLGADO_CUERDA3, COLGADO_CUERDA4, COLGADO_CUERDA5};
        for (int i = 0; i < cu.length; i++) {
            float vol = 0.9F - i * 0.08F, tono = 0.9F - i * 0.07F;
            m.en(cu[i], c -> c.sonido(AparatosRegistro.COLGADO_CUERDA.get(), c.punto(0, 1.1, 0), vol, tono));
        }
        int[] ja = {COLGADO_JADEO1, COLGADO_JADEO2, COLGADO_JADEO3};
        for (int i = 0; i < ja.length; i++) {
            float vol = 0.75F - i * 0.2F;
            m.en(ja[i], c -> { if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_JADEO.get(), c.punto(0, -0.8, 0), vol, 0.85F); });
        }
        for (int t : new int[]{10, 30, 52, 74}) m.en(t, c -> c.latido(0.8F - c.tick / 160F, 0.75F - c.tick / 300F));
        // la sangre que se le ha ido a la cabeza le gotea por la nariz
        m.cada(20, 115, 5, c -> {
            if (c.ent != null && c.r.nextFloat() < 0.7F) c.gota(c.ent.getBoundingBox().getCenter().subtract(0, c.ent.getBbHeight() * 0.5 - 0.05, 0));
        });
        return m;
    }

    // ------------------------------------------------------------------ 10. veneno de Dream (bebido, en cualquier sitio)
    /** La boca de la víctima (pista "boca", sacada del esqueleto de la vista previa), o sus ojos si no la hay. */
    private static Vec3 boca(Cinematica.Ctx c) {
        Pista p = c.a.def.pista("boca");
        if (p == null) return c.cabeza();
        float t = c.tick;
        return c.punto(p.en(t, 0), p.en(t, 1), p.en(t, 2));
    }

    /** Espuma verde oscura que le sale por la boca y cae (n partículas, 'fuerza' para las arcadas). */
    private static void espuma(Cinematica.Ctx c, int n, double fuerza) {
        Vec3 b = boca(c);
        Vec3 f = c.dir(0, 0, 1);
        for (int i = 0; i < n; i++) {
            double r = c.r.nextDouble();
            Vec3 p = b.add(c.r.nextGaussian() * 0.025, c.r.nextGaussian() * 0.02, c.r.nextGaussian() * 0.025);
            if (r < 0.45) c.particula(Cinematica.ESPUMA, p, f.x * 0.01 * fuerza, -0.01, f.z * 0.01 * fuerza);
            else if (r < 0.7) c.particula(Cinematica.ESPUMA_OSCURA, p, f.x * 0.008 * fuerza, -0.012, f.z * 0.008 * fuerza);
            else if (r < 0.88) c.particula(Cinematica.TROZO_VERDE, p, f.x * 0.05 * fuerza + c.r.nextGaussian() * 0.01, 0.02 * fuerza, f.z * 0.05 * fuerza + c.r.nextGaussian() * 0.01);
            else c.particula(ParticleTypes.ITEM_SLIME, p, f.x * 0.04 * fuerza, 0.01, f.z * 0.04 * fuerza);
        }
    }

    private static Cinematica veneno() {
        Cinematica m = new Cinematica(TipoMuerte.VENENO, VENENO_MUERTE, VENENO_FIN, VENENO_MARGEN, VENENO_SIN_CABEZA, VENENO_N, VENENO_K);
        if (VENENO_YACE) m.yace();
        // baja la botella: el último trago le quema
        m.en(VENENO_BAJA, c -> {
            c.sonido(SoundEvents.GENERIC_DRINK, boca(c), 0.6F, 0.6F);
            c.destello(0x502E5A1C, 8);
        });
        // tos (con la mano en la boca) y la espuma que empieza
        for (int i = 0; i < VENENO_TOSES.length; i++) {
            float f = 1F + i * 0.15F;
            m.en(VENENO_TOSES[i], c -> {
                if (jugador(c)) c.sonido(AparatosRegistro.INMERSION_TOS.get(), boca(c), 0.9F * f, 0.95F - c.tick * 0.006F);
                else c.sonido(SoundEvents.PLAYER_HURT_DROWN, boca(c), 0.8F, 0.7F);
                c.temblor(0.25F * f, 5);
                espuma(c, 3, 1.5);
            });
        }
        // se agarra el cuello: arcada y espuma
        m.en(VENENO_AGARRA, c -> {
            if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_JADEO.get(), boca(c), 0.8F, 0.75F);
            c.destello(0x60143C0A, 10);
        });
        m.cada(VENENO_ESPUMA, VENENO_SUELO + 20, 2, c -> {
            float ritmo = c.tick < VENENO_RODILLAS ? 0.6F : c.tick < VENENO_SUELO ? 1F : 0.55F - c.k * 0.4F;
            if (c.r.nextFloat() < ritmo) espuma(c, 1 + (c.tick > VENENO_RODILLAS && c.tick < VENENO_SUELO ? 2 : 1), 0.6);
        });
        m.en(VENENO_RODILLAS - 4, c -> c.sonido(VozRegistro.VOMITO.get(), boca(c), 0.55F, 0.7F));
        m.en(VENENO_RODILLAS, c -> {
            c.sonido(SoundEvents.PLAYER_SMALL_FALL, c.foco(), 1.0F, 0.8F);
            c.sonido(SoundEvents.ARMOR_EQUIP_GENERIC, c.foco(), 0.6F, 0.6F);
            c.temblor(0.6F, 6);
        });
        // convulsiones de rodillas: arcadas con más espuma
        for (int t : new int[]{63, 70}) m.en(t, c -> {
            if (jugador(c)) c.sonido(AparatosRegistro.INTERROGATORIO_JADEO.get(), boca(c), 0.6F, 0.6F);
            espuma(c, 8, 2.0);
        });
        m.en(VENENO_SUELO, c -> {
            c.sonido(SoundEvents.PLAYER_BIG_FALL, c.foco(), 1.0F, 0.75F);
            c.sonido(SoundEvents.GENERIC_HURT, c.foco(), 0.4F, 0.5F);
            c.temblor(1.0F, 8);
            c.particulas(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    c.level.getBlockState(net.minecraft.core.BlockPos.containing(c.foco()).below())), c.punto(0, 0.05, 0.6), 14, 0.35, new Vec3(0, 0.05, 0), 0.05);
            espuma(c, 10, 0.8);
        });
        // latidos cada vez más lentos y graves (solo la víctima) y el pitido en los oídos
        for (int i = 0; i < VENENO_LATIDOS.length; i++) {
            float k = i / (float) (VENENO_LATIDOS.length - 1);
            m.en(VENENO_LATIDOS[i], c -> c.latido(0.95F - k * 0.25F, 1.15F - k * 0.6F));
        }
        m.en(VENENO_PITIDO, c -> c.sonidoVictima(com.miscelanea.tortura.efectos.EfectosRegistro.MIEDO_PITIDO.get(), 0.35F, 1.0F));
        m.en(VENENO_CAE, c -> c.sonidoVictima(com.miscelanea.tortura.efectos.EfectosRegistro.MIEDO_PITIDO.get(), 0.6F, 0.9F));
        return m;
    }
}
