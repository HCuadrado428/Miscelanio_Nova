package com.miscelanea.client.armasfuego.comun;

import net.minecraft.client.Minecraft;

/**
 * Cuándo enseñar los contadores de balas y el tambor del HUD. Siempre encendidos tapaban la pantalla, así que solo salen
 * un rato (unos 3,5 s) tras sacar el arma, apuntar, disparar o recargar; y siempre que importe (sin balas, pocas, encasquillada
 * o recargando), lo decide cada HUD. Se renueva con {@link #tocar}.
 */
public final class HudActividad {
    private HudActividad() {}

    private static long hasta;

    /** Enseña el HUD de las armas durante {@code ticks} ticks desde ahora. */
    public static void tocar(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) hasta = Math.max(hasta, mc.level.getGameTime() + ticks);
    }

    /** ¿Ha habido actividad hace poco? */
    public static boolean reciente() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() <= hasta;
    }
}
