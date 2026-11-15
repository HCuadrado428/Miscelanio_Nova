package com.miscelanea.armasfuego.comun;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ir a ver de dónde ha venido un ruido (un disparo). Un monstruo sin objetivo que lo oye camina hasta el sitio y, si por el
 * camino ve a alguien, sus objetivos de siempre hacen el resto. Es un objetivo con el movimiento reservado, de prioridad
 * media: no pisa el ataque, pero sí el paseo al azar. Se olvida si llega, si pasa el tiempo o si encuentra un objetivo.
 */
public final class InvestigarRuido extends Goal {
    private record Aviso(Vec3 donde, long hasta) {}

    /** Solo en el hilo del servidor. */
    private static final Map<Mob, Aviso> AVISOS = new WeakHashMap<>();

    private final Mob m;

    private InvestigarRuido(Mob m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** El mob irá a {@code donde} durante {@code ticks} como mucho. */
    public static void avisar(Mob m, Vec3 donde, int ticks) {
        boolean tiene = m.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof InvestigarRuido);
        if (!tiene) m.goalSelector.addGoal(3, new InvestigarRuido(m));
        AVISOS.put(m, new Aviso(donde, m.level().getGameTime() + ticks));
    }

    /** ¿Va de camino a un ruido? (para la prueba) */
    public static boolean investigando(Mob m) {
        Aviso a = AVISOS.get(m);
        return a != null && m.level().getGameTime() < a.hasta();
    }

    @Override
    public boolean canUse() {
        Aviso a = AVISOS.get(m);
        if (a == null) return false;
        if (m.level().getGameTime() >= a.hasta() || m.getTarget() != null || m.distanceToSqr(a.donde()) < 6.25) {
            AVISOS.remove(m);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        ir();
    }

    @Override
    public void tick() {
        // si la navegación se ha parado (un obstáculo, otro objetivo), se vuelve a pedir el camino cada segundo
        if (m.tickCount % 20 == 0 && m.getNavigation().isDone()) ir();
    }

    @Override
    public void stop() {
        AVISOS.remove(m);
    }

    private void ir() {
        Aviso a = AVISOS.get(m);
        if (a != null) m.getNavigation().moveTo(a.donde().x, a.donde().y, a.donde().z, 1.0);
    }
}
