package com.miscelanea.client.pipboy;

import com.miscelanea.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** El Pip-Boy puesto en el antebrazo izquierdo. */
public class PipBoyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final boolean fino;
    private ItemStack pip;

    public PipBoyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre, boolean fino) {
        super(padre);
        this.fino = fino;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt, float partial,
                       float edad, float headYaw, float headPitch) {
        if (!PipBoyCliente.lleva(p) || p.isInvisible()) return;
        if (pip == null) pip = new ItemStack(ModItems.PIPBOY.get());
        ps.pushPose();
        getParentModel().leftArm.translateAndRotate(ps);
        ps.translate(((fino ? 0.5F : 1.0F) + 2.9F) / 16F, 6.6F / 16F, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-90));
        Minecraft.getInstance().getItemRenderer().renderStatic(p, pip, ItemDisplayContext.NONE, false, ps, buf, p.level(), luz,
                OverlayTexture.NO_OVERLAY, p.getId());
        ps.popPose();
    }
}
