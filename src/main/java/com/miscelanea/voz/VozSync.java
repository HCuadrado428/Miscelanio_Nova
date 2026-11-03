package com.miscelanea.voz;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Servidor: manda a los clientes qué voces tiene cada jugador ({@link VozEstadoPacket}). Los cambios de efecto se
 * apuntan en {@link #marcar} y se mandan en el tick siguiente (dentro de los eventos de efecto el mapa de efectos aún
 * no está al día), ver {@link VozEventos}.
 */
public final class VozSync {
    private VozSync() {}

    private static final Set<UUID> PENDIENTES = Collections.synchronizedSet(new HashSet<>());

    public static void marcar(ServerPlayer p) {
        PENDIENTES.add(p.getUUID());
    }

    static boolean pendiente(ServerPlayer p) {
        return PENDIENTES.remove(p.getUUID());
    }

    static void olvidar(ServerPlayer p) {
        PENDIENTES.remove(p.getUUID());
    }

    /** Estado actual del jugador a todos los que lo ven (y a él). */
    public static void sincronizar(ServerPlayer p) {
        VozRed.alrededor(p, new VozEstadoPacket(p.getUUID(), VozPorEfecto.vocesDe(p)));
    }

    /** Estado actual de {@code de} solo a {@code quien} (cuando empieza a verlo). */
    public static void sincronizarCon(ServerPlayer de, ServerPlayer quien) {
        VozRed.a(quien, new VozEstadoPacket(de.getUUID(), VozPorEfecto.vocesDe(de)));
    }
}
