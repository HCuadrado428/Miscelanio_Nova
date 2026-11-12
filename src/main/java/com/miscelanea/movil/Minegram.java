package com.miscelanea.movil;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minegram (lado servidor): publicar una foto de la galería con un pie, feed de todos o de a quién sigues, me gusta,
 * comentarios, seguir/dejar de seguir, perfil de cada jugador y notificaciones. Moderación: el autor de la publicación y
 * los operadores (nivel 2) pueden borrar publicaciones y comentarios; cada uno puede borrar sus comentarios.
 */
public final class Minegram {
    private Minegram() {}

    private static final Logger LOG = LogUtils.getLogger();
    public static final int PAGINA = 15, MAX_GRID = 90;
    private static final Map<UUID, Long> ULT_PUBLICAR = new HashMap<>(), ULT_COMENTAR = new HashMap<>();

    static MinegramDatos datos(ServerPlayer p) {
        return MinegramDatos.de(p.server);
    }

    static boolean op(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    static int noVistas(ServerPlayer p) {
        return MovilConfig.minegram() ? datos(p).noVistas(p.getUUID()) : 0;
    }

    static void recibir(ServerPlayer p, int tipo, FriendlyByteBuf b) {
        if (!MovilConfig.minegram()) return;
        switch (tipo) {
            case MovilRed.C_MG_FEED -> feed(p, b.readByte(), b.readVarInt());
            case MovilRed.C_MG_PUBLICAR -> publicar(p, b.readVarInt(), b.readUtf(512));
            case MovilRed.C_MG_LIKE -> like(p, b.readVarInt(), b.readBoolean());
            case MovilRed.C_MG_POST -> post(p, b.readVarInt());
            case MovilRed.C_MG_COMENTAR -> comentar(p, b.readVarInt(), b.readUtf(512));
            case MovilRed.C_MG_BORRAR -> borrar(p, b.readBoolean(), b.readVarInt(), b.readVarInt());
            case MovilRed.C_MG_PERFIL -> perfil(p, b.readUUID());
            case MovilRed.C_MG_SEGUIR -> seguir(p, b.readUUID(), b.readBoolean());
            case MovilRed.C_MG_NOTIS -> notis(p);
            default -> {
            }
        }
    }

    private static void resumen(ServerPlayer p, FriendlyByteBuf b, MinegramDatos.Post x) {
        b.writeVarInt(x.id);
        b.writeUUID(x.autor);
        b.writeUtf(MovilServidor.nombreDe(p.server, x.autor));
        b.writeLong(x.t);
        b.writeUtf(x.pie);
        b.writeVarInt(x.likes.size());
        b.writeBoolean(x.likes.contains(p.getUUID()));
        b.writeVarInt(x.comentarios.size());
        b.writeBoolean(x.autor.equals(p.getUUID()) || op(p));
    }

    /** Feed: modo 0 = todos, 1 = a quién sigues (y tú). antes = id de la última que tiene el cliente (0 = desde la más nueva). */
    private static void feed(ServerPlayer p, int modo, int antes) {
        MinegramDatos d = datos(p);
        Set<UUID> sigo = d.sigue(p.getUUID());
        List<MinegramDatos.Post> todas = new ArrayList<>(d.posts.values());
        List<MinegramDatos.Post> l = new ArrayList<>();
        boolean mas = false;
        for (int i = todas.size() - 1; i >= 0; i--) {
            MinegramDatos.Post x = todas.get(i);
            if (antes > 0 && x.id >= antes) continue;
            if (modo == 1 && !sigo.contains(x.autor) && !x.autor.equals(p.getUUID())) continue;
            if (l.size() >= PAGINA) {
                mas = true;
                break;
            }
            l.add(x);
        }
        boolean hayMas = mas;
        MovilRed.aJugador(p, MovilRed.S_MG_FEED, b -> {
            b.writeByte(modo);
            b.writeVarInt(antes);
            b.writeBoolean(hayMas);
            b.writeVarInt(l.size());
            for (MinegramDatos.Post x : l) resumen(p, b, x);
        });
    }

    private static void publicar(ServerPlayer p, int foto, String pie) {
        long ahora = p.server.getTickCount();
        Long ult = ULT_PUBLICAR.get(p.getUUID());
        if (ult != null && ahora - ult < 60) return;
        ULT_PUBLICAR.put(p.getUUID(), ahora);
        MinegramDatos d = datos(p);
        if (d.postsDe(p.getUUID()) >= MovilConfig.mgMaxPosts()) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.mg_lleno", Integer.toString(MovilConfig.mgMaxPosts()));
            return;
        }
        MovilDatos.Perfil yo = MovilServidor.datos(p.server).perfil(p.getUUID(), MovilServidor.nombre(p));
        if (yo.fotos.stream().noneMatch(f -> f.id() == foto)) return;
        int id = d.sigPost++;
        if (!Fotos.copiar(Fotos.propia(p.server, p.getUUID(), foto), Fotos.minegram(p.server, id))) return;
        MinegramDatos.Post x = new MinegramDatos.Post(id);
        x.autor = p.getUUID();
        x.t = System.currentTimeMillis();
        x.pie = MovilServidor.limpiar(pie, MovilConfig.mgMaxPie());
        d.posts.put(id, x);
        d.setDirty();
        LOG.info("[Minegram] {} publica la foto #{}", MovilServidor.nombre(p), id);
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "minegram");
        MovilServidor.aviso(p, "movil.miscelanea.aviso.mg_publicado", "");
        // aviso a sus seguidores conectados
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p && d.sigue(o.getUUID()).contains(p.getUUID()))
                MovilServidor.noti(o, MovilRed.APP_MINEGRAM, MovilServidor.nombre(p), MovilServidor.tr("mg_noti_post"), false);
    }

    private static void like(ServerPlayer p, int id, boolean si) {
        MinegramDatos d = datos(p);
        MinegramDatos.Post x = d.posts.get(id);
        if (x == null) return;
        boolean cambio = si ? x.likes.add(p.getUUID()) : x.likes.remove(p.getUUID());
        if (!cambio) return;
        d.setDirty();
        if (si && !x.autor.equals(p.getUUID())) {
            // una sola notificación de me gusta por persona y publicación
            List<MinegramDatos.Noti> l = d.notis.getOrDefault(x.autor, List.of());
            boolean ya = l.stream().anyMatch(n -> n.tipo() == MinegramDatos.N_LIKE && n.post() == id && n.de().equals(p.getUUID()));
            if (!ya) {
                d.noti(x.autor, new MinegramDatos.Noti(MinegramDatos.N_LIKE, p.getUUID(), id, System.currentTimeMillis(), ""));
                ServerPlayer a = p.server.getPlayerList().getPlayer(x.autor);
                if (a != null) MovilServidor.noti(a, MovilRed.APP_MINEGRAM, MovilServidor.nombre(p), MovilServidor.tr("mg_noti_like"), true);
            }
        }
    }

    private static void post(ServerPlayer p, int id) {
        MinegramDatos.Post x = datos(p).posts.get(id);
        boolean op = op(p);
        MovilRed.aJugador(p, MovilRed.S_MG_POST, b -> {
            b.writeVarInt(id);
            b.writeBoolean(x != null);
            if (x == null) return;
            resumen(p, b, x);
            b.writeVarInt(x.comentarios.size());
            for (MinegramDatos.Comentario c : x.comentarios) {
                b.writeVarInt(c.id());
                b.writeUUID(c.autor());
                b.writeUtf(MovilServidor.nombreDe(p.server, c.autor()));
                b.writeLong(c.t());
                b.writeUtf(c.texto());
                b.writeBoolean(op || c.autor().equals(p.getUUID()) || x.autor.equals(p.getUUID()));
            }
        });
    }

    private static void comentar(ServerPlayer p, int id, String texto) {
        long ahora = p.server.getTickCount();
        Long ult = ULT_COMENTAR.get(p.getUUID());
        if (ult != null && ahora - ult < 20) return;
        ULT_COMENTAR.put(p.getUUID(), ahora);
        MinegramDatos d = datos(p);
        MinegramDatos.Post x = d.posts.get(id);
        texto = MovilServidor.limpiar(texto, MovilConfig.mgMaxComentario());
        if (x == null || texto.isEmpty()) return;
        x.comentarios.add(new MinegramDatos.Comentario(x.sigComentario++, p.getUUID(), System.currentTimeMillis(), texto));
        while (x.comentarios.size() > MovilConfig.mgMaxComentarios()) x.comentarios.remove(0);
        d.setDirty();
        if (!x.autor.equals(p.getUUID())) {
            d.noti(x.autor, new MinegramDatos.Noti(MinegramDatos.N_COMENTARIO, p.getUUID(), id, System.currentTimeMillis(), texto));
            ServerPlayer a = p.server.getPlayerList().getPlayer(x.autor);
            if (a != null) MovilServidor.noti(a, MovilRed.APP_MINEGRAM, MovilServidor.nombre(p), MovilServidor.tr("mg_noti_comentario", texto), true);
        }
        post(p, id);
    }

    /** Borrar una publicación (comentario = false) o un comentario. */
    private static void borrar(ServerPlayer p, boolean comentario, int id, int com) {
        MinegramDatos d = datos(p);
        MinegramDatos.Post x = d.posts.get(id);
        if (x == null) return;
        boolean op = op(p), autor = x.autor.equals(p.getUUID());
        if (!comentario) {
            if (!autor && !op) return;
            d.posts.remove(id);
            Fotos.borrar(Fotos.minegram(p.server, id));
            d.setDirty();
            LOG.info("[Minegram] {} borra la publicación #{} de {}", MovilServidor.nombre(p), id, MovilServidor.nombreDe(p.server, x.autor));
            return;
        }
        boolean quitado = x.comentarios.removeIf(c -> c.id() == com && (op || autor || c.autor().equals(p.getUUID())));
        if (quitado) {
            d.setDirty();
            if (op && !autor) LOG.info("[Minegram] {} borra un comentario de la publicación #{}", MovilServidor.nombre(p), id);
        }
        post(p, id);
    }

    private static void perfil(ServerPlayer p, UUID u) {
        MinegramDatos d = datos(p);
        MovilDatos.Perfil pf = MovilServidor.datos(p.server).perfiles.get(u);
        List<MinegramDatos.Post> suyos = new ArrayList<>();
        List<MinegramDatos.Post> todas = new ArrayList<>(d.posts.values());
        for (int i = todas.size() - 1; i >= 0 && suyos.size() < MAX_GRID; i--) if (todas.get(i).autor.equals(u)) suyos.add(todas.get(i));
        MovilRed.aJugador(p, MovilRed.S_MG_PERFIL, b -> {
            b.writeUUID(u);
            b.writeUtf(pf == null ? "?" : pf.nombre);
            b.writeVarInt(d.postsDe(u));
            b.writeVarInt(d.seguidores(u));
            b.writeVarInt(d.sigue(u).size());
            b.writeBoolean(d.sigue(p.getUUID()).contains(u));
            b.writeVarInt(suyos.size());
            for (MinegramDatos.Post x : suyos) {
                b.writeVarInt(x.id);
                b.writeVarInt(x.likes.size());
            }
        });
    }

    private static void seguir(ServerPlayer p, UUID u, boolean si) {
        MinegramDatos d = datos(p);
        if (u.equals(p.getUUID()) || !MovilServidor.datos(p.server).perfiles.containsKey(u)) return;
        boolean cambio = si ? d.sigue(p.getUUID()).add(u) : d.sigue(p.getUUID()).remove(u);
        if (cambio) {
            d.setDirty();
            if (si) {
                d.noti(u, new MinegramDatos.Noti(MinegramDatos.N_SEGUIR, p.getUUID(), 0, System.currentTimeMillis(), ""));
                ServerPlayer o = p.server.getPlayerList().getPlayer(u);
                if (o != null) MovilServidor.noti(o, MovilRed.APP_MINEGRAM, MovilServidor.nombre(p), MovilServidor.tr("mg_noti_seguir"), true);
            }
        }
        perfil(p, u);
    }

    private static void notis(ServerPlayer p) {
        MinegramDatos d = datos(p);
        List<MinegramDatos.Noti> l = d.notis.getOrDefault(p.getUUID(), List.of());
        long visto = d.visto.getOrDefault(p.getUUID(), 0L);
        MovilRed.aJugador(p, MovilRed.S_MG_NOTIS, b -> {
            b.writeVarInt(l.size());
            for (MinegramDatos.Noti n : l) {
                b.writeByte(n.tipo());
                b.writeUUID(n.de());
                b.writeUtf(MovilServidor.nombreDe(p.server, n.de()));
                b.writeVarInt(n.post());
                b.writeBoolean(n.post() > 0 && d.posts.containsKey(n.post()));
                b.writeLong(n.t());
                b.writeUtf(n.texto());
                b.writeBoolean(n.t() > visto);
            }
        });
        d.visto.put(p.getUUID(), System.currentTimeMillis());
        d.setDirty();
    }
}
