package com.miscelanea.feria;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Como en las barcas: el pasajero gira con su asiento y su cuerpo mira hacia delante; la cabeza puede mirar 105° a cada lado. */
public final class Montar {
    private Montar() {}

    /** Gira al pasajero lo que ha girado el asiento este tic y lo encara hacia {@code yaw}. */
    public static void seguir(Entity p, float giro, float yaw) {
        p.setYRot(p.getYRot() + giro);
        p.setYHeadRot(p.getYHeadRot() + giro);
        encarar(p, yaw);
    }

    public static void encarar(Entity p, float yaw) {
        if (p instanceof LivingEntity le) {
            le.setYBodyRot(yaw);
            le.yBodyRotO = yaw;
        }
        float f = Mth.wrapDegrees(p.getYRot() - yaw);
        float g = Mth.clamp(f, -105.0F, 105.0F);
        p.yRotO += g - f;
        p.setYRot(p.getYRot() + g - f);
        p.setYHeadRot(p.getYRot());
    }
}
