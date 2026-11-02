package com.miscelanea.tortura.aparatos;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Lo que sabe el CLIENTE de quién está metido en un aparato: lo rellena cada aparato en su tick de cliente (con el id de
 * entidad que le llega sincronizado) y caduca solo si deja de refrescarse (aparato fuera de la vista, roto, etc.).
 * No importa clases de cliente: lo consultan también los eventos comunes cuando corren en el cliente.
 */
public final class AparatosVictimas {
    private AparatosVictimas() {}

    public static final int DONCELLA = 1, POTRO = 2, SILLA = 3, PENDULO = 4, INMERSION = 5;
    private static final long CADUCA_MS = 400;

    public record Entrada(BlockPos pos, int tipo, long visto) {}

    private static final Map<Integer, Entrada> MAPA = new HashMap<>();

    public static synchronized void poner(int id, BlockPos pos, int tipo) {
        if (id < 0) return;
        MAPA.put(id, new Entrada(pos, tipo, Util.getMillis()));
    }

    @Nullable
    public static synchronized Entrada de(int id) {
        Entrada e = MAPA.get(id);
        if (e == null) return null;
        if (Util.getMillis() - e.visto() > CADUCA_MS) {
            MAPA.remove(id);
            return null;
        }
        return e;
    }

    public static boolean en(int id) {
        return de(id) != null;
    }

    public static int tipo(int id) {
        Entrada e = de(id);
        return e == null ? 0 : e.tipo();
    }

    public static synchronized void quitar(int id) {
        MAPA.remove(id);
    }

    public static synchronized void limpiar() {
        MAPA.clear();
    }
}
