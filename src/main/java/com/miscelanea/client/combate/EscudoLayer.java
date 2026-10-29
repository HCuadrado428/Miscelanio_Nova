package com.miscelanea.client.combate;

import com.miscelanea.hacha.HachaItem;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** El escudo plegable en el antebrazo izquierdo mientras llevas el Hacha Leviatán: pequeño plegado, grande al subirlo. */
public class EscudoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<Integer, Float> APERTURA = new HashMap<>();
    private ItemStack escudo;
    private final boolean fino;

    public EscudoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre, boolean fino) {
        super(padre);
        this.fino = fino;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt, float partial,
                       float edad, float headYaw, float headPitch) {
        if (!(p.getMainHandItem().getItem() instanceof HachaItem) || p.isInvisible()) return;
        if (escudo == null) escudo = new ItemStack(ModItems.ESCUDO_GUARDIAN.get());
        // se abre/cierra suave
        float objetivo = HachaCliente.escudoArriba(p) ? 1F : 0F;
        float a = APERTURA.getOrDefault(p.getId(), 0F);
        a += (objetivo - a) * Math.min(1, Minecraft.getInstance().getDeltaFrameTime() * 0.6F);
        APERTURA.put(p.getId(), a);
        ps.pushPose();
        getParentModel().leftArm.translateAndRotate(ps);
        // cara exterior del antebrazo (x+), algo por debajo del codo
        ps.translate((fino ? 2.5F : 3.0F) / 16F + 0.02F + 0.03F * a, 0.5F, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-90));   // el disco mira hacia fuera del brazo
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(a, 0, 15)));
        float s = Mth.lerp(a, 0.42F, 1.0F);
        ps.scale(s, s, s);
        Minecraft.getInstance().getItemRenderer().renderStatic(p, escudo, ItemDisplayContext.NONE, false, ps, buf, p.level(), luz,
                OverlayTexture.NO_OVERLAY, p.getId());
        ps.popPose();
    }
}
