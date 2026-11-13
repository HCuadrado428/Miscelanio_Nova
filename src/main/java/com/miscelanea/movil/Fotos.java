package com.miscelanea.movil;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Archivos de imagen del móvil, en la carpeta del mundo: {@code miscelanea_movil/fotos/<uuid>/<id>.jpg} (galería de cada
 * jugador) y {@code miscelanea_movil/adjuntos/<id>.jpg} (copias enviadas en los chats, para que borrar la foto de la
 * galería no la quite del chat). Solo se aceptan JPEG/PNG de hasta {@link MovilConfig#maxBytesFoto()} bytes.
 */
public final class Fotos {
    private static final Logger LOG = LogUtils.getLogger();

    private Fotos() {}

    private static Path raiz(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("miscelanea_movil");
    }

    static Path propia(MinecraftServer s, UUID jugador, int id) {
        return raiz(s).resolve("fotos").resolve(jugador.toString()).resolve(id + ".jpg");
    }

    static Path adjunto(MinecraftServer s, int id) {
        return raiz(s).resolve("adjuntos").resolve(id + ".jpg");
    }

    /** Foto de una publicación de Minegram (copia propia, así borrar la de la galería no la quita). */
    static Path minegram(MinecraftServer s, int id) {
        return raiz(s).resolve("minegram").resolve(id + ".jpg");
    }

    /** Vídeo corto de la galería (su miniatura es la foto con el mismo id). */
    static Path clipPropio(MinecraftServer s, UUID jugador, int id) {
        return raiz(s).resolve("fotos").resolve(jugador.toString()).resolve(id + ".mvd");
    }

    /** Vídeo corto enviado a un chat (su miniatura es el adjunto con el mismo id). */
    static Path clipAdjunto(MinecraftServer s, int id) {
        return raiz(s).resolve("adjuntos").resolve(id + ".mvd");
    }

    /** Nota de voz de un chat (Opus en el formato de {@code NotasVoz}). */
    static Path voz(MinecraftServer s, int id) {
        return raiz(s).resolve("voz").resolve(id + ".mvz");
    }

    /** ¿Parece una imagen JPEG o PNG de tamaño aceptable? */
    public static boolean valida(byte[] b, int max) {
        if (b == null || b.length < 64 || b.length > max) return false;
        boolean jpg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        return jpg || png;
    }

    static boolean escribir(Path p, byte[] b) {
        try {
            Files.createDirectories(p.getParent());
            Files.write(p, b);
            return true;
        } catch (IOException e) {
            LOG.warn("Móvil: no se pudo guardar la imagen {}: {}", p, e.toString());
            return false;
        }
    }

    static byte[] leer(Path p) {
        try {
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static void borrar(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    static boolean copiar(Path de, Path a) {
        byte[] b = leer(de);
        return b != null && escribir(a, b);
    }

    /** Los números de las fotos de la galería de alguien, de la más vieja a la más nueva. */
    public static java.util.List<Integer> galeriaDe(net.minecraft.server.level.ServerPlayer p) {
        MovilDatos.Perfil yo = MovilServidor.datos(p.server).perfil(p.getUUID(), MovilServidor.nombre(p));
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (MovilDatos.FotoMeta f : yo.fotos) l.add(f.id());
        return l;
    }

    /**
     * Hace una copia de una foto de la galería para que la pueda ver cualquiera (como cuando se manda por el chat) y
     * devuelve su número. Devuelve -1 si esa foto no es suya o si no se pudo copiar.
     */
    public static int copiaParaEnsenar(net.minecraft.server.level.ServerPlayer p, int fotoId) {
        MovilDatos d = MovilServidor.datos(p.server);
        MovilDatos.Perfil yo = d.perfil(p.getUUID(), MovilServidor.nombre(p));
        if (yo.fotos.stream().noneMatch(f -> f.id() == fotoId)) return -1;
        int a = d.sigAdjunto++;
        d.setDirty();
        return copiar(propia(p.server, p.getUUID(), fotoId), adjunto(p.server, a)) ? a : -1;
    }
}
