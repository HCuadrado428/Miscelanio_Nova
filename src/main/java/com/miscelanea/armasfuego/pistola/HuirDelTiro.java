package com.miscelanea.armasfuego.pistola;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Huir de un disparo de fogueo. Mandar al animal a un sitio con la navegación no basta: en el siguiente reparto de
 * objetivos el paseo al azar (o pastar) le cambia el camino y se queda donde estaba. Esto es un objetivo de
 * prioridad 0 con el movimiento reservado: mientras dure el susto, el animal corre lejos del tiro y, si llega,
 * busca otro sitio aún más lejos.
 */
public final class HuirDelTiro extends Goal {
    private record Susto(Vec3 desde, long hasta) {}

    /** Solo en el hilo del servidor. */
    private static final Map<PathfinderMob, Susto> SUSTOS = new WeakHashMap<>();

    private final PathfinderMob m;

    private HuirDelTiro(PathfinderMob m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** El animal sale corriendo lejos de {@code desde} durante {@code ticks}. */
    public static void asustar(PathfinderMob m, Vec3 desde, int ticks) {
        boolean tiene = m.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof HuirDelTiro);
        if (!tiene) m.goalSelector.addGoal(0, new HuirDelTiro(m));
        SUSTOS.put(m, new Susto(desde, m.level().getGameTime() + ticks));
        correr(m, desde);   // que arranque ya, sin esperar al siguiente reparto de objetivos
    }

    static void limpiar() {
        SUSTOS.clear();
    }

    private Susto susto() {
        Susto s = SUSTOS.get(m);
        if (s != null && m.level().getGameTime() > s.hasta()) {
            SUSTOS.remove(m);
            return null;
        }
        return s;
    }

    @Override
    public boolean canUse() {
        return susto() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return susto() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        Susto s = susto();
        if (s != null) correr(m, s.desde());
    }

    @Override
    public void tick() {
        Susto s = susto();
        if (s != null && m.getNavigation().isDone()) correr(m, s.desde());
    }

    @Override
    public void stop() {
        m.getNavigation().stop();
    }

    private static void correr(PathfinderMob m, Vec3 desde) {
        Vec3 lejos = DefaultRandomPos.getPosAway(m, 16, 7, desde);
        if (lejos == null) lejos = LandRandomPos.getPosAway(m, 12, 7, desde);
        if (lejos != null) m.getNavigation().moveTo(lejos.x, lejos.y, lejos.z, 2.0);
    }
}
