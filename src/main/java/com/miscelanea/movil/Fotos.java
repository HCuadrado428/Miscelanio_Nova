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
}
