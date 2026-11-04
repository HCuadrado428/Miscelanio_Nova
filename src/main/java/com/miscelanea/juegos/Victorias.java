package com.miscelanea.juegos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Victorias de cada jugador en cada juego de mesa, para todo el servidor (se guarda con el mundo, en el SavedData
 * {@code miscelanea_victorias} del mundo principal).
 * <p>
 * API para otros módulos (p. ej. un ranking):
 * <ul>
 *     <li>{@link #sumar(ServerPlayer, String)} / {@link #sumar(ServerPlayer, String, boolean)}: suma una victoria.</li>
 *     <li>{@link #de(UUID, String)}: victorias totales de un jugador en un juego; {@link #contraHumanos(UUID, String)}: solo
 *     las ganadas con algún rival humano en la mesa.</li>
 *     <li>{@link #nombre(UUID)}: último nombre conocido; {@link #todas()}: copia de toda la tabla.</li>
 * </ul>
 * Identificadores de juego: {@link #JUEGOS}.
 */
public class Victorias extends SavedData {
    private static final String NOMBRE = "miscelanea_victorias";
    public static final String PARCHIS = "parchis", OCA = "oca", IMPERIO = "imperio", CUATRO = "cuatro", FLOTA = "flota", DOMINO = "domino",
            BRISCA = "brisca", CHINCHON = "chinchon", AJEDREZ = "ajedrez", DAMAS = "damas", REVERSI = "reversi", BILLAR = "billar",
            FUTBOLIN = "futbolin", AIR_HOCKEY = "air_hockey";
    public static final String[] JUEGOS = {PARCHIS, OCA, IMPERIO, CUATRO, FLOTA, DOMINO, BRISCA, CHINCHON, AJEDREZ, DAMAS, REVERSI, BILLAR,
            FUTBOLIN, AIR_HOCKEY};

    /** jugador → juego → {totales, contra humanos}. */
    private final Map<UUID, Map<String, int[]>> tabla = new HashMap<>();
    private final Map<UUID, String> nombres = new HashMap<>();

    @Nullable
    public static Victorias de(@Nullable MinecraftServer s) {
        if (s == null) return null;
        return s.overworld().getDataStorage().computeIfAbsent(Victorias::cargar, Victorias::new, NOMBRE);
    }

    @Nullable
    private static Victorias actual() {
        return de(ServerLifecycleHooks.getCurrentServer());
    }

    // ------------------------------------------------------------------ API estática

    /** Suma una victoria (contando que había algún rival humano). Devuelve las victorias totales tras sumar. */
    public static int sumar(ServerPlayer p, String juego) {
        return sumar(p, juego, true);
    }

    /** Suma una victoria; {@code contraHumano} si alguno de los rivales era una persona. Devuelve el total tras sumar. */
    public static int sumar(ServerPlayer p, String juego, boolean contraHumano) {
        return sumar(p.server, p.getUUID(), p.getGameProfile().getName(), juego, contraHumano);
    }

    /** Igual, para un jugador que puede no estar conectado. */
    public static int sumar(MinecraftServer s, UUID u, String nombre, String juego, boolean contraHumano) {
        Victorias v = de(s);
        if (v == null || u == null || juego == null || juego.isEmpty()) return 0;
        int[] c = v.tabla.computeIfAbsent(u, k -> new HashMap<>()).computeIfAbsent(juego, k -> new int[2]);
        c[0]++;
        if (contraHumano) c[1]++;
        if (nombre != null && !nombre.isEmpty()) v.nombres.put(u, nombre);
        v.setDirty();
        // el ranking del servidor lleva su propia cuenta (con la hora, para desempatar) y avisa a los tablones
        com.miscelanea.ranking.Ranking.victoria(s, u, nombre, juego);
        return c[0];
    }

    /** Victorias totales del jugador en ese juego (0 si no hay servidor o no ha ganado nunca). */
    public static int de(UUID u, String juego) {
        int[] c = celda(u, juego);
        return c == null ? 0 : c[0];
    }

    /** Victorias del jugador en ese juego con algún rival humano. */
    public static int contraHumanos(UUID u, String juego) {
        int[] c = celda(u, juego);
        return c == null ? 0 : c[1];
    }

    /** Victorias del jugador sumando todos los juegos. */
    public static int total(UUID u) {
        Victorias v = actual();
        if (v == null || u == null) return 0;
        int n = 0;
        for (int[] c : v.tabla.getOrDefault(u, Map.of()).values()) n += c[0];
        return n;
    }

    /** Último nombre con el que ganó (o "" si no se conoce). */
    public static String nombre(UUID u) {
        Victorias v = actual();
        return v == null || u == null ? "" : v.nombres.getOrDefault(u, "");
    }

    /** Copia de la tabla: jugador → juego → victorias totales. */
    public static Map<UUID, Map<String, Integer>> todas() {
        Victorias v = actual();
        if (v == null) return Collections.emptyMap();
        Map<UUID, Map<String, Integer>> out = new LinkedHashMap<>();
        v.tabla.forEach((u, m) -> {
            Map<String, Integer> c = new LinkedHashMap<>();
            m.forEach((j, n) -> c.put(j, n[0]));
            out.put(u, c);
        });
        return out;
    }

    @Nullable
    private static int[] celda(UUID u, String juego) {
        Victorias v = actual();
        if (v == null || u == null) return null;
        Map<String, int[]> m = v.tabla.get(u);
        return m == null ? null : m.get(juego);
    }

    // ------------------------------------------------------------------ guardar

    private static Victorias cargar(CompoundTag t) {
        Victorias v = new Victorias();
        CompoundTag js = t.getCompound("jugadores");
        for (String k : js.getAllKeys()) {
            UUID u;
            try {
                u = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag j = js.getCompound(k);
            if (j.contains("nombre")) v.nombres.put(u, j.getString("nombre"));
            CompoundTag g = j.getCompound("juegos");
            Map<String, int[]> m = new HashMap<>();
            for (String juego : g.getAllKeys()) {
                int[] a = g.getIntArray(juego);
                m.put(juego, new int[]{a.length > 0 ? a[0] : 0, a.length > 1 ? a[1] : 0});
            }
            v.tabla.put(u, m);
        }
        return v;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        CompoundTag js = new CompoundTag();
        tabla.forEach((u, m) -> {
            CompoundTag j = new CompoundTag();
            String n = nombres.get(u);
            if (n != null) j.putString("nombre", n);
            CompoundTag g = new CompoundTag();
            m.forEach((juego, c) -> g.putIntArray(juego, c.clone()));
            j.put("juegos", g);
            js.put(u.toString(), j);
        });
        t.put("jugadores", js);
        return t;
    }
}
