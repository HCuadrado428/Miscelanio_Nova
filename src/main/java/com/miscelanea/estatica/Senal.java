package com.miscelanea.estatica;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La Señal del hombre de estática (0-100): se llena al golpear y al acertar poderes; Pausa, Canal perdido, Rebobinar,
 * Sin señal y Grabación la gastan, y la Sobrecarga necesita la barra llena. También guarda el historial de los últimos
 * 4 s del jugador (posición, mirada, vida y golpes) para Rebobinar y Grabación.
 */
public final class Senal {
    private Senal() {}

    public static final int MAX = 100;
    /** Ticks de historial que se guardan (4 s). */
    public static final int HIST = 80;

    private static final Map<UUID, Float> SENAL = new HashMap<>();
    private static final Map<UUID, Integer> ENVIADA = new HashMap<>();

    public record Paso(Vec3 pos, float yaw, float pitch, float vida, boolean golpe, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {}

    private static final Map<UUID, ArrayDeque<Paso>> HISTORIAL = new HashMap<>();
    private static final Map<UUID, Boolean> GOLPE = new HashMap<>();

    public static float de(ServerPlayer p) {
        return SENAL.getOrDefault(p.getUUID(), 0F);
    }

    public static void ganar(ServerPlayer p, float n) {
        if (n <= 0 || !Estatica.es(p)) return;
        SENAL.put(p.getUUID(), Math.min(MAX, de(p) + n));
        enviar(p, false);
    }

    /** Gasta 'n' si tiene; si no, avisa y devuelve false. */
    public static boolean gastar(ServerPlayer p, int n) {
        if (n <= 0) return true;
        float s = de(p);
        if (s + 0.01F < n) {
            Estatica.aviso(p, net.minecraft.network.chat.Component.translatable("estatica.miscelanea.sin_senal_aviso", n, (int) s));
            return false;
        }
        SENAL.put(p.getUUID(), Math.max(0, s - n));
        enviar(p, false);
        return true;
    }

    public static void vaciar(ServerPlayer p) {
        SENAL.remove(p.getUUID());
        HISTORIAL.remove(p.getUUID());
        enviar(p, true);
    }

    static void enviar(ServerPlayer p, boolean siempre) {
        int v = Math.round(de(p));
        Integer ant = ENVIADA.put(p.getUUID(), v);
        if (siempre || ant == null || ant != v) EstaticaRed.a(p, new EstaticaRed.SenalMsg(v));
    }

    private static final Map<Long, Long> ACIERTOS = new HashMap<>();

    /** Un poder le ha hecho daño a 'v': un poco de Señal (como mucho cada medio segundo por víctima). */
    static void alAcertar(ServerPlayer p, net.minecraft.world.entity.Entity v) {
        long t = p.level().getGameTime();
        long k = ((long) p.getId() << 32) | (v.getId() & 0xFFFFFFFFL);
        Long ant = ACIERTOS.get(k);
        if (ant != null && t - ant < 10 && t >= ant) return;
        if (ACIERTOS.size() > 1024) ACIERTOS.clear();
        ACIERTOS.put(k, t);
        ganar(p, com.miscelanea.ConfigServidor.estaticaSenalPoder() * 0.5F);
    }

    /** Un golpe del jugador en este tick (lo repite la Grabación). */
    public static void golpe(ServerPlayer p) {
        GOLPE.put(p.getUUID(), true);
    }

    static void tick(ServerPlayer p) {
        ArrayDeque<Paso> h = HISTORIAL.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        boolean g = GOLPE.remove(p.getUUID()) != null;
        h.addLast(new Paso(p.position(), p.getYRot(), p.getXRot(), p.getHealth(), g, p.level().dimension()));
        while (h.size() > HIST) h.removeFirst();
        // a oscuras la Señal sube sola, despacito (1 por segundo)
        if (p.tickCount % 20 == 0 && p.level().getMaxLocalRawBrightness(p.blockPosition().above()) <= com.miscelanea.ConfigServidor.estaticaOscuridadLuz())
            ganar(p, 1);
        if (p.tickCount % 100 == 0) enviar(p, true);
    }

    /** Historial (el más viejo primero). */
    static Paso[] historial(ServerPlayer p) {
        ArrayDeque<Paso> h = HISTORIAL.get(p.getUUID());
        return h == null ? new Paso[0] : h.toArray(new Paso[0]);
    }

    static void olvidar(UUID u) {
        SENAL.remove(u);
        ENVIADA.remove(u);
        HISTORIAL.remove(u);
        GOLPE.remove(u);
    }

    static void limpiar() {
        ACIERTOS.clear();
        SENAL.clear();
        ENVIADA.clear();
        HISTORIAL.clear();
        GOLPE.clear();
    }
}
