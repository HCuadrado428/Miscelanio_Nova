package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilItem;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Si alguien usa el móvil (lo abrió con la tecla, sin tenerlo en la mano) y la mano que lo sujeta está vacía, se le dibuja
 * el móvil en ella igual que haría {@code ItemInHandLayer}.
 */
public class MovilManoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public MovilManoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre) {
        super(padre);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float a, float b, float pt, float c, float d, float e) {
        if (MovilCliente.poseDe(p) == MovilRed.POSE_NADA || p.isSpectator()) return;
        if (MovilRegistro.esMovil(p.getMainHandItem()) || MovilRegistro.esMovil(p.getOffhandItem())) return;
        boolean izq = AnimMovil.izquierda(p);
        HumanoidArm brazo = izq ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        boolean principal = brazo == p.getMainArm();
        if (!(principal ? p.getMainHandItem() : p.getOffhandItem()).isEmpty()) return;
        ItemStack s = p == Minecraft.getInstance().player ? MovilCliente.movilDe(p).copy() : ItemStack.EMPTY;
        if (s.isEmpty()) {
            s = new ItemStack(MovilRegistro.MOVIL.get());
            s.getOrCreateTag().putInt(MovilItem.COLOR, MovilItem.COLOR_BASE);
        }
        ps.pushPose();
        getParentModel().translateToHand(brazo, ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.translate((izq ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, s,
                izq ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, izq, ps, buf, luz);
        ps.popPose();
    }
}
