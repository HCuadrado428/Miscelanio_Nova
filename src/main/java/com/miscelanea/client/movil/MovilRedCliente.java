package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.UUID;

import static com.miscelanea.client.movil.MovilCliente.*;

/** Lectura de los paquetes del servidor (mismo orden que los escribe {@code MovilServidor}/{@code Llamadas}). */
public final class MovilRedCliente {
    private MovilRedCliente() {}

    static Msg leerMensaje(FriendlyByteBuf b) {
        return new Msg(b.readLong(), b.readUUID(), b.readUtf(), b.readLong(), b.readUtf(), b.readVarInt() - 1, b.readVarInt() - 1, b.readVarInt(),
                b.readByteArray(64));
    }

    public static void recibir(int tipo, FriendlyByteBuf b) {
        try {
            switch (tipo) {
                case MovilRed.S_SYNC -> sync(b);
                case MovilRed.S_CHAT -> {
                    ChatAbierto c = new ChatAbierto();
                    c.id = b.readVarInt();
                    c.grupo = b.readBoolean();
                    c.nombre = b.readUtf();
                    int n = b.readVarInt();
                    for (int i = 0; i < n; i++) c.miembros.put(b.readUUID(), b.readUtf());
                    n = b.readVarInt();
                    for (int i = 0; i < n; i++) c.msgs.add(leerMensaje(b));
                    chat = c;
                    ChatInfo ci = chatInfo(c.id);
                    if (ci != null) ci.noLeidos = 0;
                }
                case MovilRed.S_MENSAJE -> {
                    int id = b.readVarInt();
                    boolean grupo = b.readBoolean();
                    String nombre = b.readUtf();
                    Msg m = leerMensaje(b);
                    MovilCliente.mensaje(id, grupo, nombre, m, b.readBoolean());
                }
                case MovilRed.S_FOTOS -> {
                    maxFotos = b.readVarInt();
                    maxBytesFoto = b.readVarInt();
                    int n = b.readVarInt();
                    FOTOS.clear();
                    for (int i = 0; i < n; i++) FOTOS.add(new Foto(b.readVarInt(), b.readLong()));
                    fotosCargadas = true;
                }
                case MovilRed.S_FOTO -> {
                    int t = b.readByte(), id = b.readVarInt();
                    FotoCache.recibir(t, id, b.readByteArray());
                }
                case MovilRed.S_LLAMADA -> {
                    int estado = b.readByte();
                    UUID otro = b.readUUID();
                    String nombre = b.readUtf();
                    boolean video = b.readBoolean();
                    int motivo = b.readByte();
                    videoParams(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
                    MovilCliente.llamada(estado, otro, nombre, video, motivo);
                }
                case MovilRed.S_VIDEO -> {
                    byte[] img = b.readByteArray();
                    otroVideo = img.length > 0;
                    FotoCache.video(img);
                }
                case MovilRed.S_POSE -> poseRemota(b.readVarInt(), b.readByte());
                case MovilRed.S_AVISO -> aviso(Component.translatable(b.readUtf(), b.readUtf()));
                case MovilRed.S_CHAT_ID -> chatPendiente = b.readVarInt();
                case MovilRed.S_VOZ -> {
                    int id = b.readVarInt();
                    NotasVozCliente.recibir(id, b.readByteArray());
                }
                case MovilRed.S_UBI -> {
                    UBI_LE.clear();
                    int n = b.readVarInt();
                    long ahora = net.minecraft.Util.getMillis();
                    for (int i = 0; i < n; i++)
                        UBI_LE.add(new UbiAmigo(b.readUUID(), b.readUtf(), b.readUtf(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readVarInt(), ahora));
                    UBI_MIAS.clear();
                    n = b.readVarInt();
                    for (int i = 0; i < n; i++) UBI_MIAS.add(new UbiMia(b.readUUID(), b.readUtf(), b.readVarInt(), ahora));
                }
                case MovilRed.S_NOTI -> MovilCliente.noti(b.readByte(), b.readUtf(), b.readUtf(), b.readBoolean());
                case MovilRed.S_MG_FEED, MovilRed.S_MG_POST, MovilRed.S_MG_PERFIL, MovilRed.S_MG_NOTIS -> MinegramCliente.recibir(tipo, b);
                case MovilRed.S_BANCO -> BancoCliente.recibir(b);
                case MovilRed.S_LINTERNA -> {
                    int id = b.readVarInt();
                    boolean on = b.readBoolean();
                    var yo = net.minecraft.client.Minecraft.getInstance().player;
                    if (yo != null && yo.getId() == id) linterna = on;
                    else if (on) LINTERNAS.add(id);
                    else LINTERNAS.remove(id);
                }
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void sync(FriendlyByteBuf b) {
        miNumero = b.readUtf();
        silencio = b.readBoolean();
        tono = b.readByte();
        fondo = b.readByte();
        alarma = b.readInt();
        vozServidor = b.readBoolean();
        videoServidor = b.readBoolean();
        CONTACTOS.clear();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) CONTACTOS.add(new Contacto(b.readUUID(), b.readUtf(), b.readUtf(), b.readBoolean()));
        CONTACTOS.sort((x, y) -> x.conectado() != y.conectado() ? (x.conectado() ? -1 : 1) : x.nombre().compareToIgnoreCase(y.nombre()));
        CHATS.clear();
        n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            ChatInfo c = new ChatInfo();
            c.id = b.readVarInt();
            c.grupo = b.readBoolean();
            c.nombre = b.readUtf();
            int m = b.readVarInt();
            for (int j = 0; j < m; j++) c.miembros.add(b.readUUID());
            if (b.readBoolean()) c.ultimo = leerMensaje(b);
            c.noLeidos = b.readVarInt();
            CHATS.add(c);
        }
        REGISTRO.clear();
        n = b.readVarInt();
        for (int i = 0; i < n; i++) REGISTRO.add(new Registro(b.readUUID(), b.readUtf(), b.readLong(), b.readByte(), b.readVarInt(), b.readBoolean()));
        notasVozServidor = b.readBoolean();
        vozMaxSeg = b.readVarInt();
        vozMaxBytes = b.readVarInt();
        minegram = b.readBoolean();
        MinegramCliente.noVistas = b.readVarInt();
        ubicacion = b.readBoolean();
        banco = b.readBoolean();
        bancoObjetos = b.readBoolean();
        linternaReal = b.readBoolean();
        sincronizado = true;
    }
}
