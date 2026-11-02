package com.miscelanea.client.tortura.castigo;

import com.miscelanea.tortura.castigo.Mancha;
import com.miscelanea.tortura.heridas.Parte;
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

import java.util.List;

/**
 * Capa de manchas sobre la skin: un modelo de jugador propio un pelo más hinchado que el de las heridas (0,33 / 0,56)
 * para no pelearse con él, que copia la pose del padre (también la de playerAnimator) en cada fotograma.
 */
public class ManchasLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final float INFLADO = 0.40F, INFLADO_CABEZA = 0.62F;
    private final boolean fino;
    private final PlayerModel<AbstractClientPlayer> cuerpo, cabeza;

    public ManchasLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre, boolean fino) {
        super(padre);
        this.fino = fino;
        this.cuerpo = modelo(INFLADO, fino);
        this.cabeza = modelo(INFLADO_CABEZA, fino);
    }

    static PlayerModel<AbstractClientPlayer> modelo(float inflado, boolean fino) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflado), fino), 64, 64).bakeRoot(), fino);
    }

    private static boolean tiene(List<Mancha> l, Parte p) {
        for (Mancha m : l) if (m.parte == p) return true;
        return false;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt, float pt,
                       float edad, float headYaw, float headPitch) {
        if (p.isInvisible()) return;
        List<Mancha> l = ManchasCliente.de(p.getId());
        if (l == null || l.isEmpty()) return;
        ResourceLocation tex = ManchasTextura.de(p.getId(), l, fino);
        PlayerModel<AbstractClientPlayer> padre = getParentModel();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(tex));
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0.0F);
        padre.copyPropertiesTo(cuerpo);
        cuerpo.setAllVisible(false);
        cuerpo.body.visible = tiene(l, Parte.TORSO) && padre.body.visible;
        cuerpo.rightArm.visible = tiene(l, Parte.BRAZO_DER) && padre.rightArm.visible;
        cuerpo.leftArm.visible = tiene(l, Parte.BRAZO_IZQ) && padre.leftArm.visible;
        cuerpo.rightLeg.visible = tiene(l, Parte.PIERNA_DER) && padre.rightLeg.visible;
        cuerpo.leftLeg.visible = tiene(l, Parte.PIERNA_IZQ) && padre.leftLeg.visible;
        cuerpo.renderToBuffer(ps, vc, luz, overlay, 1F, 1F, 1F, 1F);
        if (tiene(l, Parte.CABEZA) && padre.head.visible) {
            padre.copyPropertiesTo(cabeza);
            cabeza.setAllVisible(false);
            cabeza.head.visible = true;
            cabeza.renderToBuffer(ps, vc, luz, overlay, 1F, 1F, 1F, 1F);
        }
    }
}
