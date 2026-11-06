package com.miscelanea.client.voz;

import com.miscelanea.Miscelanea;
import com.miscelanea.voz.VozEntrada;
import com.miscelanea.voz.VozEstadoPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente de las voces: guarda qué voces tiene cada jugador (lo manda el servidor, {@link VozEstadoPacket}), monta
 * la cadena de procesadores de cada hablante y la aplica a su audio cuando lo pide el plugin de Simple Voice Chat
 * ({@code plugin.MiscelaneaVozPlugin}, que es quien llama a {@link #procesar}). Si el voicechat no está, nadie llama
 * y todo esto queda en un mapa que no molesta. No se avisa en pantalla de que tu voz suena distinta: a ti te oyes
 * normal (el cambio lo aplican los demás) y nada lo delata.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class VozCliente {
    private VozCliente() {}

    /** Estado de un hablante. Las entradas las cambia el hilo del juego; la cadena solo la toca el hilo de audio. */
    private static final class Hablante {
        volatile List<VozEntrada> entradas = List.of();
        volatile int version;
        int versionCadena = -1;
        final List<ProcesadorVoz> cadena = new ArrayList<>();
        final List<Integer> amps = new ArrayList<>();
    }

    private static final Map<UUID, Hablante> HABLANTES = new ConcurrentHashMap<>();
    private static volatile boolean voicechat;

    // ------------------------------------------------------------------ estado que manda el servidor
    public static void recibir(VozEstadoPacket p) {
        if (p.voces.isEmpty()) {
            HABLANTES.remove(p.jugador);
            return;
        }
        Hablante h = HABLANTES.computeIfAbsent(p.jugador, u -> new Hablante());
        h.entradas = List.copyOf(p.voces);
        h.version++;
    }

    public static List<VozEntrada> vocesDe(UUID jugador) {
        Hablante h = HABLANTES.get(jugador);
        return h == null ? List.of() : h.entradas;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        HABLANTES.clear();
    }

    // ------------------------------------------------------------------ audio (hilo del voicechat)
    /** El plugin avisa de que está activo. */
    public static void voicechatActivo() {
        voicechat = true;
    }

    /** ¿Está Simple Voice Chat cargado en este cliente? (lo usa el poder de imitar voces). */
    public static boolean hayVoicechat() {
        return voicechat;
    }

    /**
     * Aplica al fotograma las voces del hablante. Devuelve {@code false} si no había nada que hacer (el audio queda
     * intacto). Se llama desde el hilo de audio del voicechat: no toca nada del juego.
     */
    public static boolean procesar(UUID hablante, short[] frame) {
        if (frame == null) return false;
        Hablante h = HABLANTES.get(hablante);
        if (h == null) return false;
        int v = h.version;
        if (h.versionCadena != v) {
            h.cadena.clear();
            h.amps.clear();
            for (VozEntrada e : h.entradas) {
                ProcesadorVoz p = Voces.crear(e.id());
                if (p == null) continue;
                h.cadena.add(p);
                h.amps.add(e.amp());
            }
            h.versionCadena = v;
        }
        if (h.cadena.isEmpty()) return false;
        try {
            for (int i = 0; i < h.cadena.size(); i++) h.cadena.get(i).procesar(frame, h.amps.get(i));
        } catch (RuntimeException ex) {
            // un fallo en un procesador no puede tirar el hilo de audio: se quita la cadena y se sigue con la voz normal
            Miscelanea.LOGGER.warn("[miscelanea] Fallo procesando la voz de {}; se deja normal", hablante, ex);
            h.cadena.clear();
            return false;
        }
        return true;
    }
}
