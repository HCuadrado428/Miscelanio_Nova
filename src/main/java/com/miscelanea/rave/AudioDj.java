package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Audio del ordenador del DJ por la mesa: el cliente del DJ captura lo que suena en su ordenador (Spotify, lo que sea), lo
 * comprime en Opus y lo manda al servidor, que lo reproduce con Simple Voice Chat como sonido de posición desde la mesa y
 * sus altavoces (hasta el alcance de la mesa). Esta clase no toca la API del voicechat: la salida la pone el plugin
 * ({@link com.miscelanea.rave.voz.RaveVozPlugin}) si el voicechat está instalado.
 */
public final class AudioDj {
    private AudioDj() {}

    /** Lo que pone el plugin del voicechat en el servidor. */
    public interface Salida {
        /** Reproduce un trozo de Opus desde {@code fuente} hasta {@code distancia} bloques, para todos menos {@code salvo}. */
        void enviar(ServerLevel l, BlockPos fuente, double distancia, UUID salvo, byte[] opus);

        void cerrar(BlockPos fuente);
    }

    public static volatile Salida salida;
    /** Altavoces que suenan como mucho a la vez por mesa (además de la propia mesa). */
    public static final int MAX_ALTAVOCES = 4;

    public static boolean disponible() {
        return salida != null;
    }

    /** Un trozo de audio del DJ (ya comprobado que lo manda quien emite en esa mesa). */
    static void recibir(ServerPlayer p, MesaDjBlockEntity mesa, byte[] opus) {
        Salida s = salida;
        if (s == null || !(mesa.getLevel() instanceof ServerLevel l)) return;
        double d = mesa.alcance();
        s.enviar(l, mesa.getBlockPos(), d, p.getUUID(), opus);
        for (BlockPos a : altavoces(mesa)) s.enviar(l, a, d, p.getUUID(), opus);
    }

    static void cerrar(MesaDjBlockEntity mesa) {
        Salida s = salida;
        if (s == null) return;
        s.cerrar(mesa.getBlockPos());
        for (BlockPos a : altavoces(mesa)) s.cerrar(a);
    }

    private static List<BlockPos> altavoces(MesaDjBlockEntity mesa) {
        List<BlockPos> l = new ArrayList<>();
        for (AltavozBlockEntity a : AltavozBlockEntity.SERVIDOR) {
            if (l.size() >= MAX_ALTAVOCES) break;
            if (!a.isRemoved() && a.getLevel() == mesa.getLevel() && a.getBlockPos().distSqr(mesa.getBlockPos()) <= 24 * 24) l.add(a.getBlockPos());
        }
        return l;
    }
}
