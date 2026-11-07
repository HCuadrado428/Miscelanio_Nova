package com.miscelanea.movil;

import com.miscelanea.Miscelanea;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado servidor del móvil: perfiles y números, chats (individuales y de grupo) con historial persistente, la galería de
 * fotos de cada jugador, la pose de "mirar el móvil" para los demás y el reparto de los paquetes. Las llamadas están en
 * {@link Llamadas}.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class MovilServidor {
    private MovilServidor() {}

    public static final int MAX_TEXTO = 256, MAX_NOMBRE_GRUPO = 24, MAX_MIEMBROS = 16;
    private static final Map<UUID, Integer> POSES = new HashMap<>();
    private static final Map<UUID, Long> ULT_MENSAJE = new HashMap<>(), ULT_FOTO = new HashMap<>();

    public static boolean tieneMovil(ServerPlayer p) {
        return p.getInventory().hasAnyMatching(MovilRegistro::esMovil);
    }

    /**
     * Nombre que se ve en el móvil: el que muestra el juego (si un mod como Fake Name le ha puesto otro, ese), sin
     * colores ni equipos.
     */
    static String nombre(ServerPlayer p) {
        String n;
        try {
            n = net.minecraftforge.event.ForgeEventFactory.getPlayerDisplayName(p, net.minecraft.network.chat.Component.literal(p.getGameProfile().getName())).getString();
        } catch (RuntimeException e) {
            n = p.getGameProfile().getName();
        }
        n = limpiar(n.replaceAll("§.", ""), 32);
        return n.isEmpty() ? p.getGameProfile().getName() : n;
    }

    /** Pone al día el nombre guardado (si se lo ha cambiado con Fake Name, por ejemplo). Devuelve si cambió. */
    static boolean refrescarNombre(ServerPlayer p) {
        MovilDatos.Perfil pf = datos(p.server).perfil(p.getUUID(), nombre(p));
        String n = nombre(p);
        if (n.equals(pf.nombre)) return false;
        pf.nombre = n;
        datos(p.server).setDirty();
        return true;
    }

    static MovilDatos datos(MinecraftServer s) {
        return MovilDatos.de(s);
    }

    static String nombreDe(MinecraftServer s, UUID u) {
        MovilDatos.Perfil p = datos(s).perfiles.get(u);
        return p == null ? "?" : p.nombre;
    }

    /** Texto de un mensaje: sin códigos de formato, sin emojis (pares sustitutos) ni caracteres raros, y recortado. */
    public static String limpiar(String s, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (Character.isSurrogate(c) || c == '§' || !SharedConstants.isAllowedChatCharacter(c)) continue;
            b.append(c);
        }
        return b.toString().trim();
    }

    // ------------------------------------------------------------------ eventos
    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            datos(p.server).perfil(p.getUUID(), nombre(p));
            sync(p);
        }
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Llamadas.salir(p);
            NotasVoz.salir(p);
            Clips.salir(p);
            Ubicaciones.salir(p);
            LinternaLuz.apagar(p);
            POSES.remove(p.getUUID());
            ULT_MENSAJE.remove(p.getUUID());
            ULT_FOTO.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void verJugador(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer otro && e.getEntity() instanceof ServerPlayer yo) {
            Integer m = POSES.get(otro.getUUID());
            if (m != null && m != MovilRed.POSE_NADA)
                MovilRed.aJugador(yo, MovilRed.S_POSE, b -> {
                    b.writeVarInt(otro.getId());
                    b.writeByte(m);
                });
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer() != null) {
            Llamadas.tick(e.getServer());
            if (e.getServer().getTickCount() % 100 == 37) {
                boolean cambio = false;
                for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) cambio |= refrescarNombre(p);
                if (cambio) for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) if (tieneMovil(p)) sync(p);
            }
            Ubicaciones.tick(e.getServer());
            LinternaLuz.tick(e.getServer());
        }
    }

    // ------------------------------------------------------------------ paquetes
    static void recibir(ServerPlayer p, int tipo, FriendlyByteBuf b) {
        try {
            switch (tipo) {
                case MovilRed.C_POSE -> pose(p, b.readByte());
                case MovilRed.C_SYNC -> sync(p);
                case MovilRed.C_CHAT_ABRIR -> abrirChat(p, b.readVarInt());
                case MovilRed.C_CHAT_NUEVO -> nuevoChat(p, b);
                case MovilRed.C_MENSAJE -> mensaje(p, b.readVarInt(), b.readUtf(MAX_TEXTO * 2), b.readVarInt());
                case MovilRed.C_FOTO_SUBIR -> subirFoto(p, b.readByteArray(MovilConfig.TOPE_BYTES_FOTO + 16));
                case MovilRed.C_FOTOS -> enviarFotos(p);
                case MovilRed.C_FOTO_PEDIR -> pedirFoto(p, b.readByte(), b.readVarInt());
                case MovilRed.C_FOTO_BORRAR -> borrarFoto(p, b.readVarInt());
                case MovilRed.C_LLAMAR -> Llamadas.llamar(p, b.readUUID(), b.readBoolean(), b.readBoolean());
                case MovilRed.C_LLAMAR_NUMERO -> {
                    String num = b.readUtf(8);
                    boolean video = b.readBoolean(), oculto = b.readBoolean();
                    MovilDatos.Perfil o = datos(p.server).porNumero(num);
                    if (o == null) aviso(p, "movil.miscelanea.aviso.no_existe", num);
                    else Llamadas.llamar(p, o.id, video, oculto);
                }
                case MovilRed.C_LLAMAR_GRUPO -> Llamadas.llamarGrupo(p, b.readVarInt(), b.readBoolean());
                case MovilRed.C_SILENCIAR -> Llamadas.silenciar(p, b.readBoolean());
                case MovilRed.C_ALTAVOZ -> Llamadas.altavoz(p, b.readBoolean());
                case MovilRed.C_CAMBIAR_LLAMADA -> Llamadas.cambiar(p);
                case MovilRed.C_TONO_CONTACTO -> tonoContacto(p, b.readUUID(), b.readByte());
                case MovilRed.C_CLIP_TROZO -> Clips.trozo(p, b);
                case MovilRed.C_CLIP_PEDIR -> Clips.pedir(p, b.readByte(), b.readVarInt());
                case MovilRed.C_FLASH -> LinternaLuz.flash(p);
                case MovilRed.C_CONTESTAR -> Llamadas.contestar(p, b.readBoolean());
                case MovilRed.C_COLGAR -> Llamadas.colgar(p);
                case MovilRed.C_VIDEO -> Llamadas.video(p, b.readByteArray(32000));
                case MovilRed.C_AJUSTES -> ajustes(p, b.readBoolean(), b.readByte(), b.readByte(), b.readInt());
                case MovilRed.C_CHAT_LEIDO -> leido(p, b.readVarInt());
                case MovilRed.C_CHAT_SALIR -> salirChat(p, b.readVarInt());
                case MovilRed.C_VOZ_TROZO -> NotasVoz.trozo(p, b);
                case MovilRed.C_VOZ_PEDIR -> NotasVoz.pedir(p, b.readVarInt());
                case MovilRed.C_UBI_COMPARTIR -> Ubicaciones.compartir(p, b);
                case MovilRed.C_UBI_PARAR -> Ubicaciones.parar(p, b);
                case MovilRed.C_MG_FEED, MovilRed.C_MG_PUBLICAR, MovilRed.C_MG_LIKE, MovilRed.C_MG_POST, MovilRed.C_MG_COMENTAR,
                     MovilRed.C_MG_BORRAR, MovilRed.C_MG_PERFIL, MovilRed.C_MG_SEGUIR, MovilRed.C_MG_NOTIS -> Minegram.recibir(p, tipo, b);
                case MovilRed.C_BANCO, MovilRed.C_BANCO_INGRESAR, MovilRed.C_BANCO_RETIRAR, MovilRed.C_BIZUM, MovilRed.C_ENVIAR_OBJETO,
                     MovilRed.C_FICHAS ->
                        Banco.recibir(p, tipo, b);
                case MovilRed.C_LINTERNA -> LinternaLuz.cambiar(p, b.readBoolean());
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            // paquete mal formado: se ignora
        }
    }

    /** Clave de traducción + argumentos para una notificación ({@code \u0001clave\u0001arg...}; la traduce el cliente). */
    public static String tr(String clave, Object... args) {
        StringBuilder b = new StringBuilder("\u0001").append(clave);
        for (Object a : args) b.append('\u0001').append(a);
        return b.toString();
    }

    /** Notificación genérica (banner dentro del móvil o aviso con él cerrado): icono de app, título y texto. */
    public static void noti(ServerPlayer p, int app, String titulo, String texto, boolean sonar) {
        if (!tieneMovil(p)) return;
        MovilRed.aJugador(p, MovilRed.S_NOTI, b -> {
            b.writeByte(app);
            b.writeUtf(titulo);
            b.writeUtf(texto);
            b.writeBoolean(sonar);
        });
        if (sonar)
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), MovilRegistro.VIBRAR.get(), SoundSource.PLAYERS, datos(p.server).perfil(p.getUUID(), null).silencio ? 0.25F : 0.4F, 1.0F);
    }

    public static void aviso(ServerPlayer p, String clave, String arg) {
        MovilRed.aJugador(p, MovilRed.S_AVISO, b -> {
            b.writeUtf(clave);
            b.writeUtf(arg == null ? "" : arg);
        });
    }

    private static void pose(ServerPlayer p, int modo) {
        if (modo < 0 || modo > 2) return;
        Integer antes = POSES.put(p.getUUID(), modo);
        if (antes != null && antes == modo) return;
        MovilRed.aQuienLoVe(p, MovilRed.S_POSE, b -> {
            b.writeVarInt(p.getId());
            b.writeByte(modo);
        });
    }

    // ------------------------------------------------------------------ sync
    private static void tonoContacto(ServerPlayer p, UUID otro, int tono) {
        MovilDatos d = datos(p.server);
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), nombre(p));
        if (tono < 0 || tono >= MovilRegistro.TONOS) yo.tonos.remove(otro);
        else if (d.perfiles.containsKey(otro)) yo.tonos.put(otro, tono);
        d.setDirty();
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        refrescarNombre(p);
        MovilDatos d = datos(p.server);
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), nombre(p));
        List<MovilDatos.Chat> chats = d.chatsDe(p.getUUID());
        chats.sort(Comparator.comparingLong((MovilDatos.Chat c) -> c.ultimo() == null ? 0 : c.ultimo().t()).reversed());
        MovilRed.aJugador(p, MovilRed.S_SYNC, b -> {
            b.writeUtf(yo.numero);
            b.writeBoolean(yo.silencio);
            b.writeByte(yo.tono);
            b.writeByte(yo.fondo);
            b.writeInt(yo.alarma);
            b.writeBoolean(MovilVoz.disponible());
            b.writeBoolean(MovilConfig.video());
            List<MovilDatos.Perfil> otros = new ArrayList<>();
            for (MovilDatos.Perfil o : d.perfiles.values()) if (!o.id.equals(yo.id)) otros.add(o);
            b.writeVarInt(otros.size());
            for (MovilDatos.Perfil o : otros) {
                b.writeUUID(o.id);
                b.writeUtf(o.nombre);
                b.writeUtf(o.numero);
                b.writeBoolean(p.server.getPlayerList().getPlayer(o.id) != null);
                b.writeByte(yo.tonos.getOrDefault(o.id, -1));
            }
            b.writeVarInt(chats.size());
            for (MovilDatos.Chat c : chats) {
                b.writeVarInt(c.id);
                b.writeBoolean(c.grupo);
                b.writeUtf(nombreChat(p.server, c, yo.id));
                b.writeVarInt(c.miembros.size());
                for (UUID u : c.miembros) b.writeUUID(u);
                MovilDatos.Mensaje u = c.ultimo();
                b.writeBoolean(u != null);
                if (u != null) escribirMensaje(p.server, b, u);
                b.writeVarInt(d.noLeidos(c, yo.id));
            }
            b.writeVarInt(yo.llamadas.size());
            for (MovilDatos.Registro r : yo.llamadas) {
                b.writeUUID(r.otro());
                b.writeUtf(r.otro().equals(Llamadas.OCULTO) ? "" : nombreDe(p.server, r.otro()));
                b.writeLong(r.t());
                b.writeByte(r.tipo());
                b.writeVarInt(r.dur());
                b.writeBoolean(r.video());
            }
            // funciones nuevas (protocolo 2)
            b.writeBoolean(MovilConfig.notasVoz() && MovilVoz.disponible());
            b.writeVarInt(MovilConfig.vozSegundos());
            b.writeVarInt(MovilConfig.vozMaxBytes());
            b.writeBoolean(MovilConfig.minegram());
            b.writeVarInt(Minegram.noVistas(p));
            b.writeBoolean(MovilConfig.ubicacion());
            b.writeBoolean(MovilConfig.banco());
            b.writeBoolean(MovilConfig.bancoObjetos());
            b.writeBoolean(MovilConfig.linternaReal());
        });
    }

    static String nombreChat(MinecraftServer s, MovilDatos.Chat c, UUID para) {
        if (c.grupo) return c.nombre;
        for (UUID u : c.miembros) if (!u.equals(para)) return nombreDe(s, u);
        return c.nombre;
    }

    static void escribirMensaje(MinecraftServer s, FriendlyByteBuf b, MovilDatos.Mensaje m) {
        b.writeLong(m.sec());
        b.writeUUID(m.de());
        b.writeUtf(nombreDe(s, m.de()));
        b.writeLong(m.t());
        b.writeUtf(m.texto());
        b.writeVarInt(m.adjunto() + 1);
        b.writeVarInt(m.voz() + 1);
        b.writeVarInt(m.vozMs());
        b.writeByteArray(m.onda());
    }

    /** Borra los archivos (foto/nota de voz) de un mensaje que desaparece. */
    static void borrarArchivos(MinecraftServer s, MovilDatos.Mensaje m) {
        if (m.adjunto() >= 0) Fotos.borrar(Fotos.adjunto(s, m.adjunto()));
        if (m.adjunto() >= 0 && m.voz() < 0 && m.vozMs() > 0) Fotos.borrar(Fotos.clipAdjunto(s, m.adjunto()));
        if (m.voz() >= 0) Fotos.borrar(Fotos.voz(s, m.voz()));
    }

    static MovilDatos.Chat chatDeJugador(ServerPlayer p, int id) {
        return chatDe(p, id);
    }

    // ------------------------------------------------------------------ chats
    private static MovilDatos.Chat chatDe(ServerPlayer p, int id) {
        MovilDatos.Chat c = datos(p.server).chats.get(id);
        return c != null && c.miembros.contains(p.getUUID()) ? c : null;
    }

    private static void abrirChat(ServerPlayer p, int id) {
        MovilDatos d = datos(p.server);
        MovilDatos.Chat c = chatDe(p, id);
        if (c == null) return;
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), nombre(p));
        yo.leido.put(c.id, c.sec);
        d.setDirty();
        int desde = Math.max(0, c.msgs.size() - 200);
        MovilRed.aJugador(p, MovilRed.S_CHAT, b -> {
            b.writeVarInt(c.id);
            b.writeBoolean(c.grupo);
            b.writeUtf(nombreChat(p.server, c, p.getUUID()));
            b.writeVarInt(c.miembros.size());
            for (UUID u : c.miembros) {
                b.writeUUID(u);
                b.writeUtf(nombreDe(p.server, u));
            }
            b.writeVarInt(c.msgs.size() - desde);
            for (int i = desde; i < c.msgs.size(); i++) escribirMensaje(p.server, b, c.msgs.get(i));
        });
    }

    private static void nuevoChat(ServerPlayer p, FriendlyByteBuf b) {
        String nombre = limpiar(b.readUtf(MAX_NOMBRE_GRUPO * 2), MAX_NOMBRE_GRUPO);
        int n = Math.min(MAX_MIEMBROS, b.readVarInt());
        MovilDatos d = datos(p.server);
        List<UUID> miembros = new ArrayList<>();
        miembros.add(p.getUUID());
        for (int i = 0; i < n; i++) {
            UUID u = b.readUUID();
            if (!miembros.contains(u) && d.perfiles.containsKey(u)) miembros.add(u);
        }
        if (miembros.size() < 2) return;
        MovilDatos.Chat c = miembros.size() == 2 ? d.individual(miembros.get(0), miembros.get(1)) : null;
        if (c == null) {
            if (d.chatsDe(p.getUUID()).size() >= MovilConfig.maxChats()) {
                aviso(p, "movil.miscelanea.aviso.muchos_chats", Integer.toString(MovilConfig.maxChats()));
                return;
            }
            boolean grupo = miembros.size() > 2;
            if (grupo && nombre.isEmpty()) {
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < Math.min(3, miembros.size()); i++) s.append(i > 0 ? ", " : "").append(nombreDe(p.server, miembros.get(i)));
                nombre = limpiar(s.toString(), MAX_NOMBRE_GRUPO);
            }
            c = d.nuevoChat(grupo, nombre, miembros);
            for (UUID u : miembros) {
                ServerPlayer o = p.server.getPlayerList().getPlayer(u);
                if (o != null && o != p) sync(o);
            }
        }
        int id = c.id;
        MovilRed.aJugador(p, MovilRed.S_CHAT_ID, w -> w.writeVarInt(id));
    }

    private static void mensaje(ServerPlayer p, int id, String texto, int fotoId) {
        MovilDatos d = datos(p.server);
        MovilDatos.Chat c = chatDe(p, id);
        if (c == null) return;
        long ahora = p.server.getTickCount();
        Long ult = ULT_MENSAJE.get(p.getUUID());
        if (ult != null && ahora - ult < 2) return;
        ULT_MENSAJE.put(p.getUUID(), ahora);
        texto = limpiar(texto, MAX_TEXTO);
        int adj = -1, clipMs = 0;
        if (fotoId > 0) {
            MovilDatos.Perfil yo = d.perfil(p.getUUID(), nombre(p));
            boolean es = yo.fotos.stream().anyMatch(f -> f.id() == fotoId);
            if (es) {
                int a = d.sigAdjunto++;
                if (Fotos.copiar(Fotos.propia(p.server, p.getUUID(), fotoId), Fotos.adjunto(p.server, a))) adj = a;
                int ms = Clips.msDe(yo, fotoId);
                // vídeo corto: se copia también el vídeo (si falla, va solo la miniatura)
                if (adj >= 0 && ms > 0 && Fotos.copiar(Fotos.clipPropio(p.server, p.getUUID(), fotoId), Fotos.clipAdjunto(p.server, a))) clipMs = ms;
            }
        }
        if (texto.isEmpty() && adj < 0) return;
        difundir(p, c, new MovilDatos.Mensaje(++c.sec, p.getUUID(), System.currentTimeMillis(), texto, adj, -1, clipMs, new byte[0]));
    }

    /** Añade el mensaje (ya con su número) al chat, recorta el historial y se lo manda a los miembros conectados. */
    static void difundir(ServerPlayer p, MovilDatos.Chat c, MovilDatos.Mensaje m) {
        MovilDatos d = datos(p.server);
        c.msgs.add(m);
        while (c.msgs.size() > MovilConfig.historial()) borrarArchivos(p.server, c.msgs.remove(0));
        d.perfil(p.getUUID(), nombre(p)).leido.put(c.id, c.sec);
        d.setDirty();
        for (UUID u : c.miembros) {
            ServerPlayer o = p.server.getPlayerList().getPlayer(u);
            if (o == null) continue;
            boolean avisar = o != p && tieneMovil(o);
            boolean silencio = d.perfil(u, null).silencio;
            MovilRed.aJugador(o, MovilRed.S_MENSAJE, b -> {
                b.writeVarInt(c.id);
                b.writeBoolean(c.grupo);
                b.writeUtf(nombreChat(p.server, c, u));
                escribirMensaje(p.server, b, m);
                b.writeBoolean(avisar);
            });
            if (avisar)
                o.level().playSound(null, o.getX(), o.getY(), o.getZ(), MovilRegistro.VIBRAR.get(), SoundSource.PLAYERS, silencio ? 0.25F : 0.45F, 1.0F);
        }
    }

    private static void leido(ServerPlayer p, int id) {
        MovilDatos.Chat c = chatDe(p, id);
        if (c == null) return;
        MovilDatos d = datos(p.server);
        d.perfil(p.getUUID(), nombre(p)).leido.put(c.id, c.sec);
        d.setDirty();
    }

    private static void salirChat(ServerPlayer p, int id) {
        MovilDatos d = datos(p.server);
        MovilDatos.Chat c = chatDe(p, id);
        if (c == null) return;
        c.miembros.remove(p.getUUID());
        d.perfil(p.getUUID(), nombre(p)).leido.remove(c.id);
        if (c.miembros.size() < (c.grupo ? 1 : 2)) {
            for (MovilDatos.Mensaje m : c.msgs) borrarArchivos(p.server, m);
            d.chats.remove(c.id);
            for (UUID u : c.miembros) d.perfil(u, null).leido.remove(c.id);
        }
        d.setDirty();
        sync(p);
    }

    // ------------------------------------------------------------------ fotos
    private static void subirFoto(ServerPlayer p, byte[] img) {
        long ahora = p.server.getTickCount();
        Long ult = ULT_FOTO.get(p.getUUID());
        if (ult != null && ahora - ult < 10) return;
        ULT_FOTO.put(p.getUUID(), ahora);
        if (!Fotos.valida(img, MovilConfig.maxBytesFoto())) {
            aviso(p, "movil.miscelanea.aviso.foto_grande", "");
            return;
        }
        MovilDatos d = datos(p.server);
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), nombre(p));
        if (yo.fotos.size() >= MovilConfig.maxFotos()) {
            aviso(p, "movil.miscelanea.aviso.galeria_llena", Integer.toString(MovilConfig.maxFotos()));
            return;
        }
        int id = yo.sigFoto++;
        if (!Fotos.escribir(Fotos.propia(p.server, p.getUUID(), id), img)) return;
        yo.fotos.add(new MovilDatos.FotoMeta(id, System.currentTimeMillis()));
        d.setDirty();
        p.level().playSound(p, p.getX(), p.getY(), p.getZ(), MovilRegistro.OBTURADOR.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
        enviarFotos(p);
    }

    static void enviarFotos(ServerPlayer p) {
        MovilDatos.Perfil yo = datos(p.server).perfil(p.getUUID(), nombre(p));
        MovilRed.aJugador(p, MovilRed.S_FOTOS, b -> {
            b.writeVarInt(MovilConfig.maxFotos());
            b.writeVarInt(MovilConfig.maxBytesFoto());
            b.writeBoolean(MovilConfig.clips());
            b.writeVarInt(MovilConfig.clipSegundos());
            b.writeVarInt(MovilConfig.clipMaxBytes());
            b.writeVarInt(yo.fotos.size());
            for (int i = yo.fotos.size() - 1; i >= 0; i--) {
                b.writeVarInt(yo.fotos.get(i).id());
                b.writeLong(yo.fotos.get(i).t());
                b.writeVarInt(yo.fotos.get(i).ms());
            }
        });
    }

    private static void pedirFoto(ServerPlayer p, int tipo, int id) {
        byte[] img = null;
        if (tipo == MovilRed.FOTO_PROPIA) {
            MovilDatos.Perfil yo = datos(p.server).perfil(p.getUUID(), nombre(p));
            if (yo.fotos.stream().anyMatch(f -> f.id() == id)) img = Fotos.leer(Fotos.propia(p.server, p.getUUID(), id));
        } else if (tipo == MovilRed.FOTO_ADJUNTA) {
            boolean puede = false;
            for (MovilDatos.Chat c : datos(p.server).chatsDe(p.getUUID())) {
                for (MovilDatos.Mensaje m : c.msgs)
                    if (m.adjunto() == id) {
                        puede = true;
                        break;
                    }
                if (puede) break;
            }
            if (puede) img = Fotos.leer(Fotos.adjunto(p.server, id));
        } else if (tipo == MovilRed.FOTO_MINEGRAM) {
            if (MinegramDatos.de(p.server).posts.containsKey(id)) img = Fotos.leer(Fotos.minegram(p.server, id));
        }
        byte[] fin = img == null ? new byte[0] : img;
        MovilRed.aJugador(p, MovilRed.S_FOTO, b -> {
            b.writeByte(tipo);
            b.writeVarInt(id);
            b.writeByteArray(fin);
        });
    }

    private static void borrarFoto(ServerPlayer p, int id) {
        MovilDatos d = datos(p.server);
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), nombre(p));
        if (yo.fotos.removeIf(f -> f.id() == id)) {
            Fotos.borrar(Fotos.propia(p.server, p.getUUID(), id));
            Fotos.borrar(Fotos.clipPropio(p.server, p.getUUID(), id));
            d.setDirty();
        }
        enviarFotos(p);
    }

    // ------------------------------------------------------------------ ajustes
    private static void ajustes(ServerPlayer p, boolean silencio, int tono, int fondo, int alarma) {
        MovilDatos d = datos(p.server);
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), nombre(p));
        yo.silencio = silencio;
        yo.tono = Math.floorMod(tono, MovilRegistro.TONOS);
        yo.fondo = Math.floorMod(fondo, 4);
        yo.alarma = alarma < 0 || alarma >= 1440 ? -1 : alarma;
        d.setDirty();
    }
}
