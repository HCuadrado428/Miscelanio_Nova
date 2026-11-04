package com.miscelanea.client.camara;

import com.miscelanea.Miscelanea;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reloj de la cámara en segundos de tiempo real, que se para con el juego en pausa. Avanza una vez por fotograma (al
 * empezar), así que todo lo que se calcula con él es independiente de los FPS y no da saltos entre ticks.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class Reloj {
    private Reloj() {}

    private static long ultimo = -1;
    private static double t = 1000.0;

    /** Segundos (con pausa descontada). */
    public static double t() {
        return t;
    }

    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        long ahora = Util.getNanos();
        if (ultimo < 0) ultimo = ahora;
        double dt = (ahora - ultimo) / 1.0E9;
        ultimo = ahora;
        // un tirón (carga de mundo, ventana arrastrada) no debe hacer saltar los efectos
        if (dt < 0 || dt > 0.25) dt = 0;
        if (Minecraft.getInstance().isPaused()) dt = 0;
        t += dt;
    }
}
