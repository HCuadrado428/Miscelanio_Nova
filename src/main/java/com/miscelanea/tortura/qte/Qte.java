package com.miscelanea.tortura.qte;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Minijuegos rápidos (QTE) reutilizables. API del SERVIDOR:
 * <pre>{@code
 * Qte.iniciar(jugador, QteTipo.CIRCULO, QteParams.de(0.5F).rondas(2).titulo(Component.literal("¡Aguanta!")),
 *         (p, r) -> { if (r.exito()) ...; else ...; });
 * }</pre>
 * <ul>
 *   <li>Solo hay un minijuego por jugador: iniciar otro cancela el anterior (su callback recibe CANCELADO).</li>
 *   <li>El cliente dibuja el minijuego encima del HUD (sin pantalla: el mundo sigue y no se pausa) y manda el resultado.</li>
 *   <li>El servidor NO se fía: un éxito más rápido de lo físicamente posible o con datos imposibles cuenta como FALLO
 *       ({@link QteLogica#plausible}). Si el cliente no contesta a tiempo, FALLO. Si se aleja del ancla, se desconecta
 *       o muere, CANCELADO.</li>
 *   <li>El callback se llama exactamente una vez, en el hilo del servidor.</li>
 *   <li>{@link #despues(int, Runnable)} sirve para encadenar otro minijuego unos ticks más tarde (p. ej. la siguiente ganzúa).</li>
 * </ul>
 */
public final class Qte {
    private Qte() {}

    private static final class Sesion {
        final int id;
        final QteTipo tipo;
        final QteParams params;
        final QteCallback cb;
        final long inicio = Util.getMillis();
        final ResourceKey<Level> dimension;

        Sesion(int id, QteTipo tipo, QteParams params, QteCallback cb, ResourceKey<Level> dim) {
            this.id = id;
            this.tipo = tipo;
            this.params = params;
            this.cb = cb;
            this.dimension = dim;
        }
    }

    private record Programada(long tick, Runnable r) {}

    private static final Map<UUID, Sesion> SESIONES = new HashMap<>();
    private static final List<Programada> PROGRAMADAS = new ArrayList<>();
    private static int siguiente = 1;
    private static long ticks;

    /** Empieza un minijuego para el jugador. Devuelve su id. */
    public static int iniciar(ServerPlayer p, QteTipo tipo, QteParams params, QteCallback cb) {
        cancelar(p);
        if (params.semilla == 0) params.semilla = ThreadLocalRandom.current().nextLong() | 1L;
        int id = siguiente++;
        SESIONES.put(p.getUUID(), new Sesion(id, tipo, params, cb, p.level().dimension()));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new QteInicioPacket(id, tipo, params.dificultad, params.rondas, params.semilla, params.titulo));
        return id;
    }

    /** ¿Está jugando ahora un minijuego? */
    public static boolean activo(ServerPlayer p) {
        return SESIONES.containsKey(p.getUUID());
    }

    /** Tipo del minijuego en curso (o null). */
    public static QteTipo tipoActivo(ServerPlayer p) {
        Sesion s = SESIONES.get(p.getUUID());
        return s == null ? null : s.tipo;
    }

    /** Cancela el minijuego en curso (si hay): el callback recibe CANCELADO y el cliente lo cierra. */
    public static void cancelar(ServerPlayer p) {
        terminar(p, QteResultado.CANCELADO, true);
    }

    /** Ejecuta algo en el servidor dentro de unos ticks. */
    public static void despues(int nTicks, Runnable r) {
        PROGRAMADAS.add(new Programada(ticks + Math.max(1, nTicks), r));
    }

    private static void terminar(ServerPlayer p, QteResultado r, boolean avisarCliente) {
        Sesion s = SESIONES.remove(p.getUUID());
        if (s == null) return;
        if (avisarCliente) ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new QteFinPacket(s.id));
        try {
            s.cb.alTerminar(p, r);
        } catch (Exception e) {
            Miscelanea.LOGGER.error("Error en el callback del minijuego {}", s.tipo, e);
        }
    }

    /** Llega el resultado del cliente: se valida antes de creérselo. */
    static void recibir(ServerPlayer p, int id, QteResultado r, int[] datos) {
        Sesion s = SESIONES.get(p.getUUID());
        if (s == null || s.id != id) return;
        long ms = Util.getMillis() - s.inicio;
        QteParams pa = s.params;
        if (r.exito()) {
            if (!QteLogica.plausible(s.tipo, pa.dificultad, pa.rondas, pa.semilla, datos, ms)) {
                Miscelanea.LOGGER.warn("Minijuego {} de {}: resultado imposible ({} ms, datos {}), cuenta como fallo",
                        s.tipo, p.getGameProfile().getName(), ms, java.util.Arrays.toString(datos));
                r = QteResultado.FALLO;
            } else if (r == QteResultado.PERFECTO && !QteLogica.perfecto(s.tipo, pa.dificultad, pa.rondas, pa.semilla, datos)) {
                r = QteResultado.EXITO;
            }
        }
        if (r == QteResultado.ROTO && s.tipo != QteTipo.GANZUA) r = QteResultado.FALLO;
        terminar(p, r, false);
    }

    /** Cada tick del servidor: plazos, anclas y tareas programadas. */
    public static void tick(MinecraftServer server) {
        ticks++;
        if (!PROGRAMADAS.isEmpty()) {
            List<Programada> listas = new ArrayList<>();
            for (Iterator<Programada> it = PROGRAMADAS.iterator(); it.hasNext(); ) {
                Programada pr = it.next();
                if (pr.tick() <= ticks) {
                    listas.add(pr);
                    it.remove();
                }
            }
            for (Programada pr : listas) {
                try {
                    pr.r().run();
                } catch (Exception e) {
                    Miscelanea.LOGGER.error("Error en una tarea programada del minijuego", e);
                }
            }
        }
        if (SESIONES.isEmpty()) return;
        long ahora = Util.getMillis();
        for (UUID u : new ArrayList<>(SESIONES.keySet())) {
            Sesion s = SESIONES.get(u);
            if (s == null) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) {
                SESIONES.remove(u);
                try {
                    s.cb.alTerminar(null, QteResultado.CANCELADO);
                } catch (Exception ignored) {
                    // el callback no espera jugador nulo: da igual, ya no está
                }
                continue;
            }
            QteParams pa = s.params;
            if (ahora - s.inicio > QteLogica.limiteMs(s.tipo, pa.dificultad, pa.rondas, pa.semilla) + 4000) {
                terminar(p, QteResultado.FALLO, true);
            } else if (!p.isAlive() || p.level().dimension() != s.dimension
                    || (pa.ancla != null && p.position().distanceTo(pa.ancla) > pa.radio)) {
                terminar(p, QteResultado.CANCELADO, true);
            }
        }
    }

    /** El jugador se va del servidor. */
    public static void desconectar(ServerPlayer p) {
        terminar(p, QteResultado.CANCELADO, false);
    }

    /** Al parar el servidor (mundo de un jugador) no debe quedar nada colgado. */
    public static void limpiar() {
        SESIONES.clear();
        PROGRAMADAS.clear();
    }
}
