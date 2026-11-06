package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lo que el cliente sabe de Minegram (lo que ha pedido al servidor): feeds, la publicación abierta, un perfil y las notificaciones. */
public final class MinegramCliente {
    private MinegramCliente() {}

    public static final class Post {
        public int id, likes, comentarios;
        public UUID autor;
        public String nombre, pie;
        public long t;
        public boolean meGusta, puedeBorrar;
    }

    public record Comentario(int id, UUID autor, String nombre, long t, String texto, boolean borrable) {}

    public record Noti(int tipo, UUID de, String nombre, int post, boolean existe, long t, String texto, boolean nueva) {}

    public static final class Perfil {
        public UUID id;
        public String nombre = "";
        public int posts, seguidores, siguiendo;
        public boolean yoSigo;
        public final List<int[]> rejilla = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static final List<Post>[] FEED = new List[]{new ArrayList<>(), new ArrayList<>()};
    public static final boolean[] HAY_MAS = new boolean[2], CARGADO = new boolean[2];
    public static Post abierto;
    public static int abiertoId = -1;
    public static boolean abiertoNoExiste;
    public static final List<Comentario> COMENTARIOS = new ArrayList<>();
    public static Perfil perfil;
    public static final List<Noti> NOTIS = new ArrayList<>();
    public static boolean notisCargadas;
    public static int noVistas;

    static Post leerPost(FriendlyByteBuf b) {
        Post p = new Post();
        p.id = b.readVarInt();
        p.autor = b.readUUID();
        p.nombre = b.readUtf();
        p.t = b.readLong();
        p.pie = b.readUtf();
        p.likes = b.readVarInt();
        p.meGusta = b.readBoolean();
        p.comentarios = b.readVarInt();
        p.puedeBorrar = b.readBoolean();
        return p;
    }

    static void recibir(int tipo, FriendlyByteBuf b) {
        switch (tipo) {
            case MovilRed.S_MG_FEED -> {
                int modo = b.readByte() == 1 ? 1 : 0;
                int antes = b.readVarInt();
                HAY_MAS[modo] = b.readBoolean();
                int n = b.readVarInt();
                List<Post> l = new ArrayList<>();
                for (int i = 0; i < n; i++) l.add(leerPost(b));
                if (antes == 0) FEED[modo].clear();
                FEED[modo].addAll(l);
                CARGADO[modo] = true;
            }
            case MovilRed.S_MG_POST -> {
                int id = b.readVarInt();
                boolean existe = b.readBoolean();
                if (id != abiertoId) return;
                abiertoNoExiste = !existe;
                COMENTARIOS.clear();
                if (!existe) {
                    abierto = null;
                    return;
                }
                abierto = leerPost(b);
                int n = b.readVarInt();
                for (int i = 0; i < n; i++) COMENTARIOS.add(new Comentario(b.readVarInt(), b.readUUID(), b.readUtf(), b.readLong(), b.readUtf(), b.readBoolean()));
                // que el feed refleje lo último
                for (List<Post> l : FEED)
                    for (int i = 0; i < l.size(); i++) if (l.get(i).id == id) l.set(i, abierto);
            }
            case MovilRed.S_MG_PERFIL -> {
                Perfil p = new Perfil();
                p.id = b.readUUID();
                p.nombre = b.readUtf();
                p.posts = b.readVarInt();
                p.seguidores = b.readVarInt();
                p.siguiendo = b.readVarInt();
                p.yoSigo = b.readBoolean();
                int n = b.readVarInt();
                for (int i = 0; i < n; i++) p.rejilla.add(new int[]{b.readVarInt(), b.readVarInt()});
                perfil = p;
            }
            case MovilRed.S_MG_NOTIS -> {
                NOTIS.clear();
                int n = b.readVarInt();
                for (int i = 0; i < n; i++)
                    NOTIS.add(new Noti(b.readByte(), b.readUUID(), b.readUtf(), b.readVarInt(), b.readBoolean(), b.readLong(), b.readUtf(), b.readBoolean()));
                notisCargadas = true;
                noVistas = 0;
            }
            default -> {
            }
        }
    }

    static void pedirFeed(int modo, boolean mas) {
        List<Post> l = FEED[modo];
        int antes = mas && !l.isEmpty() ? l.get(l.size() - 1).id : 0;
        MovilRed.alServidor(MovilRed.C_MG_FEED, b -> {
            b.writeByte(modo);
            b.writeVarInt(antes);
        });
    }

    /** Me gusta / ya no (se ve al momento; el servidor lo guarda). */
    static void like(Post p, boolean si) {
        if (p.meGusta == si) return;
        p.meGusta = si;
        p.likes += si ? 1 : -1;
        int id = p.id;
        for (List<Post> l : FEED)
            for (Post x : l)
                if (x.id == id && x != p) {
                    x.meGusta = si;
                    x.likes = p.likes;
                }
        if (abierto != null && abierto.id == id && abierto != p) {
            abierto.meGusta = si;
            abierto.likes = p.likes;
        }
        MovilRed.alServidor(MovilRed.C_MG_LIKE, b -> {
            b.writeVarInt(id);
            b.writeBoolean(si);
        });
    }

    static void quitarDelFeed(int id) {
        for (List<Post> l : FEED) l.removeIf(p -> p.id == id);
    }

    static void limpiar() {
        for (List<Post> l : FEED) l.clear();
        HAY_MAS[0] = HAY_MAS[1] = CARGADO[0] = CARGADO[1] = false;
        abierto = null;
        abiertoId = -1;
        COMENTARIOS.clear();
        perfil = null;
        NOTIS.clear();
        notisCargadas = false;
        noVistas = 0;
    }
}
