package com.miscelanea.mixin;

import com.miscelanea.client.tortura.efectos.FantasmaCliente;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fantasma escondido (ver {@link FantasmaCliente}): el despachador dibuja la sombra, las llamas y la caja (F3+B) FUERA
 * del dibujo de la entidad, así que cancelar el evento de dibujo no basta. Se le hace creer que la entidad es
 * invisible y que no arde. require = 0: si cambia el método, solo se vería la sombra.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Redirect(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isInvisible()Z"), require = 0)
    private boolean miscelanea$fantasmaSombra(Entity e) {
        // un fantasma no tiene sombra ni cuando asoma (tampoco uno mismo en tercera persona)
        return e.isInvisible() || FantasmaCliente.dibujoFantasma(e);
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"), require = 0)
    private boolean miscelanea$fantasmaFuego(Entity e) {
        return e.displayFireAnimation() && !FantasmaCliente.oculto(e);
    }
}
