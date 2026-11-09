package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
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

/**
 * Los ojos verdes de Dream (y de los infectados) que brillan en la oscuridad, como los de una araña: una cabeza un pelo
 * más grande que la segunda capa de la skin con solo dos ojos pintados, dibujada con el tipo de render de los ojos
 * (no le afecta la luz). Solo si a la cabeza le llega poca luz (7 o menos).
 */
public class OjosLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/dream_ojos.png");
    private static final RenderType TIPO = RenderType.eyes(TEX);
    private final PlayerModel<AbstractClientPlayer> cabeza;
    /** Solo para la prueba del CI: veces que se han dibujado. */
    public static int pruebaDibujados;

    public OjosLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre, boolean fino) {
        super(padre);
        this.cabeza = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.56F), fino), 64, 64).bakeRoot(), fino);
    }

    /** La luz que le llega de verdad: la de bloques o la del cielo según la hora (0..15). */
    static int luz(AbstractClientPlayer p, int empaquetada, float pt) {
        int bloque = net.minecraft.client.renderer.LightTexture.block(empaquetada);
        int cielo = net.minecraft.client.renderer.LightTexture.sky(empaquetada);
        // getSkyDarken: 1 a mediodía, 0,2 a medianoche
        float dia = net.minecraft.util.Mth.clamp((((net.minecraft.client.multiplayer.ClientLevel) p.level()).getSkyDarken(pt) - 0.2F) / 0.8F, 0F, 1F);
        return Math.max(bloque, Math.round(cielo * dia));
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt, float pt,
                       float edad, float headYaw, float headPitch) {
        if (p.isInvisible() || !PersecucionCliente.brilla(p)) return;
        PlayerModel<AbstractClientPlayer> padre = getParentModel();
        if (!padre.head.visible) return;
        if (luz(p, luz, pt) > 7) return;
        padre.copyPropertiesTo(cabeza);
        cabeza.setAllVisible(false);
        cabeza.head.visible = true;
        cabeza.renderToBuffer(ps, buf.getBuffer(TIPO), 0xF000F0, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pruebaDibujados++;
    }
}
