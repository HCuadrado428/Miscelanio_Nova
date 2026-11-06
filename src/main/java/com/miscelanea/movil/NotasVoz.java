package com.miscelanea.movil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notas de voz de los chats (lado servidor). El cliente graba el micro (a través de Simple Voice Chat), lo comprime en
 * Opus y lo sube en trozos de {@link MovilRed#TROZO} bytes ({@link MovilRed#C_VOZ_TROZO}); con el último trozo van el
 * chat, la duración y la forma de onda. El servidor guarda el archivo en {@code miscelanea_movil/voz/<id>.mvz} y publica
 * el mensaje; los miembros del chat lo descargan al tocarlo ({@link MovilRed#C_VOZ_PEDIR}).
 * <p>
 * Formato del archivo (lo escribe el cliente): {@code "MVZ1"}, número de tramas (int) y cada trama de 20 ms como
 * {@code [u16 longitud][bytes Opus]} (48 kHz, mono).
 */
public final class NotasVoz {
    private NotasVoz() {}

    public static final byte[] MAGIA = {'M', 'V', 'Z', '1'};
    public static final int ONDA = 40;

    private static final class Subida {
        final ByteArrayOutputStream datos = new ByteArrayOutputStream();
        long ultimo;
    }

    private static final Map<UUID, Subida> SUBIDAS = new HashMap<>();
    private static final Map<UUID, Long> ULT_NOTA = new HashMap<>();

    static void salir(ServerPlayer p) {
        SUBIDAS.remove(p.getUUID());
        ULT_NOTA.remove(p.getUUID());
    }

    static void trozo(ServerPlayer p, FriendlyByteBuf b) {
        int chatId = b.readVarInt();
        boolean fin = b.readBoolean();
        byte[] trozo = b.readByteArray(MovilRed.TROZO + 16);
        long ahora = p.server.getTickCount();
        Subida s = SUBIDAS.get(p.getUUID());
        if (s == null || ahora - s.ultimo > 200) {
            s = new Subida();
            SUBIDAS.put(p.getUUID(), s);
        }
        s.ultimo = ahora;
        if (!MovilConfig.notasVoz()) {
            SUBIDAS.remove(p.getUUID());
            return;
        }
        if (s.datos.size() + trozo.length > MovilConfig.vozMaxBytes()) {
            SUBIDAS.remove(p.getUUID());
            MovilServidor.aviso(p, "movil.miscelanea.aviso.nota_grande", "");
            return;
        }
        s.datos.write(trozo, 0, trozo.length);
        if (!fin) return;
        SUBIDAS.remove(p.getUUID());
        int ms = b.readVarInt();
        byte[] onda = b.readByteArray(ONDA);
        byte[] todo = s.datos.toByteArray();
        if (!valida(todo) || ms <= 200 || ms > MovilConfig.vozSegundos() * 1000 + 1000) return;
        Long ult = ULT_NOTA.get(p.getUUID());
        if (ult != null && ahora - ult < 20) return;
        ULT_NOTA.put(p.getUUID(), ahora);
        MovilDatos.Chat c = MovilServidor.chatDeJugador(p, chatId);
        if (c == null) return;
        MovilDatos d = MovilDatos.de(p.server);
        int id = d.sigVoz++;
        if (!Fotos.escribir(Fotos.voz(p.server, id), todo)) return;
        d.setDirty();
        MovilServidor.difundir(p, c, new MovilDatos.Mensaje(++c.sec, p.getUUID(), System.currentTimeMillis(), "", -1, id, ms, onda));
    }

    /** Cabecera y tramas bien formadas (no se decodifica nada en el servidor). */
    static boolean valida(byte[] b) {
        if (b.length < 8) return false;
        for (int i = 0; i < 4; i++) if (b[i] != MAGIA[i]) return false;
        int n = ((b[4] & 0xFF) << 24) | ((b[5] & 0xFF) << 16) | ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
        if (n <= 0 || n > 6000) return false;
        int i = 8;
        for (int k = 0; k < n; k++) {
            if (i + 2 > b.length) return false;
            int len = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            i += 2 + len;
            if (len > 1500 || i > b.length) return false;
        }
        return i == b.length;
    }

    static void pedir(ServerPlayer p, int id) {
        boolean puede = false;
        for (MovilDatos.Chat c : MovilDatos.de(p.server).chatsDe(p.getUUID())) {
            for (MovilDatos.Mensaje m : c.msgs)
                if (m.voz() == id) {
                    puede = true;
                    break;
                }
            if (puede) break;
        }
        byte[] datos = puede ? Fotos.leer(Fotos.voz(p.server, id)) : null;
        byte[] fin = datos == null ? new byte[0] : datos;
        MovilRed.aJugador(p, MovilRed.S_VOZ, w -> {
            w.writeVarInt(id);
            w.writeByteArray(fin);
        });
    }
}
