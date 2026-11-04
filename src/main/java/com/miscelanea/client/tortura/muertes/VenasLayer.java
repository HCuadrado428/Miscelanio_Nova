package com.miscelanea.client.tortura.muertes;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Las venas verdes que se le marcan a quien se ha bebido el veneno de Dream (canal "venas" de la cinemática, 0..1): un
 * modelo de jugador un pelo más hinchado que la segunda capa de la skin (y distinto del de las heridas, para no
 * pelearse en profundidad) que copia la pose del padre (también la de playerAnimator) y se dibuja translúcido.
 */
public class VenasLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/venas_veneno.png");
    private final PlayerModel<AbstractClientPlayer> cuerpo, cabeza;

    public VenasLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre, boolean fino) {
        super(padre);
        this.cuerpo = modelo(0.29F, fino);
        this.cabeza = modelo(0.52F, fino);
    }

    private static PlayerModel<AbstractClientPlayer> modelo(float inflado, boolean fino) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflado), fino), 64, 64).bakeRoot(), fino);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt, float pt,
                       float edad, float headYaw, float headPitch) {
        if (MuertesCliente.vacio() || p.isInvisible()) return;
        float k = MuertesCliente.canal(p.getId(), "venas", pt);
        if (Float.isNaN(k) || k <= 0.01F) return;
        k = Mth.clamp(k, 0F, 1F);
        PlayerModel<AbstractClientPlayer> padre = getParentModel();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TEX));
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0.0F);
        padre.copyPropertiesTo(cuerpo);
        cuerpo.setAllVisible(false);
        cuerpo.body.visible = padre.body.visible;
        cuerpo.rightArm.visible = padre.rightArm.visible;
        cuerpo.leftArm.visible = padre.leftArm.visible;
        cuerpo.renderToBuffer(ps, vc, luz, overlay, 1F, 1F, 1F, k);
        if (padre.head.visible) {
            padre.copyPropertiesTo(cabeza);
            cabeza.setAllVisible(false);
            cabeza.head.visible = true;
            cabeza.renderToBuffer(ps, vc, luz, overlay, 1F, 1F, 1F, k);
        }
    }
}
