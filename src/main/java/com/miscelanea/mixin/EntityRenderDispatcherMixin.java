package com.miscelanea.mixin;

import com.miscelanea.client.pociones.Estados;
import com.miscelanea.client.pociones.TamanoCliente;
import com.miscelanea.client.tortura.efectos.FantasmaCliente;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fantasma escondido (ver {@link FantasmaCliente}): el despachador dibuja la sombra, las llamas y la caja (F3+B) FUERA
 * del dibujo de la entidad, así que cancelar el evento de dibujo no basta. Se le hace creer que la entidad es
 * invisible y que no arde. require = 0: si cambia el método, solo se vería la sombra.
 * <p>
 * Además, el tamaño de la sombra redonda: a escala en los gigantes y enanos (la que se ve, ya suavizada) y ninguna en
 * quien es sombra (lleva su propia aura oscura en el suelo).
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

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"),
            index = 6, require = 0)
    private float miscelanea$tamanoSombra(PoseStack ps, MultiBufferSource buf, Entity e, float fuerza, float pt, LevelReader nivel, float radio) {
        try {
            if (Estados.sombra(e)) return 0F;
            float s = TamanoCliente.escalaVista(e, pt);
            return s == 1F ? radio : Math.min(radio * s, 32F);
        } catch (RuntimeException ex) {
            return radio;
        }
    }

    /** Videoclip de la gramola: solo se ve a los que bailan en ella (ver VideoclipCliente). */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$videoclipOcultar(Entity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (com.miscelanea.client.baile.videoclip.VideoclipCliente.ocultar(e)) cir.setReturnValue(false);
    }

    /** ... y con la luz del decorado, no la del sitio de verdad. */
    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true, require = 0)
    private void miscelanea$videoclipLuz(Entity e, float pt, CallbackInfoReturnable<Integer> cir) {
        int l = com.miscelanea.client.baile.videoclip.VideoclipCliente.luzEntidad(e);
        if (l >= 0) cir.setReturnValue(l);
    }
}
