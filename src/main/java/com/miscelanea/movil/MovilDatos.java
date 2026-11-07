package com.miscelanea.movil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Datos del móvil que se guardan con el mundo ({@code data/miscelanea_movil.dat}): el perfil de cada jugador (número,
 * ajustes, sus fotos y su registro de llamadas) y los chats con su historial. Las imágenes van en archivos aparte
 * ({@link Fotos}).
 */
public class MovilDatos extends SavedData {
    private static final String NOMBRE = "miscelanea_movil";

    /** Tipos del registro de llamadas. */
    public static final int R_SALIENTE = 0, R_ENTRANTE = 1, R_PERDIDA = 2, R_SIN_RESPUESTA = 3;
    public static final int MAX_REGISTRO = 40;

    public static final class Perfil {
        public final UUID id;
        public String nombre = "?", numero = "000";
        public boolean silencio;
        public int tono, fondo, alarma = -1, sigFoto = 1;
        public final List<FotoMeta> fotos = new ArrayList<>();
        public final List<Registro> llamadas = new ArrayList<>();
        /** chat → último número de mensaje leído. */
        public final Map<Integer, Long> leido = new HashMap<>();
        /** Tono de llamada para un contacto concreto (si no está, el general). */
        public final Map<UUID, Integer> tonos = new HashMap<>();

        Perfil(UUID id) {
            this.id = id;
        }
    }

    /** Foto de la galería; con {@code ms > 0} es un vídeo corto (y la foto es su miniatura). */
    public record FotoMeta(int id, long t, int ms) {
        public FotoMeta(int id, long t) {
            this(id, t, 0);
        }
    }

    public record Registro(UUID otro, long t, int tipo, int dur, boolean video) {}

    /**
     * Un mensaje: texto, o foto adjunta ({@code adjunto} &ge; 0; si además {@code voz} &lt; 0 y {@code vozMs} &gt; 0 es un
     * vídeo corto de esa duración), o nota de voz ({@code voz} &ge; 0, con su duración en ms y
     * su forma de onda: una barra 0..255 por byte).
     */
    public record Mensaje(long sec, UUID de, long t, String texto, int adjunto, int voz, int vozMs, byte[] onda) {
        public Mensaje(long sec, UUID de, long t, String texto, int adjunto) {
            this(sec, de, t, texto, adjunto, -1, 0, new byte[0]);
        }
    }

    public static final class Chat {
        public final int id;
        public boolean grupo;
        public String nombre = "";
        public final List<UUID> miembros = new ArrayList<>();
        public final ArrayList<Mensaje> msgs = new ArrayList<>();
        public long sec;

        Chat(int id) {
            this.id = id;
        }

        public Mensaje ultimo() {
            return msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);
        }
    }

    public final Map<UUID, Perfil> perfiles = new LinkedHashMap<>();
    public final Map<Integer, Chat> chats = new LinkedHashMap<>();
    public int sigChat = 1, sigAdjunto = 1, sigVoz = 1;

    public static MovilDatos de(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(MovilDatos::cargar, MovilDatos::new, NOMBRE);
    }

    /** Perfil de un jugador; lo crea (con número nuevo) si no lo tenía. */
    public Perfil perfil(UUID id, String nombre) {
        Perfil p = perfiles.get(id);
        if (p == null) {
            p = new Perfil(id);
            p.numero = numeroLibre(id);
            perfiles.put(id, p);
            setDirty();
        }
        if (nombre != null && !nombre.equals(p.nombre)) {
            p.nombre = nombre;
            setDirty();
        }
        return p;
    }

    private String numeroLibre(UUID id) {
        Set<String> usados = new HashSet<>();
        for (Perfil p : perfiles.values()) usados.add(p.numero);
        int digitos = usados.size() >= 850 ? 4 : 3, min = digitos == 3 ? 100 : 1000, rango = digitos == 3 ? 900 : 9000;
        int n = Math.floorMod(id.hashCode() * 31 + (int) (id.getLeastSignificantBits() >>> 7), rango);
        for (int i = 0; i < rango; i++) {
            String s = Integer.toString(min + (n + i * 7) % rango);
            if (!usados.contains(s)) return s;
        }
        return Integer.toString(10000 + perfiles.size());
    }

    public Perfil porNumero(String numero) {
        for (Perfil p : perfiles.values()) if (p.numero.equals(numero)) return p;
        return null;
    }

    public List<Chat> chatsDe(UUID id) {
        List<Chat> l = new ArrayList<>();
        for (Chat c : chats.values()) if (c.miembros.contains(id)) l.add(c);
        return l;
    }

    /** El chat individual entre dos jugadores, si existe. */
    public Chat individual(UUID a, UUID b) {
        for (Chat c : chats.values())
            if (!c.grupo && c.miembros.size() == 2 && c.miembros.contains(a) && c.miembros.contains(b)) return c;
        return null;
    }

    public Chat nuevoChat(boolean grupo, String nombre, List<UUID> miembros) {
        Chat c = new Chat(sigChat++);
        c.grupo = grupo;
        c.nombre = nombre == null ? "" : nombre;
        c.miembros.addAll(miembros);
        chats.put(c.id, c);
        setDirty();
        return c;
    }

    public int noLeidos(Chat c, UUID quien) {
        long hasta = perfiles.containsKey(quien) ? perfiles.get(quien).leido.getOrDefault(c.id, 0L) : 0L;
        int n = 0;
        for (int i = c.msgs.size() - 1; i >= 0; i--) {
            Mensaje m = c.msgs.get(i);
            if (m.sec() <= hasta) break;
            if (!m.de().equals(quien)) n++;
        }
        return n;
    }

    public void registrar(UUID quien, Registro r) {
        Perfil p = perfiles.get(quien);
        if (p == null) return;
        p.llamadas.add(0, r);
        while (p.llamadas.size() > MAX_REGISTRO) p.llamadas.remove(p.llamadas.size() - 1);
        setDirty();
    }

    // ------------------------------------------------------------------ NBT
    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag ps = new ListTag();
        for (Perfil p : perfiles.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", p.id);
            c.putString("n", p.nombre);
            c.putString("num", p.numero);
            c.putBoolean("sil", p.silencio);
            c.putInt("tono", p.tono);
            c.putInt("fondo", p.fondo);
            c.putInt("alarma", p.alarma);
            c.putInt("sigFoto", p.sigFoto);
            ListTag fs = new ListTag();
            for (FotoMeta f : p.fotos) {
                CompoundTag x = new CompoundTag();
                x.putInt("id", f.id());
                x.putLong("t", f.t());
                if (f.ms() > 0) x.putInt("ms", f.ms());
                fs.add(x);
            }
            c.put("fotos", fs);
            ListTag ll = new ListTag();
            for (Registro r : p.llamadas) {
                CompoundTag x = new CompoundTag();
                x.putUUID("o", r.otro());
                x.putLong("t", r.t());
                x.putByte("k", (byte) r.tipo());
                x.putInt("d", r.dur());
                x.putBoolean("v", r.video());
                ll.add(x);
            }
            c.put("llamadas", ll);
            CompoundTag le = new CompoundTag();
            p.leido.forEach((k, v) -> le.putLong(Integer.toString(k), v));
            c.put("leido", le);
            ListTag ts = new ListTag();
            p.tonos.forEach((k, v) -> {
                CompoundTag x = new CompoundTag();
                x.putUUID("u", k);
                x.putByte("t", (byte) (int) v);
                ts.add(x);
            });
            c.put("tonos", ts);
            ps.add(c);
        }
        t.put("perfiles", ps);
        ListTag cs = new ListTag();
        for (Chat ch : chats.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", ch.id);
            c.putBoolean("g", ch.grupo);
            c.putString("n", ch.nombre);
            c.putLong("sec", ch.sec);
            ListTag ms = new ListTag();
            for (UUID u : ch.miembros) {
                CompoundTag x = new CompoundTag();
                x.putUUID("u", u);
                ms.add(x);
            }
            c.put("miembros", ms);
            ListTag mm = new ListTag();
            for (Mensaje m : ch.msgs) {
                CompoundTag x = new CompoundTag();
                x.putLong("s", m.sec());
                x.putUUID("de", m.de());
                x.putLong("t", m.t());
                x.putString("x", m.texto());
                if (m.adjunto() >= 0) x.putInt("a", m.adjunto());
                // adjunto con vozMs y sin voz: es un vídeo corto (vozMs = su duración)
                if (m.voz() < 0 && m.vozMs() > 0) x.putInt("vms", m.vozMs());
                if (m.voz() >= 0) {
                    x.putInt("v", m.voz());
                    x.putInt("vms", m.vozMs());
                    x.putByteArray("onda", m.onda());
                }
                mm.add(x);
            }
            c.put("msgs", mm);
            cs.add(c);
        }
        t.put("chats", cs);
        t.putInt("sigChat", sigChat);
        t.putInt("sigAdjunto", sigAdjunto);
        t.putInt("sigVoz", sigVoz);
        return t;
    }

    private static MovilDatos cargar(CompoundTag t) {
        MovilDatos d = new MovilDatos();
        ListTag ps = t.getList("perfiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag c = ps.getCompound(i);
            if (!c.hasUUID("id")) continue;
            Perfil p = new Perfil(c.getUUID("id"));
            p.nombre = c.getString("n");
            p.numero = c.getString("num");
            p.silencio = c.getBoolean("sil");
            p.tono = c.getInt("tono");
            p.fondo = c.getInt("fondo");
            p.alarma = c.contains("alarma") ? c.getInt("alarma") : -1;
            p.sigFoto = Math.max(1, c.getInt("sigFoto"));
            ListTag fs = c.getList("fotos", Tag.TAG_COMPOUND);
            for (int j = 0; j < fs.size(); j++) p.fotos.add(new FotoMeta(fs.getCompound(j).getInt("id"), fs.getCompound(j).getLong("t"), fs.getCompound(j).getInt("ms")));
            ListTag ll = c.getList("llamadas", Tag.TAG_COMPOUND);
            for (int j = 0; j < ll.size(); j++) {
                CompoundTag x = ll.getCompound(j);
                if (x.hasUUID("o")) p.llamadas.add(new Registro(x.getUUID("o"), x.getLong("t"), x.getByte("k"), x.getInt("d"), x.getBoolean("v")));
            }
            ListTag ts = c.getList("tonos", Tag.TAG_COMPOUND);
            for (int j = 0; j < ts.size(); j++)
                if (ts.getCompound(j).hasUUID("u")) p.tonos.put(ts.getCompound(j).getUUID("u"), (int) ts.getCompound(j).getByte("t"));
            CompoundTag le = c.getCompound("leido");
            for (String k : le.getAllKeys()) {
                try {
                    p.leido.put(Integer.parseInt(k), le.getLong(k));
                } catch (NumberFormatException ignored) {
                }
            }
            d.perfiles.put(p.id, p);
        }
        ListTag cs = t.getList("chats", Tag.TAG_COMPOUND);
        for (int i = 0; i < cs.size(); i++) {
            CompoundTag c = cs.getCompound(i);
            Chat ch = new Chat(c.getInt("id"));
            ch.grupo = c.getBoolean("g");
            ch.nombre = c.getString("n");
            ch.sec = c.getLong("sec");
            ListTag ms = c.getList("miembros", Tag.TAG_COMPOUND);
            for (int j = 0; j < ms.size(); j++) if (ms.getCompound(j).hasUUID("u")) ch.miembros.add(ms.getCompound(j).getUUID("u"));
            ListTag mm = c.getList("msgs", Tag.TAG_COMPOUND);
            for (int j = 0; j < mm.size(); j++) {
                CompoundTag x = mm.getCompound(j);
                if (!x.hasUUID("de")) continue;
                ch.msgs.add(new Mensaje(x.getLong("s"), x.getUUID("de"), x.getLong("t"), x.getString("x"), x.contains("a") ? x.getInt("a") : -1,
                        x.contains("v") ? x.getInt("v") : -1, x.getInt("vms"), x.getByteArray("onda")));
            }
            d.chats.put(ch.id, ch);
        }
        d.sigChat = Math.max(1, t.getInt("sigChat"));
        d.sigAdjunto = Math.max(1, t.getInt("sigAdjunto"));
        d.sigVoz = Math.max(1, t.getInt("sigVoz"));
        for (Chat ch : d.chats.values()) d.sigChat = Math.max(d.sigChat, ch.id + 1);
        return d;
    }
}
