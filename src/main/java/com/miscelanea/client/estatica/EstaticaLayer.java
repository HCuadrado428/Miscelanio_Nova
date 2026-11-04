package com.miscelanea.client.estatica;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * La estática sobre el cuerpo: el mismo modelo del jugador (ya posado) con la textura viva de su {@link Mascara}
 * (translúcida, con luz propia) y, encima, los ojos blancos que brillan (como los de las arañas). Durante la
 * transformación la cobertura y los ojos siguen a la cinemática.
 */
public class EstaticaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public EstaticaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre) {
        super(padre);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt, float pt, float edad,
                       float cabY, float cabX) {
        int id = p.getId();
        if (!EstaticaCliente.conEstatica(id) || p.isInvisible()) return;
        float cob = EstaticaCine.cobertura(id, pt), ojos = EstaticaCine.ojos(id, pt);
        if (Float.isNaN(cob)) cob = 1F;
        if (Float.isNaN(ojos)) ojos = 1F;
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        if (cob > 0) {
            Mascara mk = EstaticaCliente.mascara(id);
            mk.actualizar(cob, Util.getMillis() + id * 37L);
            m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(mk.rl)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        }
        if (ojos > 0) {
            float parp = ojos * (0.85F + 0.15F * net.minecraft.util.Mth.sin((p.tickCount + pt) * 0.21F));
            m.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(EstaticaCliente.OJOS)), 0xF000F0, OverlayTexture.NO_OVERLAY, parp, parp, parp, 1F);
        }
    }
}
