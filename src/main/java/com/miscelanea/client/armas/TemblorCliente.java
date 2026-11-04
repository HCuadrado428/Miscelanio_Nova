package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.camara.Sacudida;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sacudida de cámara que se apaga poco a poco (la manda el servidor con {@code TemblorPacket}): ruido suave con
 * trauma que decae ({@link Sacudida}), en vez de senos rápidos que se notaban mecánicos.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TemblorCliente {
    private TemblorCliente() {}

    /** Hasta 3 grados con trauma 1; la caída se ajusta en cada golpe a la duración pedida. */
    private static Sacudida actual;
    private static float fuerzaActual;

    /** @param f grados (como antes, hasta 3); @param duracion ticks hasta apagarse */
    public static void empezar(float f, int duracion) {
        f = Math.min(f, 3F);
        if (f <= 0F || duracion <= 0) return;
        // si ya hay uno más fuerte en marcha, se queda ese
        if (actual != null && actual.trauma() * fuerzaActual > f) return;
        float seg = duracion / 20F;
        // con trauma² y caída lineal, se apaga del todo en "seg" segundos
        actual = new Sacudida(f, f, f * 0.7F, 10F, 1F / seg);
        actual.golpe(1F);
        fuerzaActual = f;
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Sacudida s = actual;
        if (s == null) return;
        if (s.quieta()) {
            actual = null;
            return;
        }
        s.aplicar(e, Sacudida.escalaPantalla());
    }
}
