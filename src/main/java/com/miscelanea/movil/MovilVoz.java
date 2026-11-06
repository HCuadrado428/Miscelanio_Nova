package com.miscelanea.movil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Puente con Simple Voice Chat sin tocar su API (para que el mod cargue sin él). Lo rellena el plugin
 * {@link com.miscelanea.movil.voz.MovilVozPlugin}, que el voicechat descubre solo si está instalado.
 * <p>
 * {@link #SOCIOS}: jugador → con quién está en una llamada activa (en los dos sentidos). El plugin, en el hilo de red del
 * voicechat, reenvía el micro de cada uno al otro como sonido "estático" (se oye igual a cualquier distancia o
 * dimensión) y quita la copia de proximidad para que no se oiga doble si están cerca.
 */
public final class MovilVoz {
    private MovilVoz() {}

    static volatile boolean disponible;

    // ------------------------------------------------------------------ notas de voz (cliente)
    /** Codificador Opus (48 kHz, mono, tramas de 960 muestras). */
    public interface Codificador {
        byte[] codificar(short[] pcm);

        void cerrar();
    }

    public interface Decodificador {
        short[] decodificar(byte[] opus);

        void cerrar();
    }

    /** Los pone el plugin del voicechat al iniciarse (en el cliente y en el servidor). */
    public static volatile java.util.function.Supplier<Codificador> nuevoCodificador;
    public static volatile java.util.function.Supplier<Decodificador> nuevoDecodificador;
    /**
     * Mientras se graba una nota de voz, el plugin le pasa aquí cada trama del micro del jugador local (desde el hilo del
     * micro del voicechat: el receptor tiene que ser seguro entre hilos).
     */
    public static volatile java.util.function.Consumer<short[]> microCliente;

    public static boolean codecDisponible() {
        return nuevoCodificador != null && nuevoDecodificador != null;
    }
    static volatile Predicate<UUID> conectado = u -> false;
    public static final Map<UUID, UUID> SOCIOS = new ConcurrentHashMap<>();

    /** El voicechat está funcionando en este servidor. */
    public static boolean disponible() {
        return disponible;
    }

    /** Ese jugador tiene el voicechat instalado y conectado. */
    public static boolean conectado(UUID u) {
        try {
            return disponible && conectado.test(u);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void activar(Predicate<UUID> conectadoFn) {
        conectado = conectadoFn;
        disponible = true;
    }

    public static void desactivar() {
        disponible = false;
        conectado = u -> false;
    }
}
