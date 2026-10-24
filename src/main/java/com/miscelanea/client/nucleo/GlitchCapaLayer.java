package com.miscelanea.client.nucleo;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

/**
 * Durante la carga, parches de "textura que falta" suben por el cuerpo: piernas → torso → brazos → cabeza
 * (como las placas del aliento atómico encendiéndose). Durante el rayo cubren a todo el jugador.
 */
public class GlitchCapaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/glitch_capa.png");
    private final PlayerModel<AbstractClientPlayer> capa;

    public GlitchCapaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        this.capa = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.3F), slim), 64, 64).bakeRoot(), slim);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        float f = NucleoCliente.fase(player.getId(), partialTick);
        if (f < 0 || player.isInvisible()) return;
        getParentModel().copyPropertiesTo(capa);
        capa.setAllVisible(false);
        capa.rightLeg.visible = capa.leftLeg.visible = f > 0.05F;
        capa.body.visible = f > 0.3F;
        capa.rightArm.visible = capa.leftArm.visible = f > 0.55F;
        capa.head.visible = f > 0.8F;
        AnimatableTexture.setAndUpdate(TEX);
        float a = f >= 1.0F ? 1.0F : 0.75F;
        capa.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX)), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, a);
    }
}
