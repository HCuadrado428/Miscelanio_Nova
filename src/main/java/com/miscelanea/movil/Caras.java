package com.miscelanea.movil;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caras para el móvil con Customizable Player Models: el juego de cada jugador dibuja la cabeza de SU modelo (de frente,
 * sin el móvil) en una imagen PNG pequeña y la manda; el servidor la guarda (en {@code miscelanea_movil/caras/}) y se la
 * pasa a todos. Así en contactos, chats y llamadas se ve la cara de su modelo aunque esté lejos o desconectado.
 */
public final class Caras {
    private Caras() {}

    /** Tamaño máximo del PNG (64x64 con transparencia suele quedarse en 3-8 KB). */
    public static final int MAX = 24_000;
    private static final Map<UUID, byte[]> CARAS = new HashMap<>();
    private static final Map<UUID, Long> ULTIMA = new HashMap<>();
    private static MinecraftServer cargadas;

    private static Path carpeta(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("miscelanea_movil").resolve("caras");
    }

    private static boolean png(byte[] b) {
        return b.length > 8 && b.length <= MAX && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static void cargar(MinecraftServer s) {
        if (cargadas == s) return;
        cargadas = s;
        CARAS.clear();
        ULTIMA.clear();
        try (var l = Files.list(carpeta(s))) {
            l.forEach(f -> {
                String n = f.getFileName().toString();
                if (!n.endsWith(".png")) return;
                try {
                    byte[] b = Files.readAllBytes(f);
                    if (png(b)) CARAS.put(UUID.fromString(n.substring(0, n.length() - 4)), b);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    static void recibir(ServerPlayer p, byte[] b) {
        if (!png(b)) return;
        MinecraftServer s = p.server;
        cargar(s);
        long ahora = s.getTickCount();
        Long ult = ULTIMA.get(p.getUUID());
        if (ult != null && ahora - ult < 200) return;     // como mucho una cada 10 s
        ULTIMA.put(p.getUUID(), ahora);
        byte[] antes = CARAS.get(p.getUUID());
        if (antes != null && java.util.Arrays.equals(antes, b)) return;
        CARAS.put(p.getUUID(), b);
        try {
            Files.createDirectories(carpeta(s));
            Files.write(carpeta(s).resolve(p.getUUID() + ".png"), b);
        } catch (Exception ignored) {
        }
        for (ServerPlayer o : s.getPlayerList().getPlayers()) enviar(o, p.getUUID(), b);
    }

    /** Al entrar: todas las caras que hay. */
    static void alEntrar(ServerPlayer p) {
        cargar(p.server);
        CARAS.forEach((id, b) -> enviar(p, id, b));
    }

    private static void enviar(ServerPlayer o, UUID id, byte[] b) {
        MovilRed.aJugador(o, MovilRed.S_CARA, w -> {
            w.writeUUID(id);
            w.writeByteArray(b);
        });
    }
}
