package com.miscelanea.client.baile;

import com.miscelanea.baile.CancionesBaile;

import java.util.HashMap;
import java.util.Map;

/**
 * Quién baila y qué: la canción y cuándo empezó (la pose sale de la coreografía de la canción en ese momento, ver
 * {@link Coreografia} y {@link AnimBaile}), la última flecha pisada (luces de la pista) y los traspiés de las fallos.
 */
public final class BailePoses {
    static final class Estado {
        /** Canción (null = esperando en el menú, con el vaivén). */
        CancionesBaile.Cancion cancion;
        long inicio;
        int carril = -1;
        long tPaso, tropiezo = -100000;
        /** Cuándo se puso la canción y cuándo se vio por última vez la pista (para los bailarines de otros). */
        long fijado, visto;
    }

    static final Map<Integer, Estado> ESTADOS = new HashMap<>();

    private BailePoses() {}

    static Estado de(int id) {
        return ESTADOS.computeIfAbsent(id, k -> new Estado());
    }

    /** Empieza a bailar (o a esperar, si la canción es null) con la canción que empezó en el milisegundo inicio. */
    public static void empezar(int id, CancionesBaile.Cancion c, long inicio) {
        Estado e = de(id);
        e.cancion = c;
        e.inicio = inicio;
        e.carril = -1;
        e.fijado = e.visto = System.currentTimeMillis();
    }

    /**
     * Desde el dibujo de la pista: lo que dice la pista (canción y tic en el que empezó) para el que baila en ella, por
     * si se llegó a mitad de canción o se perdió el aviso. No toca al jugador propio (ese lo lleva BaileCliente).
     */
    public static void sincronizar(int id, CancionesBaile.Cancion c, long inicio) {
        Estado e = ESTADOS.get(id);
        long ahora = System.currentTimeMillis();
        // un segundo de margen: el aviso de la canción y los datos de la pista no llegan a la vez
        if (e == null || e.cancion != c && ahora - e.fijado > 1000) {
            empezar(id, c, inicio);
            e = ESTADOS.get(id);
        }
        e.visto = ahora;
    }

    /** Los bailarines de otros cuya pista no se ha visto en 3 s dejan de bailar. */
    static void olvidarViejos(int yo) {
        if (ESTADOS.isEmpty()) return;
        long ahora = System.currentTimeMillis();
        ESTADOS.entrySet().removeIf(en -> en.getKey() != yo && ahora - en.getValue().visto > 3000);
    }

    public static void parar(int id) {
        ESTADOS.remove(id);
    }

    public static boolean baila(int id) {
        return ESTADOS.containsKey(id);
    }

    /** Canción que baila (null si espera o no baila). */
    public static CancionesBaile.Cancion cancion(int id) {
        Estado e = ESTADOS.get(id);
        return e == null ? null : e.cancion;
    }

    public static void paso(int id, int carril, int juicio) {
        Estado e = ESTADOS.get(id);
        if (e == null) return;
        e.carril = carril;
        e.tPaso = System.currentTimeMillis();
        if (juicio == 3) e.tropiezo = e.tPaso;
    }

    /** Último paso de una entidad: {carril, milisegundos desde entonces} o null. */
    public static long[] ultimo(int id) {
        Estado e = ESTADOS.get(id);
        if (e == null || e.carril < 0) return null;
        return new long[]{e.carril, System.currentTimeMillis() - e.tPaso};
    }

    /** Segundo de la canción (o del vaivén de espera) en el que va la entidad. */
    static float segundo(Estado e, long ahora) {
        return (ahora - e.inicio) / 1000F;
    }

    /** Segundos desde el último fallo. */
    static float desdeTropiezo(Estado e, long ahora) {
        return (ahora - e.tropiezo) / 1000F;
    }
}
