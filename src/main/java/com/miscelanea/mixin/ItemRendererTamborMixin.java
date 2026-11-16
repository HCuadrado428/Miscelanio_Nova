package com.miscelanea.mixin;

import com.miscelanea.armasfuego.pistola.RevolverItem;
import com.miscelanea.client.armasfuego.pistola.RevolverTambor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El tambor del revólver no está en su modelo 3D: se dibuja aparte (girándolo y con los culotes de cada bala) justo después
 * del armazón, en todo menos en el inventario (que usa el icono plano). Ver {@link RevolverTambor}.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererTamborMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void miscelanea$tambor(ItemStack s, ItemDisplayContext ctx, boolean izq, PoseStack ps, MultiBufferSource buf, int luz, int overlay,
                                   BakedModel m, CallbackInfo ci) {
        if (ctx == ItemDisplayContext.GUI) return;
        if (s.getItem() instanceof RevolverItem) RevolverTambor.dibujar(s, ctx, izq, ps, buf, luz, overlay, m);
        com.miscelanea.client.armasfuego.comun.AccesoriosVisibles.dibujar(s, ctx, izq, ps, buf, luz, overlay, m);
    }
}
