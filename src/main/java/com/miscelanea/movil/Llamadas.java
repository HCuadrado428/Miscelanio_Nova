package com.miscelanea.movil;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Llamadas y videollamadas (en memoria; si se reinicia el servidor se cortan). La voz la lleva Simple Voice Chat a través
 * de {@link MovilVoz#SOCIOS}; el vídeo son imágenes JPEG pequeñas que el servidor reenvía SOLO al otro de la llamada,
 * con límite de imágenes por segundo y de tamaño ({@link MovilConfig}).
 * <p>
 * Ancho de banda del vídeo, por defecto (72x128, 5 fps, JPEG calidad ~55): 2-4 KB por imagen → 10-20 KB/s por sentido
 * (el servidor recibe y reenvía lo mismo: ~40 KB/s por videollamada en total). Con el máximo (144x256, 12 fps) puede
 * llegar a ~150 KB/s por sentido.
 */
public final class Llamadas {
    private Llamadas() {}

    public static final int FIN_COLGADA = 0, FIN_RECHAZADA = 1, FIN_SIN_RESPUESTA = 2, FIN_CORTADA = 3;

    static final class Llamada {
        final UUID a, b;
        final boolean video;
        boolean activa;
        long creada, inicio;
        long ultA, ultB;

        Llamada(UUID a, UUID b, boolean video, long ahora) {
            this.a = a;
            this.b = b;
            this.video = video;
            this.creada = ahora;
        }

        UUID otro(UUID u) {
            return u.equals(a) ? b : a;
        }
    }

    private static final Map<UUID, Llamada> EN = new HashMap<>();

    public static boolean ocupado(UUID u) {
        return EN.containsKey(u);
    }

    private static void estado(ServerPlayer p, int estado, UUID otro, String nombre, boolean video, int motivo) {
        MovilRed.aJugador(p, MovilRed.S_LLAMADA, b -> {
            b.writeByte(estado);
            b.writeUUID(otro);
            b.writeUtf(nombre);
            b.writeBoolean(video);
            b.writeByte(motivo);
            b.writeBoolean(MovilConfig.video());
            b.writeVarInt(MovilConfig.videoFps());
            b.writeVarInt(MovilConfig.videoAncho());
            b.writeVarInt(MovilConfig.videoAlto());
            b.writeVarInt(MovilConfig.videoMaxBytes());
        });
    }

    static void llamar(ServerPlayer yo, UUID otroId, boolean video) {
        if (otroId.equals(yo.getUUID()) || ocupado(yo.getUUID())) return;
        MinecraftServer s = yo.server;
        String nombreOtro = MovilServidor.nombreDe(s, otroId);
        if (!MovilVoz.disponible()) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.sin_voz_servidor", "");
            return;
        }
        if (!MovilVoz.conectado(yo.getUUID())) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.sin_voz_tu", "");
            return;
        }
        ServerPlayer otro = s.getPlayerList().getPlayer(otroId);
        if (otro == null || !MovilServidor.tieneMovil(otro)) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.no_disponible", nombreOtro);
            registrarAmbos(s, yo.getUUID(), otroId, false, 0, video);
            return;
        }
        if (ocupado(otroId)) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.ocupado", nombreOtro);
            return;
        }
        if (!MovilVoz.conectado(otroId)) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.sin_voz_otro", nombreOtro);
            return;
        }
        if (!cobertura(yo, otro)) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.sin_cobertura", nombreOtro);
            return;
        }
        boolean v = video && MovilConfig.video();
        Llamada l = new Llamada(yo.getUUID(), otroId, v, s.getTickCount());
        EN.put(l.a, l);
        EN.put(l.b, l);
        estado(yo, MovilRed.LL_SALIENTE, otroId, nombreOtro, v, 0);
        estado(otro, MovilRed.LL_ENTRANTE, yo.getUUID(), MovilServidor.nombre(yo), v, 0);
        timbre(s, l);
    }

    private static boolean cobertura(ServerPlayer a, ServerPlayer b) {
        if (MovilConfig.sinLimite()) return true;
        if (a.level() != b.level()) return MovilConfig.entreDimensiones();
        double d = MovilConfig.distancia();
        return a.distanceToSqr(b) <= d * d;
    }

    /** El timbre suena en el mundo, donde está quien recibe la llamada (lo oyen también los de alrededor). */
    private static void timbre(MinecraftServer s, Llamada l) {
        ServerPlayer o = s.getPlayerList().getPlayer(l.b);
        if (o == null) return;
        MovilDatos.Perfil pf = MovilServidor.datos(s).perfil(l.b, null);
        if (pf.silencio)
            o.level().playSound(null, o.getX(), o.getY(), o.getZ(), MovilRegistro.VIBRAR.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
        else
            o.level().playSound(null, o.getX(), o.getY(), o.getZ(), MovilRegistro.TONO[Math.floorMod(pf.tono, MovilRegistro.TONOS)].get(),
                    SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    static void contestar(ServerPlayer yo, boolean si) {
        Llamada l = EN.get(yo.getUUID());
        if (l == null || l.activa || !l.b.equals(yo.getUUID())) return;
        if (!si) {
            terminar(yo.server, l, FIN_RECHAZADA);
            return;
        }
        MinecraftServer s = yo.server;
        ServerPlayer a = s.getPlayerList().getPlayer(l.a);
        if (a == null) {
            terminar(s, l, FIN_CORTADA);
            return;
        }
        l.activa = true;
        l.inicio = s.getTickCount();
        MovilVoz.SOCIOS.put(l.a, l.b);
        MovilVoz.SOCIOS.put(l.b, l.a);
        estado(a, MovilRed.LL_ACTIVA, l.b, MovilServidor.nombre(yo), l.video, 0);
        estado(yo, MovilRed.LL_ACTIVA, l.a, MovilServidor.nombre(a), l.video, 0);
    }

    static void colgar(ServerPlayer yo) {
        Llamada l = EN.get(yo.getUUID());
        if (l == null) return;
        terminar(yo.server, l, l.activa ? FIN_COLGADA : yo.getUUID().equals(l.b) ? FIN_RECHAZADA : FIN_COLGADA);
    }

    static void salir(ServerPlayer yo) {
        Llamada l = EN.get(yo.getUUID());
        if (l != null) terminar(yo.server, l, l.activa ? FIN_CORTADA : FIN_SIN_RESPUESTA);
    }

    private static void terminar(MinecraftServer s, Llamada l, int motivo) {
        EN.remove(l.a);
        EN.remove(l.b);
        MovilVoz.SOCIOS.remove(l.a);
        MovilVoz.SOCIOS.remove(l.b);
        int dur = l.activa ? (int) ((s.getTickCount() - l.inicio) / 20) : 0;
        MovilDatos d = MovilServidor.datos(s);
        long ahora = System.currentTimeMillis();
        if (l.activa) {
            d.registrar(l.a, new MovilDatos.Registro(l.b, ahora, MovilDatos.R_SALIENTE, dur, l.video));
            d.registrar(l.b, new MovilDatos.Registro(l.a, ahora, MovilDatos.R_ENTRANTE, dur, l.video));
        } else if (motivo == FIN_RECHAZADA) {
            d.registrar(l.a, new MovilDatos.Registro(l.b, ahora, MovilDatos.R_SIN_RESPUESTA, 0, l.video));
            d.registrar(l.b, new MovilDatos.Registro(l.a, ahora, MovilDatos.R_ENTRANTE, 0, l.video));
        } else {
            registrarAmbos(s, l.a, l.b, false, 0, l.video);
        }
        for (UUID u : new UUID[]{l.a, l.b}) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p != null) {
                UUID o = l.otro(u);
                estado(p, MovilRed.LL_FIN, o, MovilServidor.nombreDe(s, o), l.video, motivo);
                MovilServidor.sync(p);
            }
        }
    }

    /** Llamada que no se cogió: perdida para quien la recibe, sin respuesta para quien llama. */
    private static void registrarAmbos(MinecraftServer s, UUID a, UUID b, boolean activa, int dur, boolean video) {
        MovilDatos d = MovilServidor.datos(s);
        long ahora = System.currentTimeMillis();
        d.registrar(a, new MovilDatos.Registro(b, ahora, MovilDatos.R_SIN_RESPUESTA, 0, video));
        d.registrar(b, new MovilDatos.Registro(a, ahora, MovilDatos.R_PERDIDA, 0, video));
    }

    static void tick(MinecraftServer s) {
        if (EN.isEmpty()) return;
        long ahora = s.getTickCount();
        List<Llamada> todas = new ArrayList<>();
        for (Llamada l : EN.values()) if (!todas.contains(l)) todas.add(l);
        for (Llamada l : todas) {
            ServerPlayer a = s.getPlayerList().getPlayer(l.a), b = s.getPlayerList().getPlayer(l.b);
            if (a == null || b == null) {
                terminar(s, l, l.activa ? FIN_CORTADA : FIN_SIN_RESPUESTA);
                continue;
            }
            if (!l.activa) {
                if (ahora - l.creada > MovilConfig.timbreTicks()) {
                    terminar(s, l, FIN_SIN_RESPUESTA);
                    continue;
                }
                if ((ahora - l.creada) % 50 == 0) timbre(s, l);
            }
            if (ahora % 20 == 0 && (!MovilServidor.tieneMovil(a) || !MovilServidor.tieneMovil(b) || !cobertura(a, b)))
                terminar(s, l, FIN_CORTADA);
        }
    }

    /** Imagen de vídeo de un lado de la llamada → al otro (vacía = cámara apagada). */
    static void video(ServerPlayer yo, byte[] img) {
        Llamada l = EN.get(yo.getUUID());
        if (l == null || !l.activa || !MovilConfig.video()) return;
        if (img.length > 0) {
            if (img.length > MovilConfig.videoMaxBytes() || !Fotos.valida(img, MovilConfig.videoMaxBytes())) return;
            long ms = System.currentTimeMillis(), min = (long) (1000.0 / MovilConfig.videoFps() * 0.7);
            boolean esA = yo.getUUID().equals(l.a);
            long ult = esA ? l.ultA : l.ultB;
            if (ms - ult < min) return;
            if (esA) l.ultA = ms;
            else l.ultB = ms;
        }
        ServerPlayer otro = yo.server.getPlayerList().getPlayer(l.otro(yo.getUUID()));
        if (otro != null) MovilRed.aJugador(otro, MovilRed.S_VIDEO, b -> b.writeByteArray(img));
    }
}
