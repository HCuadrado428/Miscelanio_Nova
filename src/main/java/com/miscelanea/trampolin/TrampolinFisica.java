package com.miscelanea.trampolin;

import net.minecraft.world.entity.player.Player;

/** Puente entre el bloque (común) y el cliente: cuándo pulsó saltar el jugador local. */
public final class TrampolinFisica {
    public static volatile long ultimoSalto;
    public static volatile java.util.UUID local;

    private TrampolinFisica() {}

    static boolean esLocal(Player p) {
        return p.getUUID().equals(local);
    }

    static boolean saltoATiempo() {
        return System.currentTimeMillis() - ultimoSalto < 280;
    }
}
