package com.miscelanea.movil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vídeos cortos de la cámara (lado servidor). El cliente los graba, los empaqueta y los sube en trozos de
 * {@link MovilRed#TROZO} bytes ({@link MovilRed#C_CLIP_TROZO}); el servidor comprueba el formato, saca la primera imagen
 * como miniatura y los guarda en la galería como una foto más (con su duración). Se pueden mandar a un chat como una foto
 * (se copian a los adjuntos) y se descargan enteros al verlos ({@link MovilRed#C_CLIP_PEDIR}).
 * <p>
 * Formato ({@code .mvd}, lo escribe el cliente): {@code "MVD1"}, imágenes por segundo (u8), ancho y alto (u16), número de
 * imágenes (int), cada imagen como {@code [u16 longitud][JPEG]}, y al final el sonido: {@code [int longitud][nota de voz
 * en el formato de NotasVoz]} (longitud 0 = sin sonido).
 */
public final class Clips {
    private Clips() {}

    public static final byte[] MAGIA = {'M', 'V', 'D', '1'};

    private static final class Subida {
        final ByteArrayOutputStream datos = new ByteArrayOutputStream();
        long ultimo;
    }

    private static final Map<UUID, Subida> SUBIDAS = new HashMap<>();

    static void salir(ServerPlayer p) {
        SUBIDAS.remove(p.getUUID());
    }

    static void trozo(ServerPlayer p, FriendlyByteBuf b) {
        boolean fin = b.readBoolean();
        byte[] trozo = b.readByteArray(MovilRed.TROZO + 16);
        long ahora = p.server.getTickCount();
        Subida s = SUBIDAS.get(p.getUUID());
        if (s == null || ahora - s.ultimo > 200) {
            s = new Subida();
            SUBIDAS.put(p.getUUID(), s);
        }
        s.ultimo = ahora;
        if (!MovilConfig.clips()) {
            SUBIDAS.remove(p.getUUID());
            return;
        }
        if (s.datos.size() + trozo.length > MovilConfig.clipMaxBytes()) {
            SUBIDAS.remove(p.getUUID());
            MovilServidor.aviso(p, "movil.miscelanea.aviso.video_grande", "");
            return;
        }
        s.datos.write(trozo, 0, trozo.length);
        if (!fin) return;
        SUBIDAS.remove(p.getUUID());
        byte[] todo = s.datos.toByteArray();
        Info info = leer(todo);
        if (info == null || info.ms > MovilConfig.clipSegundos() * 1000 + 1500) return;
        if (!Fotos.valida(info.primera, MovilConfig.maxBytesFoto())) return;
        MovilDatos d = MovilDatos.de(p.server);
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), MovilServidor.nombre(p));
        if (yo.fotos.size() >= MovilConfig.maxFotos()) {
            MovilServidor.aviso(p, "movil.miscelanea.aviso.galeria_llena", Integer.toString(MovilConfig.maxFotos()));
            return;
        }
        int id = yo.sigFoto++;
        if (!Fotos.escribir(Fotos.propia(p.server, p.getUUID(), id), info.primera)) return;
        if (!Fotos.escribir(Fotos.clipPropio(p.server, p.getUUID(), id), todo)) {
            Fotos.borrar(Fotos.propia(p.server, p.getUUID(), id));
            return;
        }
        yo.fotos.add(new MovilDatos.FotoMeta(id, System.currentTimeMillis(), Math.max(1, info.ms)));
        d.setDirty();
        p.level().playSound(p, p.getX(), p.getY(), p.getZ(), MovilRegistro.OBTURADOR.get(), SoundSource.PLAYERS, 0.4F, 1.3F);
        MovilServidor.enviarFotos(p);
    }

    record Info(int fps, int w, int h, int n, int ms, byte[] primera) {}

    private static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static int i32(byte[] b, int i) {
        return ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    /** Comprueba el formato (sin decodificar nada) y saca la primera imagen; null si está mal. */
    static Info leer(byte[] b) {
        if (b.length < 13) return null;
        for (int i = 0; i < 4; i++) if (b[i] != MAGIA[i]) return null;
        int fps = b[4] & 0xFF, w = u16(b, 5), h = u16(b, 7), n = i32(b, 9);
        if (fps < 1 || fps > 30 || w < 8 || h < 8 || w > 400 || h > 400 || n < 1 || n > fps * 32) return null;
        int i = 13;
        byte[] primera = null;
        for (int k = 0; k < n; k++) {
            if (i + 2 > b.length) return null;
            int len = u16(b, i);
            i += 2;
            if (len < 64 || i + len > b.length || (b[i] & 0xFF) != 0xFF || (b[i + 1] & 0xFF) != 0xD8) return null;
            if (k == 0) primera = java.util.Arrays.copyOfRange(b, i, i + len);
            i += len;
        }
        if (i + 4 > b.length) return null;
        int audio = i32(b, i);
        i += 4;
        if (audio < 0 || i + audio != b.length) return null;
        if (audio > 0 && !NotasVoz.valida(java.util.Arrays.copyOfRange(b, i, i + audio))) return null;
        return new Info(fps, w, h, n, n * 1000 / fps, primera);
    }

    /** Duración del vídeo de esa foto de la galería (0 = es una foto). */
    static int msDe(MovilDatos.Perfil yo, int foto) {
        for (MovilDatos.FotoMeta f : yo.fotos) if (f.id() == foto) return f.ms();
        return 0;
    }

    static void pedir(ServerPlayer p, int tipo, int id) {
        byte[] datos = null;
        if (tipo == MovilRed.FOTO_PROPIA) {
            MovilDatos.Perfil yo = MovilDatos.de(p.server).perfil(p.getUUID(), null);
            if (msDe(yo, id) > 0) datos = Fotos.leer(Fotos.clipPropio(p.server, p.getUUID(), id));
        } else if (tipo == MovilRed.FOTO_ADJUNTA) {
            boolean puede = false;
            for (MovilDatos.Chat c : MovilDatos.de(p.server).chatsDe(p.getUUID())) {
                for (MovilDatos.Mensaje m : c.msgs)
                    if (m.adjunto() == id && m.voz() < 0 && m.vozMs() > 0) {
                        puede = true;
                        break;
                    }
                if (puede) break;
            }
            if (puede) datos = Fotos.leer(Fotos.clipAdjunto(p.server, id));
        }
        byte[] fin = datos == null ? new byte[0] : datos;
        MovilRed.aJugador(p, MovilRed.S_CLIP, w -> {
            w.writeByte(tipo);
            w.writeVarInt(id);
            w.writeByteArray(fin);
        });
    }
}
