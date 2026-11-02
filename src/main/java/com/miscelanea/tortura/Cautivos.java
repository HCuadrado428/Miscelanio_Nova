package com.miscelanea.tortura;

import com.miscelanea.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Estado de los cautivos guardado en los datos persistentes de la entidad (se guarda con el jugador o el mob, así que
 * sobrevive a desconectarse y a reiniciar el servidor). Al morir un jugador NO se copia a su nuevo cuerpo: la trampa y los
 * grilletes desaparecen con la muerte.
 * <pre>
 * miscelanea_tortura: {
 *   Trampa: ticks que quedan (>0 = la lleva), TrampaId, TrampaCaptor (UUID), TrampaCaja (pos con la llave), SinCajas,
 *   Buscadas: [pos...], Esperas: {pos: tick},
 *   Grilletes: bool, GrilletesCaptor (UUID), Cadena (UUID de quien tira), Cerradura (semilla de la ganzúa),
 *   Lazo: 1 enlazado / 2 derribado / 3 maniatado, LazoCaptor (UUID), LazoT (ticks que quedan del estado)
 * }
 * </pre>
 */
public final class Cautivos {
    private Cautivos() {}

    public static final String RAIZ = "miscelanea_tortura";

    /** Copia de los datos (para leer). */
    public static CompoundTag leer(Entity e) {
        return e.getPersistentData().getCompound(RAIZ);
    }

    public static void escribir(Entity e, CompoundTag d) {
        if (d.isEmpty()) e.getPersistentData().remove(RAIZ);
        else e.getPersistentData().put(RAIZ, d);
    }

    public static boolean tieneAlgo(Entity e) {
        return e.getPersistentData().contains(RAIZ);
    }

    public static boolean conTrampa(Entity e) {
        return tieneAlgo(e) && leer(e).getInt("Trampa") > 0;
    }

    public static boolean atado(Entity e) {
        return tieneAlgo(e) && leer(e).getBoolean("Grilletes");
    }

    @Nullable
    public static UUID cadena(Entity e) {
        if (!tieneAlgo(e)) return null;
        CompoundTag d = leer(e);
        return d.hasUUID("Cadena") ? d.getUUID("Cadena") : null;
    }

    /** Estado del lazo (0 = nada; ver {@link Lazo#ENLAZADO}, {@link Lazo#DERRIBADO}, {@link Lazo#MANIATADO}). */
    public static int lazo(Entity e) {
        return tieneAlgo(e) ? leer(e).getInt("Lazo") : 0;
    }

    @Nullable
    public static UUID lazoCaptor(Entity e) {
        if (!tieneAlgo(e)) return null;
        CompoundTag d = leer(e);
        return d.hasUUID("LazoCaptor") ? d.getUUID("LazoCaptor") : null;
    }

    /** Manda el estado a quien ve a la entidad (y a ella misma si es un jugador). */
    public static void sincronizar(LivingEntity e) {
        if (e.level().isClientSide) return;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), paquete(e));
    }

    /** Solo a un jugador concreto (al empezar a ver a la entidad). */
    public static void sincronizarCon(LivingEntity e, ServerPlayer quien) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> quien), paquete(e));
    }

    static EstadoCautivoPacket paquete(LivingEntity e) {
        CompoundTag d = leer(e);
        int cad = -1;
        if (d.hasUUID("Cadena") && e.level() instanceof ServerLevel sl) {
            Entity h = sl.getEntity(d.getUUID("Cadena"));
            if (h != null) cad = h.getId();
        }
        int lazoCap = -1;
        if (d.hasUUID("LazoCaptor") && e.level() instanceof ServerLevel sl) {
            Entity h = sl.getEntity(d.getUUID("LazoCaptor"));
            if (h != null) lazoCap = h.getId();
        }
        return new EstadoCautivoPacket(e.getId(), d.getInt("Trampa"), d.getBoolean("SinCajas"), d.getBoolean("Grilletes"), cad,
                d.getInt("Lazo"), lazoCap, d.getInt("LazoT"));
    }
}
