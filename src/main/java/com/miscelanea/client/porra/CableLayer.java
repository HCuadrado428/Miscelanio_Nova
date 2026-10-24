package com.miscelanea.client.porra;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.Tubo;
import com.miscelanea.porra.BateriaItem;
import com.miscelanea.porra.PorraItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Cable (con textura) desde los bornes de la batería de la espalda hasta la mano de la porra. */
public class CableLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/cable.png");

    public CableLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static Matrix4f parte(ModelPart p) {
        return new Matrix4f().translate(p.x / 16.0F, p.y / 16.0F, p.z / 16.0F).rotateZYX(p.zRot, p.yRot, p.xRot);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (BateriaItem.de(player).isEmpty()) return;

        HumanoidArm brazo;
        if (player.getMainHandItem().getItem() instanceof PorraItem) brazo = player.getMainArm();
        else if (player.getOffhandItem().getItem() instanceof PorraItem) brazo = player.getMainArm().getOpposite();
        else return;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        boolean izq = brazo == HumanoidArm.LEFT;
        float lado = izq ? 1.0F : -1.0F;

        // Borne de la batería (arriba de la espalda) y mano
        Vector3f borne = parte(model.body).transformPosition(new Vector3f(lado * 2.5F / 16.0F, 0.0F, 3.6F / 16.0F));
        ModelPart arm = izq ? model.leftArm : model.rightArm;
        Vector3f mano = parte(arm).transformPosition(new Vector3f(-lado * 1.0F / 16.0F, 10.5F / 16.0F, 0.0F));

        // Sale hacia arriba y atrás del borne, pasa por el hombro y baja colgando hasta la mano
        Vector3f c1 = new Vector3f(borne).add(lado * 1.5F / 16.0F, -3.0F / 16.0F, 2.0F / 16.0F);
        Vector3f c2 = new Vector3f(mano).add(0.0F, 5.0F / 16.0F, 2.0F / 16.0F);
        Tubo.dibujar(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURA)), light,
                borne, c1, c2, mano, 0.45F / 16.0F, 18, 7.2F / 16.0F);
    }
}
