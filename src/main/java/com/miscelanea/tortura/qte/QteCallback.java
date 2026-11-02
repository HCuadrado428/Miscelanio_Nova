package com.miscelanea.tortura.qte;

import net.minecraft.server.level.ServerPlayer;

/**
 * Lo que se hace en el servidor cuando el minijuego termina (siempre se llama exactamente una vez).
 * 'jugador' solo puede ser null si se ha desconectado de golpe (en ese caso el resultado es CANCELADO).
 */
@FunctionalInterface
public interface QteCallback {
    void alTerminar(ServerPlayer jugador, QteResultado resultado);
}
