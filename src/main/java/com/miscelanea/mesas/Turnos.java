package com.miscelanea.mesas;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Utilidades comunes a los juegos de mesa por turnos: saber si un jugador sigue en la mesa, jugar por él si se ha ido
 * (o lleva demasiado sin jugar) y votar para terminar la partida antes de tiempo.
 */
public final class Turnos {
    /** Distancia (bloques) a partir de la que se considera que el jugador se ha ido de la mesa. */
    public static final double RADIO = 16;

    public static final String AVISO_CPU = "msg.miscelanea.turno.cpu", AVISO_VUELVE = "msg.miscelanea.turno.vuelve",
            AVISO_VOTO = "msg.miscelanea.turno.voto_fin",
            AVISO_TIEMPO = "msg.miscelanea.turno.tiempo";

    private Turnos() {}

    /** El jugador está conectado, en este mundo y a menos de {@link #RADIO} bloques de la mesa. */
    public static boolean presente(Level l, BlockPos pos, @Nullable UUID u) {
        if (u == null || l == null) return false;
        Player p = l.getPlayerByUUID(u);
        return p != null && !p.isRemoved() && p.distanceToSqr(Vec3.atCenterOf(pos)) <= RADIO * RADIO;
    }

    /** Mensaje en el chat a quienes estén alrededor de la mesa. */
    public static void avisar(Level l, BlockPos pos, String clave, Object... args) {
        if (l == null || l.isClientSide) return;
        for (ServerPlayer o : l.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(12)))
            o.displayClientMessage(Component.translatable(clave, args).withStyle(ChatFormatting.YELLOW), false);
    }

    /**
     * Reloj de turno: vigila al jugador humano de turno. Si se le acaba el tiempo ({@code juegos_de_mesa.turno_segundos})
     * o se va de la mesa / se desconecta más de {@code ausencia_segundos}, queda "dormido" y la CPU juega por él hasta
     * que vuelva (si se fue) o hasta que vuelva a tocar la mesa (si estaba cerca sin jugar). El sitio no se pierde.
     * Mientras espera a un humano manda el reloj a los clientes cercanos (se pinta en el panel del juego).
     * No se guarda: tras recargar el mundo se vuelve a contar desde cero.
     */
    public static final class Vigia {
        /** Dormidos: true si fue por ausencia (se despierta al volver), false si por inactividad (se despierta al jugar). */
        private final Map<UUID, Boolean> dormidos = new HashMap<>();
        private final float factor;
        private long clave = Long.MIN_VALUE;
        private int espera, ausente, envio;
        @Nullable
        private UUID vigilado;
        private boolean anunciado;

        public Vigia() {
            this(1F);
        }

        /** @param factor multiplica el tiempo de turno (p. ej. 2 en juegos con turnos de muchas acciones) */
        public Vigia(float factor) {
            this.factor = factor;
        }

        private int limite() {
            return Math.round(com.miscelanea.ConfigServidor.mesasTurnoTicks() * factor);
        }

        private void reloj(Level l, BlockPos pos, @Nullable UUID u, String nombre, boolean dormido) {
            int lim = limite();
            com.miscelanea.juegos.MesasRed.RelojPacket.enviar(l, pos, new com.miscelanea.juegos.MesasRed.RelojPacket(pos, u, nombre,
                    lim <= 0 || dormido ? -1 : Math.max(0, lim - espera), lim, dormido));
            anunciado = u != null;
            envio = 0;
        }

        /**
         * Llamar cada tick mientras haya que esperar a un humano.
         *
         * @param u          jugador humano que debe actuar (null si no hay que vigilar a nadie ahora)
         * @param claveTurno valor que cambia con cada acción (reinicia la cuenta)
         * @return true si hay que jugar por él en este momento
         */
        public boolean vigilar(Level l, BlockPos pos, @Nullable UUID u, String nombre, long claveTurno) {
            if (u == null) {
                if (anunciado) reloj(l, pos, null, "", false);
                clave = Long.MIN_VALUE;
                espera = ausente = 0;
                vigilado = null;
                return false;
            }
            if (!u.equals(vigilado)) {
                vigilado = u;
                clave = Long.MIN_VALUE;
            }
            boolean pres = presente(l, pos, u);
            Boolean d = dormidos.get(u);
            envio++;
            if (d != null) {
                if (d && pres) {
                    // ha vuelto: vuelve a jugar él
                    dormidos.remove(u);
                    clave = claveTurno;
                    espera = ausente = 0;
                    avisar(l, pos, AVISO_VUELVE, nombre);
                    reloj(l, pos, u, nombre, false);
                    return false;
                }
                if (envio >= 10) reloj(l, pos, u, nombre, true);
                return true;
            }
            boolean nuevo = false;
            if (claveTurno != clave) {
                clave = claveTurno;
                espera = ausente = 0;
                nuevo = true;
            }
            espera++;
            ausente = pres ? 0 : ausente + 1;
            int lim = limite();
            if (ausente >= com.miscelanea.ConfigServidor.mesasAusenciaTicks() || (lim > 0 && espera >= lim)) {
                boolean fuera = ausente >= com.miscelanea.ConfigServidor.mesasAusenciaTicks();
                dormidos.put(u, fuera);
                avisar(l, pos, fuera ? AVISO_CPU : AVISO_TIEMPO, nombre);
                reloj(l, pos, u, nombre, true);
                return true;
            }
            if (nuevo || envio >= 10) reloj(l, pos, u, nombre, false);
            return false;
        }

        /**
         * El jugador ha hecho algo en la mesa: si la CPU jugaba por él, vuelve a jugar él (con el reloj de nuevo lleno).
         * Tocar la mesa sin jugar no para el reloj de un turno en marcha.
         */
        public void despertar(@Nullable UUID u) {
            if (u == null) return;
            if (dormidos.remove(u) != null && u.equals(vigilado)) {
                espera = ausente = 0;
                envio = 10;
            }
        }

        public boolean dormido(@Nullable UUID u) {
            return u != null && dormidos.containsKey(u);
        }

        public void limpiar() {
            dormidos.clear();
            clave = Long.MIN_VALUE;
            espera = ausente = 0;
            vigilado = null;
        }
    }

    /** Votación para terminar antes de tiempo: hace falta el acuerdo de todos los humanos que siguen en la mesa. */
    public static final class Votacion {
        private final Set<UUID> votos = new HashSet<>();

        /**
         * Registra el voto de quien pulsa "Terminar".
         *
         * @param humanos jugadores humanos que siguen en la partida
         * @return cuántos humanos presentes faltan por estar de acuerdo (0: se puede terminar)
         */
        public int votar(Level l, BlockPos pos, UUID quien, Collection<UUID> humanos) {
            votos.add(quien);
            votos.retainAll(humanos);
            int faltan = 0;
            for (UUID u : humanos) if (!votos.contains(u) && presente(l, pos, u)) faltan++;
            if (faltan == 0) votos.clear();
            return faltan;
        }

        public int votos() {
            return votos.size();
        }

        public void limpiar() {
            votos.clear();
        }
    }
}
