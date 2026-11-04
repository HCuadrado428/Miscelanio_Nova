package com.miscelanea.mixin;

import com.miscelanea.client.tortura.efectos.FantasmaCliente;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Justo antes de dibujar el modelo (después de TODAS las animaciones, también las propias de cada mob): pose de brazos
 * atados de los mobs humanoides con grilletes y su forcejeo en la ejecución de la trampa de oso. require = 0: si otra versión cambia el método, solo se pierde la pose.
 * <p>
 * Fantasma (ver {@link FantasmaCliente}): cuando asoma por el rabillo del ojo se dibuja translúcido y pálido (tipo de
 * dibujo translúcido, color azulado con la opacidad del fundido) y sin capas (armadura, objetos, flechas clavadas...).
 * También con require = 0: si falla, el fantasma asoma opaco y con todo puesto.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected abstract RenderType getRenderType(LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing);

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
            require = 0)
    private void miscelanea$grilletes(LivingEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz, CallbackInfo ci) {
        com.miscelanea.client.tortura.PoseGrilletes.posar(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), e);
        com.miscelanea.client.tortura.EjecucionCliente.posar(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), e, pt);
        com.miscelanea.client.tortura.muertes.MuertesCliente.posar(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), e, pt);
        com.miscelanea.client.tortura.castigo.PoseCastigo.posar(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), e, pt);
    }

    /** Al empezar a dibujar cada entidad: se guarda cuál es, para los @ModifyArg del color del fantasma. */
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), require = 0)
    private void miscelanea$empieza(LivingEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz, CallbackInfo ci) {
        miscelanea$actual = e;
        miscelanea$pt = pt;
    }

    /** Fantasma: tipo de dibujo translúcido (si el normal era nulo, sigue sin dibujarse: invisibilidad de vanilla). */
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"),
            require = 0)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private RenderType miscelanea$fantasmaTipo(LivingEntityRenderer r, LivingEntity e, boolean cuerpoVisible, boolean translucido, boolean brilla) {
        RenderType normal = getRenderType(e, cuerpoVisible, translucido, brilla);
        if (normal == null || !FantasmaCliente.dibujoFantasma(e)) return normal;
        return RenderType.entityTranslucent(r.getTextureLocation(e));
    }

    /** Entidad que se está dibujando (la guarda la inyección de justo antes de renderToBuffer; hilo de render). */
    private static LivingEntity miscelanea$actual;
    private static float miscelanea$pt;

    /* Fantasma: color pálido azulado y la opacidad del fundido (argumentos 4..7 = r, g, b, a de renderToBuffer).
     * Con @ModifyArg y no @ModifyArgs: en Forge @ModifyArgs genera una clase sintética que el cargador no encuentra. */
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), index = 4, require = 0)
    private float miscelanea$fantasmaRojo(float v) {
        return miscelanea$actual != null && FantasmaCliente.dibujoFantasma(miscelanea$actual) ? 0.82F : v;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), index = 5, require = 0)
    private float miscelanea$fantasmaVerde(float v) {
        return miscelanea$actual != null && FantasmaCliente.dibujoFantasma(miscelanea$actual) ? 0.92F : v;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), index = 6, require = 0)
    private float miscelanea$fantasmaAzul(float v) {
        return miscelanea$actual != null && FantasmaCliente.dibujoFantasma(miscelanea$actual) ? 1.0F : v;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), index = 7, require = 0)
    private float miscelanea$fantasmaAlfa(float v) {
        return miscelanea$actual != null && FantasmaCliente.dibujoFantasma(miscelanea$actual) ? Math.min(FantasmaCliente.alfa(miscelanea$actual, miscelanea$pt), v) : v;
    }

    /** Fantasma: sin capas (armadura, objetos en la mano, flechas, capa, élitros, collar...). */
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"),
            require = 0)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void miscelanea$fantasmaCapas(RenderLayer capa, PoseStack ps, MultiBufferSource buf, int luz, Entity e, float limb, float limbAmount,
                                          float pt, float edad, float cabezaYaw, float cabezaPitch) {
        if (FantasmaCliente.dibujoFantasma(e)) return;
        capa.render(ps, buf, luz, e, limb, limbAmount, pt, edad, cabezaYaw, cabezaPitch);
    }
}
