package com.miscelanea.movil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ubicación en tiempo real: un jugador comparte su posición con otros durante 15/30/60 minutos reales. Cada ~2 s el
 * servidor manda a cada jugador SOLO las posiciones que le comparten a él ({@link MovilRed#S_UBI}), más la lista de con
 * quién está compartiendo la suya. Va en memoria: si se reinicia el servidor, se deja de compartir.
 */
public final class Ubicaciones {
    private Ubicaciones() {}

    /** quien comparte → (con quién → hasta cuándo, en ms reales). */
    private static final Map<UUID, Map<UUID, Long>> COMPARTE = new HashMap<>();
    private static final int CADA = 40;

    static void compartir(ServerPlayer p, FriendlyByteBuf b) {
        int min = b.readVarInt();
        int n = Math.min(MovilServidor.MAX_MIEMBROS, b.readVarInt());
        List<UUID> con = new ArrayList<>();
        for (int i = 0; i < n; i++) con.add(b.readUUID());
        if (!MovilConfig.ubicacion()) return;
        min = Math.max(1, Math.min(MovilConfig.ubiMaxMinutos(), min));
        long hasta = System.currentTimeMillis() + min * 60_000L;
        Map<UUID, Long> m = COMPARTE.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>());
        MovilDatos d = MovilDatos.de(p.server);
        String nombre = MovilServidor.nombre(p);
        for (UUID u : con) {
            if (u.equals(p.getUUID()) || !d.perfiles.containsKey(u)) continue;
            boolean nuevo = !m.containsKey(u);
            m.put(u, hasta);
            ServerPlayer o = p.server.getPlayerList().getPlayer(u);
            if (o != null && nuevo)
                MovilServidor.noti(o, MovilRed.APP_MAPA, nombre, MovilServidor.tr("ubi_te_comparte", min), true);
        }
        enviar(p.server, p);
    }

    static void parar(ServerPlayer p, FriendlyByteBuf b) {
        boolean todos = b.readBoolean();
        UUID u = todos ? null : b.readUUID();
        Map<UUID, Long> m = COMPARTE.get(p.getUUID());
        if (m != null) {
            if (todos) m.clear();
            else m.remove(u);
            if (m.isEmpty()) COMPARTE.remove(p.getUUID());
        }
        enviar(p.server, p);
        if (u != null) {
            ServerPlayer o = p.server.getPlayerList().getPlayer(u);
            if (o != null) enviar(p.server, o);
        }
    }

    static void salir(ServerPlayer p) {
        // quien se va deja de compartir (y los demás lo verán desaparecer en el siguiente envío)
        COMPARTE.remove(p.getUUID());
        ENVIADO.remove(p.getUUID());
    }


    static void tick(MinecraftServer s) {
        if (s.getTickCount() % CADA != 0 || (COMPARTE.isEmpty() && s.getTickCount() % (CADA * 5) != 0)) return;
        long ahora = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<UUID, Long>>> it = COMPARTE.entrySet().iterator(); it.hasNext(); ) {
            Map<UUID, Long> m = it.next().getValue();
            m.values().removeIf(t -> t < ahora);
            if (m.isEmpty()) it.remove();
        }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) enviar(s, p);
    }

    /** Lo que ve p: posiciones que le comparten y con quién comparte la suya. */
    private static void enviar(MinecraftServer s, ServerPlayer p) {
        long ahora = System.currentTimeMillis();
        List<ServerPlayer> le = new ArrayList<>();
        List<Long> leHasta = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, Long>> e : COMPARTE.entrySet()) {
            Long t = e.getValue().get(p.getUUID());
            ServerPlayer o = s.getPlayerList().getPlayer(e.getKey());
            if (t != null && t > ahora && o != null) {
                le.add(o);
                leHasta.add(t);
            }
        }
        Map<UUID, Long> mias = COMPARTE.getOrDefault(p.getUUID(), Map.of());
        if (le.isEmpty() && mias.isEmpty() && !ENVIADO.containsKey(p.getUUID())) return;
        if (le.isEmpty() && mias.isEmpty()) ENVIADO.remove(p.getUUID());
        else ENVIADO.put(p.getUUID(), true);
        MovilRed.aJugador(p, MovilRed.S_UBI, b -> {
            b.writeVarInt(le.size());
            for (int i = 0; i < le.size(); i++) {
                ServerPlayer o = le.get(i);
                b.writeUUID(o.getUUID());
                b.writeUtf(MovilServidor.nombre(o));
                b.writeUtf(o.level().dimension().location().toString());
                b.writeDouble(o.getX());
                b.writeDouble(o.getY());
                b.writeDouble(o.getZ());
                b.writeFloat(o.getYRot());
                b.writeVarInt((int) ((leHasta.get(i) - ahora) / 1000));
            }
            b.writeVarInt(mias.size());
            for (Map.Entry<UUID, Long> e : mias.entrySet()) {
                b.writeUUID(e.getKey());
                b.writeUtf(MovilServidor.nombreDe(s, e.getKey()));
                b.writeVarInt((int) Math.max(0, (e.getValue() - ahora) / 1000));
            }
        });
    }

    /** A quién se le ha mandado algo (para mandarle una vez la lista vacía cuando se acaba). */
    private static final Map<UUID, Boolean> ENVIADO = new HashMap<>();
}
