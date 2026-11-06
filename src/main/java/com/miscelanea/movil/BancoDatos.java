package com.miscelanea.movil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saldo de esmeraldas de cada jugador en el Banco del móvil y su historial ({@code data/miscelanea_banco.dat}). */
public class BancoDatos extends SavedData {
    private static final String NOMBRE = "miscelanea_banco";
    public static final int MAX_HISTORIAL = 50;
    /** Tipos de movimiento. */
    public static final int M_INGRESO = 0, M_RETIRADA = 1, M_ENVIADO = 2, M_RECIBIDO = 3, M_ADMIN = 4, M_OBJETO_ENVIADO = 5, M_OBJETO_RECIBIDO = 6;

    /** cantidad con signo (lo que cambia el saldo; 0 en los objetos), otro = el otro jugador (o null), texto = concepto u objeto. */
    public record Movimiento(long t, int tipo, UUID otro, String nombreOtro, long cantidad, String texto) {}

    public final Map<UUID, Long> saldo = new HashMap<>();
    public final Map<UUID, List<Movimiento>> historial = new HashMap<>();

    public static BancoDatos de(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(BancoDatos::cargar, BancoDatos::new, NOMBRE);
    }

    public long saldo(UUID u) {
        return saldo.getOrDefault(u, 0L);
    }

    /** Cambia el saldo; nunca lo deja negativo (devuelve false y no hace nada si quedaría). */
    public boolean mover(UUID u, long delta) {
        long n = saldo(u) + delta;
        if (n < 0) return false;
        saldo.put(u, n);
        setDirty();
        return true;
    }

    public void anotar(UUID u, Movimiento m) {
        List<Movimiento> l = historial.computeIfAbsent(u, k -> new ArrayList<>());
        l.add(0, m);
        while (l.size() > MAX_HISTORIAL) l.remove(l.size() - 1);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        java.util.Set<UUID> todos = new java.util.HashSet<>(saldo.keySet());
        todos.addAll(historial.keySet());
        for (UUID u : todos) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", u);
            c.putLong("s", saldo(u));
            ListTag h = new ListTag();
            for (Movimiento m : historial.getOrDefault(u, List.of())) {
                CompoundTag x = new CompoundTag();
                x.putLong("t", m.t());
                x.putByte("k", (byte) m.tipo());
                if (m.otro() != null) x.putUUID("o", m.otro());
                x.putString("on", m.nombreOtro());
                x.putLong("c", m.cantidad());
                x.putString("x", m.texto());
                h.add(x);
            }
            c.put("h", h);
            l.add(c);
        }
        t.put("cuentas", l);
        return t;
    }

    private static BancoDatos cargar(CompoundTag t) {
        BancoDatos d = new BancoDatos();
        ListTag l = t.getList("cuentas", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            if (!c.hasUUID("u")) continue;
            UUID u = c.getUUID("u");
            d.saldo.put(u, Math.max(0, c.getLong("s")));
            List<Movimiento> h = new ArrayList<>();
            ListTag hl = c.getList("h", Tag.TAG_COMPOUND);
            for (int j = 0; j < hl.size(); j++) {
                CompoundTag x = hl.getCompound(j);
                h.add(new Movimiento(x.getLong("t"), x.getByte("k"), x.hasUUID("o") ? x.getUUID("o") : null, x.getString("on"), x.getLong("c"), x.getString("x")));
            }
            d.historial.put(u, h);
        }
        return d;
    }
}
