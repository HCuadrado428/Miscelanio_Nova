package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Charcos de estática: al andar, el hombre de estática va dejando charcos (no son bloques: los dibuja el cliente). Quien
 * los pisa (que no sea de estática) va lento, oye un chisporroteo y ve nieve por los bordes de la pantalla.
 */
public final class Charcos {
    private Charcos() {}

    private record Charco(ResourceKey<Level> dim, Vec3 pos, long hasta) {}

    private static final Map<UUID, Deque<Charco>> CHARCOS = new HashMap<>();
    private static final Map<UUID, Vec3> ULTIMO = new HashMap<>();
    /** Último chisporroteo de cada víctima (id de entidad → tick), para no repetirlo cada vez. */
    private static final Map<Integer, Long> PISADO = new HashMap<>();

    static void tickJugador(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        long t = l.getGameTime();
        if (t % 5 != 0 || !p.onGround() || p.isSpectator()) return;
        Vec3 u = ULTIMO.get(p.getUUID());
        Vec3 aqui = p.position();
        if (u != null && u.distanceToSqr(aqui) < 2.2 * 2.2) return;
        if (u != null && p.getRandom().nextFloat() > 0.45F) return;
        ULTIMO.put(p.getUUID(), aqui);
        if (u == null) return;
        int vida = ConfigServidor.estaticaCharcosVidaTicks();
        Deque<Charco> d = CHARCOS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        Vec3 pos = aqui.add(p.getRandom().nextGaussian() * 0.2, 0.02, p.getRandom().nextGaussian() * 0.2);
        d.addLast(new Charco(l.dimension(), pos, t + vida));
        while (d.size() > ConfigServidor.estaticaCharcosMax()) d.removeFirst();
        EstaticaRed.cerca(l, pos.x, pos.y, pos.z, 48, new EstaticaRed.Charco(pos.x, pos.y, pos.z, vida, p.getRandom().nextInt()));
    }

    static void tick(MinecraftServer srv) {
        if (CHARCOS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Deque<Charco>>> it = CHARCOS.entrySet().iterator(); it.hasNext(); ) {
            Deque<Charco> d = it.next().getValue();
            for (Iterator<Charco> ic = d.iterator(); ic.hasNext(); ) {
                Charco c = ic.next();
                ServerLevel l = srv.getLevel(c.dim);
                if (l == null || l.getGameTime() > c.hasta) {
                    ic.remove();
                    continue;
                }
                if (!l.isLoaded(net.minecraft.core.BlockPos.containing(c.pos))) continue;
                AABB caja = new AABB(c.pos.x - 0.7, c.pos.y - 0.3, c.pos.z - 0.7, c.pos.x + 0.7, c.pos.y + 0.5, c.pos.z + 0.7);
                long t = l.getGameTime();
                for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, caja, e -> e.isAlive() && !Estatica.es(e) && !e.isSpectator())) {
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, true));
                    Long ult = PISADO.get(v.getId());
                    if (ult != null && t - ult < 20) continue;
                    PISADO.put(v.getId(), t);
                    Estatica.sonido(l, v.position(), EstaticaRegistro.CRUJIDO.get(), 0.7F, 1.1F + l.random.nextFloat() * 0.3F);
                    if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(14, true, 0));
                }
            }
            if (d.isEmpty()) it.remove();
        }
        if (PISADO.size() > 512) PISADO.clear();
    }

    static void olvidar(UUID u) {
        ULTIMO.remove(u);
    }

    static void limpiar() {
        CHARCOS.clear();
        ULTIMO.clear();
        PISADO.clear();
    }
}
