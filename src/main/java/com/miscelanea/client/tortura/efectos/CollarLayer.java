package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Capa para todas las entidades vivas: el collar explosivo al cuello (anillo de acero, módulo con la luz roja, baterías
 * y antena) colgado de la cabeza como la trampa de oso; la luz parpadea según el estado ({@link CollarCliente#luz}).
 * Medidas en píxeles de bloque: la cabeza ocupa 4..12 en los tres ejes y el cuello está en y = 4 (gen_tortura4b.py).
 */
public class CollarLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation COLLAR = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/collar_explosivo_puesto");
    public static final ResourceLocation LUZ = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/collar_explosivo_luz");

    public CollarLayer(RenderLayerParent<T, M> padre) {
        super(padre);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, T e, float limb, float limbAmt, float pt, float edad, float yawCabeza, float pitchCabeza) {
        CollarCliente.Estado est = CollarCliente.estado(e.getId());
        if (est == null || e.isInvisible()) return;
        ps.pushPose();
        if (getParentModel() instanceof HeadedModel hm) {
            hm.getHead().translateAndRotate(ps);
        } else {
            // sin cabeza conocida: a la altura de los ojos, un poco por debajo
            ps.translate(0, 1.501F - e.getEyeHeight() + 0.3F, 0);
        }
        ps.translate(0, -0.25F, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(180));
        if (est.detona >= 0) {
            // tiembla en la cuenta final
            float t = e.tickCount + pt;
            ps.translate(Mth.sin(t * 5.1F) * 0.006F, Mth.sin(t * 6.3F + 1F) * 0.005F, 0);
        }
        ps.translate(-0.5F, -0.5F, -0.5F);
        Minecraft mc = Minecraft.getInstance();
        BakedModel bm = mc.getModelManager().getModel(COLLAR);
        mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        float k = CollarCliente.luz(est, e, pt);
        BakedModel bl = mc.getModelManager().getModel(LUZ);
        int luzLed = k > 0.5F ? 0xF000F0 : luz;
        float a = 0.28F + 0.72F * k;
        mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bl,
                a, 0.25F + 0.75F * a * a, 0.25F + 0.75F * a * a, luzLed, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }
}
