package com.miscelanea.client.tortura.castigo;

import com.miscelanea.client.tortura.TorturaLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * Capa para todas las entidades vivas: el grillete del tobillo de la bola de preso (el mismo modelo que el de las
 * muñecas) y el punto de donde sale su cadena (en espacio de vista, para dibujarla luego en el mundo).
 */
public class CastigoLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** Altura del tobillo en la pierna (píxeles desde la cadera). */
    static final float TOBILLO_Y = 10.4F;

    public CastigoLayer(RenderLayerParent<T, M> padre) {
        super(padre);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, T e, float limb, float limbAmt, float pt, float edad, float yawCabeza, float pitchCabeza) {
        if (e.isInvisible() || CastigoCliente.bolaDe(e.getId()) == null) return;
        if (!(getParentModel() instanceof HumanoidModel<?> hm)) return;
        ps.pushPose();
        hm.rightLeg.translateAndRotate(ps);
        ps.translate(0, TOBILLO_Y / 16F, 0);
        // el punto de la argolla (detrás del tobillo, hacia fuera)
        CastigoCliente.capturarTobillo(e.getId(), ps.last().pose().transformPosition(new Vector3f(-1.2F / 16F, 0.4F / 16F, 2.9F / 16F)));
        ps.mulPose(Axis.ZP.rotationDegrees(180));
        ps.translate(-0.5F, -0.5F, -0.5F);
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(TorturaLayer.GRILLETE);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }
}
