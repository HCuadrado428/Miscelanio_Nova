package com.miscelanea.tinta;

import com.miscelanea.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** Depósito de tinta del jugador (0..100), compartido por el lanzatintas y el rodillo. Lo guarda el servidor y lo manda al cliente. */
public final class Deposito {
    public static final float MAX = 100.0F;
    private static final String NBT = "MiscelaneaTinta";
    /** Cliente: nivel de cada jugador visible (por id de entidad). */
    private static final Map<Integer, Float> CLIENTE = new HashMap<>();

    private Deposito() {}

    public static float nivel(Player p) {
        if (p.level().isClientSide) return CLIENTE.getOrDefault(p.getId(), MAX);
        return p.getPersistentData().contains(NBT) ? p.getPersistentData().getFloat(NBT) : MAX;
    }

    public static void poner(ServerPlayer p, float v) {
        float antes = nivel(p);
        v = Math.max(0, Math.min(MAX, v));
        p.getPersistentData().putFloat(NBT, v);
        // Se manda al propio jugador cuando cambia algo visible, y a los demás de vez en cuando (para el depósito de la espalda)
        if ((int) antes != (int) v || v == MAX || v == 0) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new DepositoPacket(p.getId(), v));
        }
    }

    /** Gasta tinta; devuelve false si no hay suficiente. */
    public static boolean gastar(ServerPlayer p, float n) {
        if (p.getAbilities().instabuild) return true;
        float v = nivel(p);
        if (v < n) return false;
        poner(p, v - n);
        return true;
    }

    public static void recargar(ServerPlayer p, float n) {
        float v = nivel(p);
        if (v < MAX) poner(p, v + n);
    }

    public static void recibir(DepositoPacket pk) {
        CLIENTE.put(pk.id, pk.nivel);
    }
}
