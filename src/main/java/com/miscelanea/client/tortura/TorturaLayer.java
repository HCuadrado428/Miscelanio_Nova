package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * Capa para TODAS las entidades vivas: la trampa de oso en la cabeza (con engranajes que giran y el reloj encendido
 * detrás), los grilletes en las muñecas con su cadenita, y la captura de puntos para la cadena-correa.
 * Las coordenadas de los modelos (en píxeles de bloque) tienen que coincidir con scratchpad/gen_tortura1.py.
 */
public class TorturaLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation TRAMPA = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/trampa_oso_puesta");
    public static final ResourceLocation ENGRANAJE = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/trampa_oso_engranaje");
    public static final ResourceLocation GRILLETE = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/grillete_muneca");

    /** Centros de los engranajes (x, y, z) y sentido de giro. La cabeza ocupa 4..12 en los tres ejes; delante = z pequeña. */
    static final float[][] ENGRANAJES = {{2.0F, 10.3F, 8.4F, 1F}, {14.0F, 10.3F, 8.4F, -1F}};
    /** Centro de la pantalla del reloj (cara de atrás de la caja). */
    static final float RELOJ_X = 8F, RELOJ_Y = 9.55F, RELOJ_Z = 15.3F;
    /** Altura de la muñeca en el brazo (píxeles desde el hombro). */
    static final float MUNECA_Y = 8.7F;

    public TorturaLayer(RenderLayerParent<T, M> padre) {
        super(padre);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, T e, float limb, float limbAmt, float pt, float edad, float yawCabeza, float pitchCabeza) {
        TorturaCliente.Estado est = TorturaCliente.estado(e.getId());
        boolean tira = TorturaCliente.esQuienTira(e.getId());
        if ((est == null && !tira) || e.isInvisible()) return;
        M m = getParentModel();
        if (est != null && est.trampa > 0) trampa(ps, buf, luz, e, m, est, pt);
        if (est != null && est.grilletes) grilletes(ps, buf, luz, e, m, est);
        if (tira && m instanceof ArmedModel am) {
            ps.pushPose();
            am.translateToHand(HumanoidArm.RIGHT, ps);
            TorturaCliente.capturarMano(e.getId(), ps.last().pose().transformPosition(new Vector3f(-1F / 16F, 10F / 16F, 0)));
            ps.popPose();
        }
    }

    private static void modelo(PoseStack ps, MultiBufferSource buf, ResourceLocation rl, int luz) {
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(rl);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    // ------------------------------------------------------------------ trampa
    private void trampa(PoseStack ps, MultiBufferSource buf, int luz, T e, M m, TorturaCliente.Estado est, float pt) {
        ps.pushPose();
        if (m instanceof HeadedModel hm) {
            hm.getHead().translateAndRotate(ps);
        } else {
            // sin cabeza conocida: encima del todo, a ojo
            ps.translate(0, 1.501F - e.getBbHeight() + 0.5F, 0);
        }
        // del espacio del modelo (y hacia abajo) al de los modelos de bloque, centrado en la cabeza
        ps.translate(0, -0.25F, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(180));
        ps.translate(-0.5F, -0.5F, -0.5F);
        modelo(ps, buf, TRAMPA, luz);
        // engranajes girando (más deprisa al final)
        float t = e.tickCount + pt;
        float vel = est.trampa <= 200 ? 14F : 5F;
        for (float[] g : ENGRANAJES) {
            ps.pushPose();
            ps.translate(g[0] / 16F, g[1] / 16F, g[2] / 16F);
            ps.mulPose(Axis.XP.rotationDegrees(t * vel * g[3]));
            ps.translate(-0.5F, -0.5F, -0.5F);
            modelo(ps, buf, ENGRANAJE, luz);
            ps.popPose();
        }
        // reloj encendido en la caja de atrás
        int seg = Math.max(0, (est.trampa + 19) / 20);
        boolean parpadeo = seg <= 10 && (e.tickCount / 5) % 2 == 0;
        String txt = String.format("%d:%02d", seg / 60, seg % 60);
        Font f = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(RELOJ_X / 16F, RELOJ_Y / 16F, RELOJ_Z / 16F);
        float s = (1.05F / 16F) / 7F;
        ps.scale(s, -s, s);
        f.drawInBatch(txt, -f.width(txt) / 2F, -3.5F, parpadeo ? 0xFF7A0A0A : 0xFFFF2A20, false, ps.last().pose(), buf,
                Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
        ps.popPose();
        ps.popPose();
    }

    // ------------------------------------------------------------------ grilletes
    private void grilletes(PoseStack ps, MultiBufferSource buf, int luz, T e, M m, TorturaCliente.Estado est) {
        if (!(m instanceof HumanoidModel<?> hm)) return;
        boolean fino = e instanceof AbstractClientPlayer acp && "slim".equals(acp.getModelName());
        float dx = (fino ? 0.5F : 1.0F) / 16F;
        Vector3f der = muneca(ps, buf, luz, hm.rightArm, -dx, 1);
        Vector3f izq = muneca(ps, buf, luz, hm.leftArm, dx, -1);
        // cadenita entre las dos muñecas (en el espacio del modelo, "abajo" es +y)
        VertexConsumer vc = buf.getBuffer(Cadenas.TIPO);
        Cadenas.dibujar(ps, vc, der, izq, new Vector3f(0, 1, 0), 0.07F, luz, 0.055F);
        if (est.cadena >= 0) {
            Vector3f medio = new Vector3f(der).add(izq).mul(0.5F);
            TorturaCliente.capturarMunecas(e.getId(), ps.last().pose().transformPosition(medio));
        }
    }

    /** Dibuja un grillete en la muñeca y devuelve (en el espacio del modelo) el punto de su argolla interior. */
    private static Vector3f muneca(PoseStack ps, MultiBufferSource buf, int luz, ModelPart brazo, float dx, int haciaDentro) {
        ps.pushPose();
        brazo.translateAndRotate(ps);
        ps.translate(dx, MUNECA_Y / 16F, 0);
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(180));
        ps.translate(-0.5F, -0.5F, -0.5F);
        modelo(ps, buf, GRILLETE, luz);
        ps.popPose();
        ps.popPose();
        // el mismo punto calculado sin el PoseStack de la capa: brazo + desplazamiento
        PoseStack tmp = new PoseStack();
        brazo.translateAndRotate(tmp);
        tmp.translate(dx, MUNECA_Y / 16F, 0);
        return tmp.last().pose().transformPosition(new Vector3f(haciaDentro * 2.6F / 16F, 0, -0.6F / 16F));
    }
}
