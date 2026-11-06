package com.miscelanea.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** El último daño recibido (ventana de invulnerabilidad), para repartir un golpe de arma en partes sin romperla. */
@Mixin(LivingEntity.class)
public interface LivingEntityGolpeAccessor {
    @Accessor("lastHurt")
    float miscelanea$ultimoDano();

    @Accessor("lastHurt")
    void miscelanea$ponerUltimoDano(float v);
}
