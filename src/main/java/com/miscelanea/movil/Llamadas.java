package com.miscelanea.movil;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Llamadas y videollamadas (en memoria; si se reinicia el servidor se cortan): individuales o de grupo (hasta
 * {@link #MAX_GRUPO} personas), con llamada en espera, número oculto, silenciar y altavoz.
 * <p>
 * Cada jugador tiene como mucho dos llamadas: la {@link #ACTUAL} (la que oye, la que está sonando o la que está haciendo) y
 * la {@link #SEGUNDA}: otra que le está entrando mientras habla (llamada en espera) o la que ha dejado en espera al coger
 * otra. La voz la lleva Simple Voice Chat a través de {@link MovilVoz#SOCIOS} (solo entre los que tienen esa llamada como
 * actual); el vídeo son imágenes JPEG pequeñas que el servidor reenvía SOLO a los de la llamada.
 */
public final class Llamadas {
    private Llamadas() {}

    public static final int FIN_COLGADA = 0, FIN_RECHAZADA = 1, FIN_SIN_RESPUESTA = 2, FIN_CORTADA = 3;
    /** Personas como máximo en una llamada de grupo (contando a quien llama). */
    public static final int MAX_GRUPO = 4;
    /** Estado de cada participante en {@link MovilRed#S_LLAMADA}. */
    public static final int P_SONANDO = 0, P_DENTRO = 1, P_ESPERA = 2;
    /** UUID que ve quien recibe una llamada con número oculto. */
    public static final UUID OCULTO = new UUID(0, 0);

    static final class Llamada {
        final UUID quien;
        final boolean video, oculto;
        final int chat;
        final String nombreGrupo;
        /** En orden: quien llama primero. */
        final List<UUID> miembros = new ArrayList<>();
        final Set<UUID> dentro = new LinkedHashSet<>(), sonando = new LinkedHashSet<>();
        final Map<UUID, Long> entro = new HashMap<>(), ultVideo = new HashMap<>();
        final long creada;
        long inicio = -1;

        Llamada(UUID quien, boolean video, boolean oculto, int chat, String nombreGrupo, long ahora) {
            this.quien = quien;
            this.video = video;
            this.oculto = oculto;
            this.chat = chat;
            this.nombreGrupo = nombreGrupo;
            this.creada = ahora;
        }

        boolean activa() {
            return inicio >= 0;
        }

        boolean grupo() {
            return chat >= 0;
        }

        /** El "otro" para el registro de llamadas de u. */
        UUID otroPara(UUID u) {
            if (!u.equals(quien)) return oculto ? OCULTO : quien;
            for (UUID m : miembros) if (!m.equals(u)) return m;
            return quien;
        }
    }

    private static final Map<UUID, Llamada> ACTUAL = new HashMap<>(), SEGUNDA = new HashMap<>();
    /** Motivo del último fin para cada uno (para el paquete de fin). */
    private static final Map<UUID, Integer> MOTIVO = new HashMap<>();

    public static boolean ocupado(UUID u) {
        return ACTUAL.containsKey(u);
    }

    /** Se le puede llamar: libre, o hablando y sin otra llamada en espera (le entrará como llamada en espera). */
    private static boolean admite(UUID u) {
        Llamada a = ACTUAL.get(u);
        if (a == null) return true;
        return a.dentro.contains(u) && !SEGUNDA.containsKey(u);
    }

    // ------------------------------------------------------------------ estado al cliente
    private static String nombreVisible(MinecraftServer s, Llamada l, UUID u, UUID para) {
        if (l.oculto && u.equals(l.quien) && !para.equals(l.quien)) return "";
        return MovilServidor.nombreDe(s, u);
    }

    private static int estadoDe(Llamada l, UUID u) {
        if (l.sonando.contains(u)) return MovilRed.LL_ENTRANTE;
        if (!l.activa()) return MovilRed.LL_SALIENTE;
        return MovilRed.LL_ACTIVA;
    }

    /** Manda a u cómo están sus llamadas (o el fin, si ya no tiene ninguna). */
    private static void enviar(MinecraftServer s, UUID u) {
        ServerPlayer p = s.getPlayerList().getPlayer(u);
        if (p == null) return;
        Llamada l = ACTUAL.get(u), dos = SEGUNDA.get(u);
        int motivo = MOTIVO.getOrDefault(u, FIN_COLGADA);
        MovilRed.aJugador(p, MovilRed.S_LLAMADA, b -> {
            b.writeByte(l == null ? MovilRed.LL_FIN : estadoDe(l, u));
            b.writeByte(motivo);
            b.writeBoolean(l != null && l.video);
            b.writeBoolean(l != null && l.grupo());
            b.writeUtf(l == null ? "" : l.nombreGrupo);
            b.writeBoolean(l != null && l.oculto && u.equals(l.quien));
            List<UUID> otros = new ArrayList<>();
            if (l != null) for (UUID m : l.miembros) if (!m.equals(u) && (l.dentro.contains(m) || l.sonando.contains(m))) otros.add(m);
            b.writeVarInt(otros.size());
            for (UUID m : otros) {
                boolean ocultar = l.oculto && m.equals(l.quien) && !u.equals(l.quien);
                b.writeUUID(ocultar ? OCULTO : m);
                b.writeUtf(nombreVisible(s, l, m, u));
                b.writeByte(l.sonando.contains(m) ? P_SONANDO : ACTUAL.get(m) == l ? P_DENTRO : P_ESPERA);
            }
            b.writeBoolean(MovilVoz.MUTE.contains(u));
            b.writeBoolean(MovilVoz.ALTAVOZ.contains(u));
            b.writeBoolean(dos != null);
            if (dos != null) {
                boolean entrante = dos.sonando.contains(u);
                b.writeByte(entrante ? MovilRed.LL_ENTRANTE : MovilRed.LL_ESPERA);
                UUID cara = entrante ? (dos.oculto ? OCULTO : dos.quien) : dos.otroPara(u);
                b.writeUUID(cara);
                b.writeUtf(dos.grupo() ? dos.nombreGrupo : cara.equals(OCULTO) ? "" : MovilServidor.nombreDe(s, cara));
                b.writeBoolean(dos.video);
            }
            b.writeBoolean(MovilConfig.video());
            b.writeVarInt(MovilConfig.videoFps());
            b.writeVarInt(MovilConfig.videoAncho());
            b.writeVarInt(MovilConfig.videoAlto());
            b.writeVarInt(MovilConfig.videoMaxBytes());
        });
    }

    private static void enviarTodos(MinecraftServer s, Llamada l) {
        for (UUID m : l.miembros) enviar(s, m);
    }

    /** Recalcula quién oye a quién (solo los que tienen la llamada como actual y están dentro). */
    private static void recalcularVoz() {
        Map<UUID, Set<UUID>> nuevo = new HashMap<>();
        Set<Llamada> vistas = new HashSet<>(ACTUAL.values());
        for (Llamada l : vistas) {
            List<UUID> oyen = new ArrayList<>();
            for (UUID u : l.dentro) if (ACTUAL.get(u) == l) oyen.add(u);
            if (oyen.size() < 2) continue;
            for (UUID u : oyen) {
                Set<UUID> otros = new HashSet<>(oyen);
                otros.remove(u);
                nuevo.put(u, Set.copyOf(otros));
            }
        }
        MovilVoz.SOCIOS.keySet().retainAll(nuevo.keySet());
        MovilVoz.SOCIOS.putAll(nuevo);
        // silencio y altavoz solo valen mientras se está en una llamada
        MovilVoz.MUTE.removeIf(u -> !ACTUAL.containsKey(u));
        MovilVoz.ALTAVOZ.removeIf(u -> !ACTUAL.containsKey(u));
    }

    // ------------------------------------------------------------------ llamar
    private static boolean puedeLlamar(ServerPlayer yo) {
        if (ocupado(yo.getUUID())) return false;
        if (!MovilVoz.disponible()) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.sin_voz_servidor", "");
            return false;
        }
        if (!MovilVoz.conectado(yo.getUUID())) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.sin_voz_tu", "");
            return false;
        }
        return true;
    }

    static void llamar(ServerPlayer yo, UUID otroId, boolean video, boolean oculto) {
        if (otroId.equals(yo.getUUID()) || otroId.equals(OCULTO) || !puedeLlamar(yo)) return;
        MinecraftServer s = yo.server;
        String nombreOtro = MovilServidor.nombreDe(s, otroId);
        ServerPlayer otro = s.getPlayerList().getPlayer(otroId);
        if (otro == null || !MovilServidor.tieneMovil(otro)) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.no_disponible", nombreOtro);
            MovilDatos d = MovilServidor.datos(s);
            long ahora = System.currentTimeMillis();
            d.registrar(yo.getUUID(), new MovilDatos.Registro(otroId, ahora, MovilDatos.R_SIN_RESPUESTA, 0, video));
            d.registrar(otroId, new MovilDatos.Registro(oculto ? OCULTO : yo.getUUID(), ahora, MovilDatos.R_PERDIDA, 0, video));
            return;
        }
        if (!admite(otroId)) {
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
        Llamada l = new Llamada(yo.getUUID(), video && MovilConfig.video(), oculto, -1, "", s.getTickCount());
        empezar(s, l, List.of(otro));
    }

    /** Llamada de grupo a los del chat (los que estén conectados, con móvil y voz), hasta {@link #MAX_GRUPO}. */
    static void llamarGrupo(ServerPlayer yo, int chatId, boolean video) {
        MovilDatos.Chat c = MovilServidor.chatDeJugador(yo, chatId);
        if (c == null || !c.grupo || !puedeLlamar(yo)) return;
        MinecraftServer s = yo.server;
        List<ServerPlayer> invitados = new ArrayList<>();
        for (UUID u : c.miembros) {
            if (u.equals(yo.getUUID()) || invitados.size() >= MAX_GRUPO - 1) continue;
            ServerPlayer o = s.getPlayerList().getPlayer(u);
            if (o != null && MovilServidor.tieneMovil(o) && admite(u) && MovilVoz.conectado(u) && cobertura(yo, o)) invitados.add(o);
        }
        if (invitados.isEmpty()) {
            MovilServidor.aviso(yo, "movil.miscelanea.aviso.grupo_nadie", "");
            return;
        }
        Llamada l = new Llamada(yo.getUUID(), video && MovilConfig.video(), false, c.id,
                MovilServidor.nombreChat(s, c, yo.getUUID()), s.getTickCount());
        empezar(s, l, invitados);
    }

    private static void empezar(MinecraftServer s, Llamada l, List<ServerPlayer> invitados) {
        l.miembros.add(l.quien);
        l.dentro.add(l.quien);
        l.entro.put(l.quien, (long) s.getTickCount());
        ACTUAL.put(l.quien, l);
        MOTIVO.remove(l.quien);
        for (ServerPlayer o : invitados) {
            UUID u = o.getUUID();
            l.miembros.add(u);
            l.sonando.add(u);
            MOTIVO.remove(u);
            if (ACTUAL.containsKey(u)) SEGUNDA.put(u, l);
            else ACTUAL.put(u, l);
        }
        recalcularVoz();
        enviarTodos(s, l);
        timbre(s, l);
    }

    private static boolean cobertura(ServerPlayer a, ServerPlayer b) {
        if (MovilConfig.sinLimite()) return true;
        if (a.level() != b.level()) return MovilConfig.entreDimensiones();
        double d = MovilConfig.distancia();
        return a.distanceToSqr(b) <= d * d;
    }

    /**
     * El timbre suena en el mundo, donde está quien recibe la llamada (lo oyen también los de alrededor), con el tono que
     * tenga puesto para quien llama. Si le entra mientras habla (llamada en espera) no suena: el pitido lo pone su móvil.
     */
    private static void timbre(MinecraftServer s, Llamada l) {
        for (UUID u : l.sonando) {
            if (SEGUNDA.get(u) == l) continue;
            ServerPlayer o = s.getPlayerList().getPlayer(u);
            if (o == null) continue;
            MovilDatos.Perfil pf = MovilServidor.datos(s).perfil(u, null);
            if (pf.silencio) {
                o.level().playSound(null, o.getX(), o.getY(), o.getZ(), MovilRegistro.VIBRAR.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
                continue;
            }
            int tono = l.oculto || l.grupo() ? pf.tono : pf.tonos.getOrDefault(l.quien, pf.tono);
            o.level().playSound(null, o.getX(), o.getY(), o.getZ(), MovilRegistro.TONO[Math.floorMod(tono, MovilRegistro.TONOS)].get(),
                    SoundSource.PLAYERS, 0.9F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ contestar, colgar, cambiar
    /** La llamada que le está sonando a u (la actual o la que le entra en espera). */
    private static Llamada sonandoA(UUID u) {
        Llamada a = ACTUAL.get(u);
        if (a != null && a.sonando.contains(u)) return a;
        Llamada b = SEGUNDA.get(u);
        return b != null && b.sonando.contains(u) ? b : null;
    }

    static void contestar(ServerPlayer yo, boolean si) {
        UUID u = yo.getUUID();
        Llamada l = sonandoA(u);
        if (l == null) return;
        MinecraftServer s = yo.server;
        if (!si) {
            rechazar(s, u, l);
            return;
        }
        Llamada antes = null;
        if (SEGUNDA.get(u) == l) {
            // llamada en espera: la que tenía pasa a esperar
            antes = ACTUAL.get(u);
            ACTUAL.put(u, l);
            SEGUNDA.put(u, antes);
        }
        l.sonando.remove(u);
        l.dentro.add(u);
        l.entro.put(u, (long) s.getTickCount());
        if (!l.activa()) l.inicio = s.getTickCount();
        recalcularVoz();
        enviarTodos(s, l);
        if (antes != null) enviarTodos(s, antes);
    }

    private static void rechazar(MinecraftServer s, UUID u, Llamada l) {
        l.sonando.remove(u);
        MovilServidor.datos(s).registrar(u, new MovilDatos.Registro(l.otroPara(u), System.currentTimeMillis(), MovilDatos.R_ENTRANTE, 0, l.video));
        MOTIVO.put(u, FIN_RECHAZADA);
        quitarMapa(u, l);
        if (!comprobar(s, l, FIN_RECHAZADA)) enviarTodos(s, l);
        enviar(s, u);
    }

    static void colgar(ServerPlayer yo) {
        UUID u = yo.getUUID();
        Llamada l = ACTUAL.get(u);
        if (l == null) return;
        if (l.sonando.contains(u)) rechazar(yo.server, u, l);
        else salirDe(yo.server, u, l, FIN_COLGADA);
    }

    /** Pasa de la llamada actual a la que está en espera (y la actual queda en espera). */
    static void cambiar(ServerPlayer yo) {
        UUID u = yo.getUUID();
        Llamada a = ACTUAL.get(u), b = SEGUNDA.get(u);
        if (a == null || b == null || !b.dentro.contains(u) || a.sonando.contains(u)) return;
        ACTUAL.put(u, b);
        SEGUNDA.put(u, a);
        recalcularVoz();
        enviarTodos(yo.server, a);
        enviarTodos(yo.server, b);
    }

    static void silenciar(ServerPlayer yo, boolean si) {
        if (!ACTUAL.containsKey(yo.getUUID())) return;
        if (si) MovilVoz.MUTE.add(yo.getUUID());
        else MovilVoz.MUTE.remove(yo.getUUID());
        enviarTodos(yo.server, ACTUAL.get(yo.getUUID()));
    }

    static void altavoz(ServerPlayer yo, boolean si) {
        if (!ACTUAL.containsKey(yo.getUUID())) return;
        if (si) MovilVoz.ALTAVOZ.add(yo.getUUID());
        else MovilVoz.ALTAVOZ.remove(yo.getUUID());
        enviar(yo.server, yo.getUUID());
    }

    static void salir(ServerPlayer yo) {
        UUID u = yo.getUUID();
        for (Llamada l : new Llamada[]{SEGUNDA.get(u), ACTUAL.get(u)}) {
            if (l == null) continue;
            if (l.sonando.contains(u)) {
                l.sonando.remove(u);
                MovilServidor.datos(yo.server).registrar(u, new MovilDatos.Registro(l.otroPara(u), System.currentTimeMillis(), MovilDatos.R_PERDIDA, 0, l.video));
                quitarMapa(u, l);
                if (!comprobar(yo.server, l, FIN_SIN_RESPUESTA)) enviarTodos(yo.server, l);
            } else salirDe(yo.server, u, l, FIN_CORTADA);
        }
        MovilVoz.MUTE.remove(u);
        MovilVoz.ALTAVOZ.remove(u);
    }

    /** u deja la llamada l (estaba dentro). */
    private static void salirDe(MinecraftServer s, UUID u, Llamada l, int motivo) {
        if (!l.dentro.remove(u)) return;
        registrarDentro(s, u, l);
        MOTIVO.put(u, motivo);
        quitarMapa(u, l);
        if (!comprobar(s, l, motivo)) enviarTodos(s, l);
        enviar(s, u);
        Llamada sigue = ACTUAL.get(u);
        if (sigue != null && sigue != l) enviarTodos(s, sigue);
    }

    private static void registrarDentro(MinecraftServer s, UUID u, Llamada l) {
        MovilDatos d = MovilServidor.datos(s);
        long ahora = System.currentTimeMillis();
        if (!l.activa()) {
            // quien llamaba y nadie lo cogió
            d.registrar(u, new MovilDatos.Registro(l.otroPara(u), ahora, MovilDatos.R_SIN_RESPUESTA, 0, l.video));
            return;
        }
        int dur = (int) ((s.getTickCount() - l.entro.getOrDefault(u, l.inicio)) / 20);
        d.registrar(u, new MovilDatos.Registro(l.otroPara(u), ahora, u.equals(l.quien) ? MovilDatos.R_SALIENTE : MovilDatos.R_ENTRANTE, dur, l.video));
    }

    /** Quita la llamada l de los huecos de u (si era la actual, la de espera pasa a ser la actual). */
    private static void quitarMapa(UUID u, Llamada l) {
        if (ACTUAL.get(u) == l) {
            ACTUAL.remove(u);
            Llamada b = SEGUNDA.remove(u);
            if (b != null) ACTUAL.put(u, b);
        } else if (SEGUNDA.get(u) == l) SEGUNDA.remove(u);
        recalcularVoz();
    }

    /** Si ya no queda llamada (menos de dos personas, o nadie dentro), la termina. Devuelve si la terminó. */
    private static boolean comprobar(MinecraftServer s, Llamada l, int motivo) {
        boolean viva = !l.dentro.isEmpty() && l.dentro.size() + l.sonando.size() >= 2;
        if (viva) return false;
        terminar(s, l, motivo);
        return true;
    }

    private static void terminar(MinecraftServer s, Llamada l, int motivo) {
        MovilDatos d = MovilServidor.datos(s);
        long ahora = System.currentTimeMillis();
        for (UUID u : new ArrayList<>(l.dentro)) {
            registrarDentro(s, u, l);
            MOTIVO.put(u, motivo);
            quitarMapa(u, l);
        }
        for (UUID u : new ArrayList<>(l.sonando)) {
            d.registrar(u, new MovilDatos.Registro(l.otroPara(u), ahora, MovilDatos.R_PERDIDA, 0, l.video));
            MOTIVO.put(u, FIN_SIN_RESPUESTA);
            quitarMapa(u, l);
        }
        l.dentro.clear();
        l.sonando.clear();
        recalcularVoz();
        for (UUID u : l.miembros) {
            enviar(s, u);
            Llamada sigue = ACTUAL.get(u);
            if (sigue != null) enviarTodos(s, sigue);
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p != null) MovilServidor.sync(p);
        }
    }

    static void tick(MinecraftServer s) {
        if (ACTUAL.isEmpty()) return;
        long ahora = s.getTickCount();
        Set<Llamada> todas = new HashSet<>(ACTUAL.values());
        todas.addAll(SEGUNDA.values());
        for (Llamada l : todas) {
            if (l.dentro.isEmpty() && l.sonando.isEmpty()) continue;
            // el timbre se acaba: los que no lo cogieron, perdida
            if (ahora - l.creada > MovilConfig.timbreTicks() && !l.sonando.isEmpty()) {
                for (UUID u : new ArrayList<>(l.sonando)) {
                    l.sonando.remove(u);
                    MovilServidor.datos(s).registrar(u, new MovilDatos.Registro(l.otroPara(u), System.currentTimeMillis(), MovilDatos.R_PERDIDA, 0, l.video));
                    MOTIVO.put(u, FIN_SIN_RESPUESTA);
                    quitarMapa(u, l);
                    enviar(s, u);
                }
                if (comprobar(s, l, FIN_SIN_RESPUESTA)) continue;
                enviarTodos(s, l);
            } else if (!l.sonando.isEmpty() && (ahora - l.creada) % 50 == 0) timbre(s, l);
            if (ahora % 20 != 0) continue;
            for (UUID u : new ArrayList<>(l.dentro)) {
                ServerPlayer p = s.getPlayerList().getPlayer(u);
                boolean mal = p == null || !MovilServidor.tieneMovil(p);
                if (!mal) {
                    boolean alguno = false;
                    for (UUID v : l.dentro) {
                        if (v.equals(u)) continue;
                        ServerPlayer q = s.getPlayerList().getPlayer(v);
                        if (q != null && cobertura(p, q)) alguno = true;
                    }
                    for (UUID v : l.sonando) {
                        ServerPlayer q = s.getPlayerList().getPlayer(v);
                        if (q != null && cobertura(p, q)) alguno = true;
                    }
                    mal = !alguno;
                }
                if (mal) {
                    salirDe(s, u, l, FIN_CORTADA);
                    if (l.dentro.isEmpty()) break;
                }
            }
        }
    }

    /** Imagen de vídeo de uno de la llamada → a los demás que la oyen (vacía = cámara apagada). */
    static void video(ServerPlayer yo, byte[] img) {
        UUID u = yo.getUUID();
        Llamada l = ACTUAL.get(u);
        if (l == null || !l.activa() || !l.dentro.contains(u) || !MovilConfig.video()) return;
        if (img.length > 0) {
            if (img.length > MovilConfig.videoMaxBytes() || !Fotos.valida(img, MovilConfig.videoMaxBytes())) return;
            long ms = System.currentTimeMillis(), min = (long) (1000.0 / MovilConfig.videoFps() * 0.7);
            if (ms - l.ultVideo.getOrDefault(u, 0L) < min) return;
            l.ultVideo.put(u, ms);
        }
        for (UUID v : l.dentro) {
            if (v.equals(u) || ACTUAL.get(v) != l) continue;
            ServerPlayer o = yo.server.getPlayerList().getPlayer(v);
            if (o != null) MovilRed.aJugador(o, MovilRed.S_VIDEO, b -> {
                b.writeUUID(u);
                b.writeByteArray(img);
            });
        }
    }
}
