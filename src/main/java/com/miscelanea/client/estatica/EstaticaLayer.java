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
        // la máscara sigue a la piel real (tamaño, brazos finos, capa exterior y ojos) y se dibuja con las mismas piezas del
        // modelo padre, ya posadas (si CPM sustituye el modelo, dibuja sus piezas con esta textura)
        Mascara mk = EstaticaCliente.mascara(id);
        mk.adaptar(p);
        if (cob > 0) {
            mk.actualizar(cob, Util.getMillis() + id * 37L);
            m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(mk.rl)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        }
        if (ojos > 0) {
            float parp = ojos * parpadeoOjos(id, p.tickCount + pt);
            m.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(mk.rlOjos)), 0xF000F0, OverlayTexture.NO_OVERLAY, parp, parp, parp, 1F);
        }
    }

    /** Brillo de los ojos: late despacio y, de vez en cuando, se cortan un instante (como una señal que se pierde). */
    static float parpadeoOjos(int id, float t) {
        float b = 0.86F + 0.14F * net.minecraft.util.Mth.sin(t * 0.21F);
        int q = (int) (t / 2);
        if (Mascara.hash(id, q, 3, 59) < 0.05F) b *= 0.3F;
        else if (Mascara.hash(id, q, 4, 59) < 0.08F) b = Math.min(1F, b * 1.15F);
        return b;
    }
}
