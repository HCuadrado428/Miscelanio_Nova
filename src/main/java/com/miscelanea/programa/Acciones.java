package com.miscelanea.programa;

import com.miscelanea.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Animaciones de interacción del jugador (las ven él y los demás): registrar, empujar, manivela, palanca,
 * ganzúa, embestir, reanimar, caído, inyectarse y lanzar. El servidor avisa al empezar y mientras se mantiene.
 */
public final class Acciones {
    public enum Accion { NADA, REGISTRAR, EMPUJAR, MANIVELA, PALANCA, GANZUA, EMBESTIR, REANIMAR, CAIDO, INYECTAR, LANZAR }

    private record Ultima(Accion a, long hasta, long enviado) {}

    private static final Map<UUID, Ultima> ULTIMAS = new HashMap<>();

    private Acciones() {}

    /** Pone (o mantiene) una acción durante 'ticks'. Llamar repetido mientras se mantiene el clic. */
    public static void poner(ServerPlayer p, Accion a, int ticks) {
        long t = p.level().getGameTime();
        Ultima u = ULTIMAS.get(p.getUUID());
        boolean enviar = u == null || u.a != a || u.hasta < t || t - u.enviado >= 5;
        long hasta = t + ticks;
        ULTIMAS.put(p.getUUID(), new Ultima(a, hasta, enviar ? t : u.enviado));
        if (enviar) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new AccionPacket(p.getId(), a.ordinal(), ticks));
        }
    }

    public static void parar(ServerPlayer p) {
        if (ULTIMAS.remove(p.getUUID()) != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new AccionPacket(p.getId(), 0, 0));
        }
    }
}
