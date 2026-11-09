package com.miscelanea.logros;

import com.miscelanea.Miscelanea;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Los logros de Miscelánea (pestaña propia, {@code data/miscelanea/advancements}). Los que no salen con criterios de
 * vanilla usan el criterio {@code miscelanea:accion}; desde cualquier sitio del código basta con
 * {@code LogrosMiscelanea.dar(jugador, "accion")}. Si el jugador no es de verdad (null, un FakePlayer, una entidad que no
 * es jugador) no hace nada, y si se llama fuera del hilo del servidor se pasa a él.
 */
public final class LogrosMiscelanea {
    private LogrosMiscelanea() {}

    @Nullable
    private static LogroTrigger trigger;

    /** En el arranque (commonSetup): da de alta el criterio en CriteriaTriggers. */
    public static void registrar() {
        if (trigger == null) trigger = CriteriaTriggers.register(new LogroTrigger());
    }

    /** El jugador ha hecho {@code accion}: se cumplen los logros que la esperan. */
    public static void dar(@Nullable Entity e, String accion) {
        if (!(e instanceof ServerPlayer p) || p instanceof FakePlayer || accion == null || trigger == null) return;
        MinecraftServer s = p.server;
        if (s == null) return;
        if (!s.isSameThread()) {
            s.execute(() -> dar(p, accion));
            return;
        }
        if (p.hasDisconnected()) return;
        try {
            trigger.trigger(p, accion);
        } catch (Throwable t) {
            Miscelanea.LOGGER.warn("[Logros] no se pudo dar '{}' a {}", accion, p.getGameProfile().getName(), t);
        }
    }

    /** Igual, para un jugador por su UUID (si está conectado). */
    public static void dar(@Nullable MinecraftServer s, @Nullable UUID u, String accion) {
        if (u == null) return;
        if (s == null) s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        dar(s.getPlayerList().getPlayer(u), accion);
    }

    public static void dar(@Nullable UUID u, String accion) {
        dar(null, u, accion);
    }
}
