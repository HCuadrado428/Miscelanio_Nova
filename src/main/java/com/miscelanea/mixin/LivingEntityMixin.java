package com.miscelanea.mixin;

import com.miscelanea.client.tortura.efectos.FantasmaCliente;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Solo en el cliente (va en la lista "client" de miscelanea.mixins.json). Las burbujas de poción las crea cada cliente
 * en {@code tickEffects} a partir del color sincronizado: un fantasma no suelta ninguna, ni de su propio efecto (que
 * el servidor ya pone sin partículas) ni de los demás que lleve, porque delatarían dónde está. require = 0.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Redirect(method = "tickEffects",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"),
            require = 0)
    private void miscelanea$fantasmaBurbujas(Level level, ParticleOptions tipo, double x, double y, double z, double vx, double vy, double vz) {
        // (en el servidor integrado addParticle no hace nada, pero no se toca el estado del cliente desde su hilo)
        if (level.isClientSide && FantasmaCliente.esFantasma((LivingEntity) (Object) this)) return;
        level.addParticle(tipo, x, y, z, vx, vy, vz);
    }
}
