package com.miscelanea.client.tortura.heridas;

import com.miscelanea.tortura.heridas.HeridasDatos;
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

/**
 * Capa de heridas sobre la skin. Usa un modelo de jugador propio, apenas más hinchado que la segunda capa de la skin
 * (chaqueta/mangas = 0.25, sombrero = 0.5) para no pelearse con ella en profundidad, y copia la pose del modelo padre
 * en cada frame: las heridas siguen la animación (y las de playerAnimator, que mueve las piezas del padre).
 * <p>
 * Customizable Player Models (modid "cpm", opcional): no se usa su API. Esta capa se sigue añadiendo al PlayerRenderer
 * y dibuja con la forma del modelo normal; si un modelo de CPM cambia mucho la silueta (o esconde piezas del padre),
 * las heridas pueden quedar flotando o no verse en esas piezas, pero nunca rompe nada. La primera persona y el HUD
 * no dependen de CPM.
 */
public class HeridasLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Cuerpo, brazos y piernas: justo por encima de la chaqueta y las mangas. */
    static final float INFLADO = 0.33F;
    /** Cabeza: por encima del "sombrero" (pelo, gorras de la skin). */
    static final float INFLADO_CABEZA = 0.56F;

    private final boolean fino;
    private final PlayerModel<AbstractClientPlayer> cuerpo, cabeza;

    public HeridasLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> padre, boolean fino) {
        super(padre);
        this.fino = fino;
        this.cuerpo = modelo(INFLADO, fino);
        this.cabeza = modelo(INFLADO_CABEZA, fino);
    }

    static PlayerModel<AbstractClientPlayer> modelo(float inflado, boolean fino) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflado), fino), 64, 64).bakeRoot(), fino);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt, float partial,
                       float edad, float headYaw, float headPitch) {
        if (p.isInvisible()) return;
        HeridasDatos d = HeridasCliente.datos(p);
        if (d == null || d.vacio()) return;
        ResourceLocation tex = HeridasTextura.de(p.getId(), d, fino);
        PlayerModel<AbstractClientPlayer> padre = getParentModel();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(tex));
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0.0F);   // el destello rojo al recibir daño, como el cuerpo

        padre.copyPropertiesTo(cuerpo);
        cuerpo.setAllVisible(false);
        cuerpo.body.visible = tiene(d, Parte.TORSO) && padre.body.visible;
        cuerpo.rightArm.visible = tiene(d, Parte.BRAZO_DER) && padre.rightArm.visible;
        cuerpo.leftArm.visible = tiene(d, Parte.BRAZO_IZQ) && padre.leftArm.visible;
        cuerpo.rightLeg.visible = tiene(d, Parte.PIERNA_DER) && padre.rightLeg.visible;
        cuerpo.leftLeg.visible = tiene(d, Parte.PIERNA_IZQ) && padre.leftLeg.visible;
        cuerpo.renderToBuffer(ps, vc, luz, overlay, 1F, 1F, 1F, 1F);

        if (tiene(d, Parte.CABEZA) && padre.head.visible) {
            padre.copyPropertiesTo(cabeza);
            cabeza.setAllVisible(false);
            cabeza.head.visible = true;
            cabeza.renderToBuffer(ps, vc, luz, overlay, 1F, 1F, 1F, 1F);
        }
    }

    static boolean tiene(HeridasDatos d, Parte p) {
        return d.vendada(p) || d.cuantas(p) > 0 || d.marcada(p);
    }
}
