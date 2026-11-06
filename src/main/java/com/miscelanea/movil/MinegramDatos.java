package com.miscelanea.movil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de Minegram que se guardan con el mundo ({@code data/miscelanea_minegram.dat}): publicaciones (con sus me gusta y
 * comentarios), a quién sigue cada uno y las notificaciones. Las fotos van en {@code miscelanea_movil/minegram/<id>.jpg}.
 */
public class MinegramDatos extends SavedData {
    private static final String NOMBRE = "miscelanea_minegram";
    public static final int MAX_NOTIS = 60;
    /** Tipos de notificación. */
    public static final int N_LIKE = 0, N_COMENTARIO = 1, N_SEGUIR = 2;

    public record Comentario(int id, UUID autor, long t, String texto) {}

    public record Noti(int tipo, UUID de, int post, long t, String texto) {}

    public static final class Post {
        public final int id;
        public UUID autor;
        public long t;
        public String pie = "";
        public final Set<UUID> likes = new LinkedHashSet<>();
        public final List<Comentario> comentarios = new ArrayList<>();
        public int sigComentario = 1;

        Post(int id) {
            this.id = id;
        }
    }

    /** Ordenadas de la más vieja a la más nueva. */
    public final LinkedHashMap<Integer, Post> posts = new LinkedHashMap<>();
    public final Map<UUID, Set<UUID>> siguiendo = new HashMap<>();
    public final Map<UUID, List<Noti>> notis = new HashMap<>();
    public final Map<UUID, Long> visto = new HashMap<>();
    public int sigPost = 1;

    public static MinegramDatos de(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(MinegramDatos::cargar, MinegramDatos::new, NOMBRE);
    }

    public Set<UUID> sigue(UUID u) {
        return siguiendo.computeIfAbsent(u, k -> new LinkedHashSet<>());
    }

    public int seguidores(UUID u) {
        int n = 0;
        for (Set<UUID> s : siguiendo.values()) if (s.contains(u)) n++;
        return n;
    }

    public int postsDe(UUID u) {
        int n = 0;
        for (Post p : posts.values()) if (p.autor.equals(u)) n++;
        return n;
    }

    public void noti(UUID para, Noti n) {
        List<Noti> l = notis.computeIfAbsent(para, k -> new ArrayList<>());
        l.add(0, n);
        while (l.size() > MAX_NOTIS) l.remove(l.size() - 1);
        setDirty();
    }

    public int noVistas(UUID u) {
        long v = visto.getOrDefault(u, 0L);
        int n = 0;
        for (Noti x : notis.getOrDefault(u, List.of())) if (x.t() > v) n++;
        return n;
    }

    // ------------------------------------------------------------------ NBT
    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag ps = new ListTag();
        for (Post p : posts.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", p.id);
            c.putUUID("a", p.autor);
            c.putLong("t", p.t);
            c.putString("pie", p.pie);
            c.putInt("sigC", p.sigComentario);
            ListTag ls = new ListTag();
            for (UUID u : p.likes) {
                CompoundTag x = new CompoundTag();
                x.putUUID("u", u);
                ls.add(x);
            }
            c.put("likes", ls);
            ListTag cs = new ListTag();
            for (Comentario k : p.comentarios) {
                CompoundTag x = new CompoundTag();
                x.putInt("id", k.id());
                x.putUUID("a", k.autor());
                x.putLong("t", k.t());
                x.putString("x", k.texto());
                cs.add(x);
            }
            c.put("coms", cs);
            ps.add(c);
        }
        t.put("posts", ps);
        ListTag sg = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> e : siguiendo.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.putUUID("u", e.getKey());
            ListTag l = new ListTag();
            for (UUID u : e.getValue()) {
                CompoundTag x = new CompoundTag();
                x.putUUID("u", u);
                l.add(x);
            }
            c.put("s", l);
            sg.add(c);
        }
        t.put("siguiendo", sg);
        ListTag ns = new ListTag();
        for (Map.Entry<UUID, List<Noti>> e : notis.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", e.getKey());
            c.putLong("visto", visto.getOrDefault(e.getKey(), 0L));
            ListTag l = new ListTag();
            for (Noti n : e.getValue()) {
                CompoundTag x = new CompoundTag();
                x.putByte("k", (byte) n.tipo());
                x.putUUID("de", n.de());
                x.putInt("p", n.post());
                x.putLong("t", n.t());
                x.putString("x", n.texto());
                l.add(x);
            }
            c.put("l", l);
            ns.add(c);
        }
        t.put("notis", ns);
        t.putInt("sigPost", sigPost);
        return t;
    }

    private static MinegramDatos cargar(CompoundTag t) {
        MinegramDatos d = new MinegramDatos();
        ListTag ps = t.getList("posts", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag c = ps.getCompound(i);
            if (!c.hasUUID("a")) continue;
            Post p = new Post(c.getInt("id"));
            p.autor = c.getUUID("a");
            p.t = c.getLong("t");
            p.pie = c.getString("pie");
            p.sigComentario = Math.max(1, c.getInt("sigC"));
            ListTag ls = c.getList("likes", Tag.TAG_COMPOUND);
            for (int j = 0; j < ls.size(); j++) if (ls.getCompound(j).hasUUID("u")) p.likes.add(ls.getCompound(j).getUUID("u"));
            ListTag cs = c.getList("coms", Tag.TAG_COMPOUND);
            for (int j = 0; j < cs.size(); j++) {
                CompoundTag x = cs.getCompound(j);
                if (x.hasUUID("a")) p.comentarios.add(new Comentario(x.getInt("id"), x.getUUID("a"), x.getLong("t"), x.getString("x")));
            }
            d.posts.put(p.id, p);
            d.sigPost = Math.max(d.sigPost, p.id + 1);
        }
        ListTag sg = t.getList("siguiendo", Tag.TAG_COMPOUND);
        for (int i = 0; i < sg.size(); i++) {
            CompoundTag c = sg.getCompound(i);
            if (!c.hasUUID("u")) continue;
            Set<UUID> s = d.sigue(c.getUUID("u"));
            ListTag l = c.getList("s", Tag.TAG_COMPOUND);
            for (int j = 0; j < l.size(); j++) if (l.getCompound(j).hasUUID("u")) s.add(l.getCompound(j).getUUID("u"));
        }
        ListTag ns = t.getList("notis", Tag.TAG_COMPOUND);
        for (int i = 0; i < ns.size(); i++) {
            CompoundTag c = ns.getCompound(i);
            if (!c.hasUUID("u")) continue;
            UUID u = c.getUUID("u");
            d.visto.put(u, c.getLong("visto"));
            List<Noti> l = new ArrayList<>();
            ListTag ll = c.getList("l", Tag.TAG_COMPOUND);
            for (int j = 0; j < ll.size(); j++) {
                CompoundTag x = ll.getCompound(j);
                if (x.hasUUID("de")) l.add(new Noti(x.getByte("k"), x.getUUID("de"), x.getInt("p"), x.getLong("t"), x.getString("x")));
            }
            d.notis.put(u, l);
        }
        d.sigPost = Math.max(d.sigPost, t.getInt("sigPost"));
        return d;
    }
}
