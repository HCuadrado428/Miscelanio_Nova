package com.miscelanea.pociones;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Espejismo (servidor): a quién imita cada jugador (UUID y nombre, en los datos persistentes) y el envío del
 * {@link EstadoPacket} con todo lo que el cliente necesita saber de una entidad (sombra, niebla, tamaño y espejismo).
 */
public final class Espejismos {
    private Espejismos() {}

    public static final String CLAVE = "MiscelaneaEspejismo";

    /** Elige a otro jugador conectado al azar (si no hay nadie más, el espejismo no imita a nadie). */
    public static void elegir(ServerPlayer p) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        List<ServerPlayer> otros = new ArrayList<>();
        for (ServerPlayer o : s.getPlayerList().getPlayers()) if (o != p) otros.add(o);
        if (otros.isEmpty()) {
            p.getPersistentData().remove(CLAVE);
            return;
        }
        fijar(p, otros.get(p.getRandom().nextInt(otros.size())));
    }

    public static void fijar(ServerPlayer p, Player objetivo) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", objetivo.getUUID());
        t.putString("Nombre", objetivo.getGameProfile().getName());
        p.getPersistentData().put(CLAVE, t);
    }

    /** UUID del imitado, o null. */
    public static UUID objetivo(LivingEntity v) {
        CompoundTag d = v.getPersistentData();
        if (!d.contains(CLAVE)) return null;
        CompoundTag t = d.getCompound(CLAVE);
        return t.hasUUID("Id") ? t.getUUID("Id") : null;
    }

    public static String nombreObjetivo(LivingEntity v) {
        CompoundTag d = v.getPersistentData();
        return d.contains(CLAVE) ? d.getCompound(CLAVE).getString("Nombre") : "";
    }

    // ------------------------------------------------------------------ estado para los clientes
    public static boolean tieneAlgo(LivingEntity v) {
        return v.hasEffect(PocionesRegistro.NIEBLA_VIVIENTE.get())
                || v.hasEffect(PocionesRegistro.ESPEJISMO.get()) || v.hasEffect(PocionesRegistro.GIGANTE.get())
                || v.hasEffect(PocionesRegistro.ENANO.get());
    }

    /** El paquete con el estado actual; {@code ignorar}: un efecto que se está quitando ahora mismo (aún figura). */
    public static EstadoPacket estado(LivingEntity v, MobEffect ignorar) {
        int b = 0;
        if (tiene(v, PocionesRegistro.NIEBLA_VIVIENTE.get(), ignorar)) b |= EstadoPacket.NIEBLA;
        UUID espejo = null;
        String nombre = "";
        if (tiene(v, PocionesRegistro.ESPEJISMO.get(), ignorar)) {
            espejo = objetivo(v);
            nombre = nombreObjetivo(v);
            if (espejo != null) b |= EstadoPacket.ESPEJISMO;
        }
        int pasos = 0;
        if (tiene(v, PocionesRegistro.GIGANTE.get(), ignorar)) pasos = Math.min(Tamanos.PASOS_MAX, v.getEffect(PocionesRegistro.GIGANTE.get()).getAmplifier() + 1);
        else if (tiene(v, PocionesRegistro.ENANO.get(), ignorar)) pasos = -Math.min(Tamanos.PASOS_MAX, v.getEffect(PocionesRegistro.ENANO.get()).getAmplifier() + 1);
        return new EstadoPacket(v.getId(), b, pasos, espejo, nombre);
    }

    private static boolean tiene(LivingEntity v, MobEffect ef, MobEffect ignorar) {
        return ef != ignorar && v.hasEffect(ef);
    }

    public static void enviarATodos(LivingEntity v, MobEffect ignorar) {
        PocionesRed.alrededor(v, estado(v, ignorar));
    }

    public static void enviarA(LivingEntity v, ServerPlayer quien) {
        PocionesRed.a(quien, estado(v, null));
    }
}
