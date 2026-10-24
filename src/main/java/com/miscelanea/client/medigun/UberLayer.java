package com.miscelanea.client.medigun;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Capa brillante que envuelve al jugador mientras tiene la ÜberCarga (como el creeper cargado, del color del rayo). */
public class UberLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/ubercarga.png");
    private final PlayerModel<AbstractClientPlayer> capa;

    public UberLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        this.capa = new PlayerModel<>(LayerDefinition.create(
                PlayerModel.createMesh(new CubeDeformation(0.4F), slim), 64, 64).bakeRoot(), slim);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int c = MedigunCliente.colorUber(player);
        if (c < 0 || player.isInvisible()) return;
        getParentModel().copyPropertiesTo(capa);
        capa.setAllVisible(false);
        capa.head.visible = capa.body.visible = capa.rightArm.visible = capa.leftArm.visible = true;
        capa.rightLeg.visible = capa.leftLeg.visible = true;
        float t = ageInTicks + partialTick;
        VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(TEXTURA, (t * 0.01F) % 1.0F, (t * 0.01F) % 1.0F));
        float pulso = 0.75F + 0.25F * (float) Math.sin(t * 0.3F);
        capa.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY,
                ((c >> 16) & 255) / 255.0F * pulso, ((c >> 8) & 255) / 255.0F * pulso, (c & 255) / 255.0F * pulso, 1.0F);
    }
}
