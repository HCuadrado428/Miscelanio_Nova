package com.miscelanea.tortura.efectos;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Miedo (toxina del Espantapájaros, Batman: Arkham). A los jugadores, alucinaciones (todo en el cliente:
 * {@code MiedoCliente}, que va por etapas y empeora con el tiempo); a los mobs, pánico: dejan de atacar y huyen de los
 * jugadores cercanos. Las dosis se acumulan (amplificador hasta III, ver {@code EfectosEventos}). La leche lo cura
 * (como cualquier efecto).
 */
public class MiedoEffect extends MobEffect {
    public static final int COLOR = 0x4E8A2E;

    public MiedoEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean isDurationEffectTick(int duracion, int amplificador) {
        return duracion % 10 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplificador) {
        if (e.level().isClientSide || !(e instanceof PathfinderMob m)) return;
        m.setTarget(null);
        if (m instanceof Creeper c) c.setSwellDir(-1);
        Player p = m.level().getNearestPlayer(m, 20);
        if (p == null || p.isSpectator()) return;
        Vec3 huir = DefaultRandomPos.getPosAway(m, 16, 7, p.position());
        if (huir != null) m.getNavigation().moveTo(huir.x, huir.y, huir.z, 1.45);
    }
}
