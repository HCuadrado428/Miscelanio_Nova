package com.miscelanea.carcel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Ruido que atrae a los monstruos cercanos hacia un punto (cristales, latas, carro, botellas...). */
public final class Ruido {
    private Ruido() {}

    public static void alertar(ServerLevel level, Vec3 donde, double radio, @Nullable Entity causante) {
        AABB caja = new AABB(donde, donde).inflate(radio);
        for (Monster m : level.getEntitiesOfClass(Monster.class, caja, Monster::isAlive)) {
            if (m.getTarget() != null) continue; // ya está persiguiendo a alguien
            m.getNavigation().moveTo(donde.x, donde.y, donde.z, 1.15);
            m.getLookControl().setLookAt(donde);
        }
        // Para el warden y los sensores de sculk
        level.gameEvent(causante, GameEvent.BLOCK_ACTIVATE, BlockPos.containing(donde));
    }
}
