package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.LazoItem;
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
import net.minecraft.util.Mth;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * Capa para TODAS las entidades vivas: la trampa de oso en la cabeza (con engranajes que giran y el reloj encendido
 * detrás), los grilletes en las muñecas con su cadenita, la captura de puntos para la cadena-correa y el lazo (cerrado
 * en el pecho, las cuerdas del maniatado en muñecas y tobillos y el aro girando sobre la mano de quien lo voltea).
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
        boolean girando = LazoCliente.girando(e);
        if ((est == null && !tira && !girando) || e.isInvisible()) return;
        M m = getParentModel();
        if (est != null && est.trampa > 0) trampa(ps, buf, luz, e, m, est, pt);
        if (est != null && est.grilletes) grilletes(ps, buf, luz, e, m, est);
        if (est != null && est.lazo != 0) lazo(ps, buf, luz, e, m, est);
        if (tira && m instanceof ArmedModel am) {
            // con el lazo, la mano que lo sujeta
            HumanoidArm brazo = LazoItem.usando(e) || e.getMainHandItem().getItem() instanceof LazoItem ? e.getMainArm()
                    : e.getOffhandItem().getItem() instanceof LazoItem ? e.getMainArm().getOpposite() : HumanoidArm.RIGHT;
            if (LazoItem.usando(e)) brazo = LazoCliente.brazoDelLazo(e);
            ps.pushPose();
            am.translateToHand(brazo, ps);
            float lado = brazo == HumanoidArm.RIGHT ? -1F : 1F;
            TorturaCliente.capturarMano(e.getId(), ps.last().pose().transformPosition(new Vector3f(lado / 16F, 10F / 16F, 0)));
            ps.popPose();
        }
        if (girando && m instanceof ArmedModel am) aroGirando(ps, buf, luz, e, am, pt);
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

    // ------------------------------------------------------------------ lazo
    private static final Vector3f X = new Vector3f(1, 0, 0), Y = new Vector3f(0, 1, 0), Z = new Vector3f(0, 0, 1);
    /** Grosor de la cuerda sobre el cuerpo (bloques). */
    static final float CUERDA = 0.045F;

    /** Punto (en el espacio de la capa) de una parte del modelo, dado en el espacio de esa parte. */
    private static Vector3f punto(ModelPart parte, float x, float y, float z) {
        PoseStack tmp = new PoseStack();
        parte.translateAndRotate(tmp);
        return tmp.last().pose().transformPosition(new Vector3f(x, y, z));
    }

    /** Aro de cuerda alrededor de una extremidad (perpendicular a ella) a 'y' píxeles de su pivote. */
    private static void aroParte(PoseStack ps, VertexConsumer vc, ModelPart parte, float xPx, float yPx, float radioPx, int luz) {
        ps.pushPose();
        parte.translateAndRotate(ps);
        Cuerdas.anillo(ps, vc, new Vector3f(xPx / 16F, yPx / 16F, 0), X, Z, radioPx / 16F, radioPx / 16F, luz, CUERDA * 0.9F, 0.4F);
        Cuerdas.anillo(ps, vc, new Vector3f(xPx / 16F, (yPx - 0.9F) / 16F, 0), X, Z, radioPx / 16F, radioPx / 16F, luz, CUERDA * 0.9F, 1.9F);
        ps.popPose();
    }

    private void lazo(PoseStack ps, MultiBufferSource buf, int luz, T e, M m, TorturaCliente.Estado est) {
        VertexConsumer vc = buf.getBuffer(Cuerdas.TIPO);
        Vector3f abajo = new Vector3f(0, 1, 0);   // en el espacio del modelo, "abajo" es +y
        if (m instanceof HumanoidModel<?> hm) {
            if (est.lazo != 3) {
                // el lazo cerrado sobre el pecho, apretando los brazos
                ps.pushPose();
                hm.body.translateAndRotate(ps);
                Vector3f c = new Vector3f(0, 5.2F / 16F, 0);
                Cuerdas.anillo(ps, vc, c, X, Z, 8.7F / 16F, 3.0F / 16F, luz, CUERDA * 1.1F, 0F);
                if (est.lazo == 1) TorturaCliente.capturarTorso(e.getId(), ps.last().pose().transformPosition(new Vector3f(0, 5.2F / 16F, -3.0F / 16F)));
                ps.popPose();
                return;
            }
            // maniatado: aros en muñecas y tobillos, muñecas juntas, tobillos juntos y una cuerda de las muñecas a los tobillos
            boolean fino = e instanceof AbstractClientPlayer acp && "slim".equals(acp.getModelName());
            float dx = fino ? 0.5F : 1.0F;
            aroParte(ps, vc, hm.rightArm, -dx, MUNECA_Y, fino ? 1.9F : 2.4F, luz);
            aroParte(ps, vc, hm.leftArm, dx, MUNECA_Y, fino ? 1.9F : 2.4F, luz);
            aroParte(ps, vc, hm.rightLeg, 0, 10.2F, 2.4F, luz);
            aroParte(ps, vc, hm.leftLeg, 0, 10.2F, 2.4F, luz);
            Vector3f md = punto(hm.rightArm, -dx / 16F, MUNECA_Y / 16F, 0), mi = punto(hm.leftArm, dx / 16F, MUNECA_Y / 16F, 0);
            Vector3f td = punto(hm.rightLeg, 0, 10.2F / 16F, 0), ti = punto(hm.leftLeg, 0, 10.2F / 16F, 0);
            Cuerdas.dibujar(ps, vc, md, mi, abajo, 0.015F, luz, CUERDA, 0F);
            Cuerdas.dibujar(ps, vc, td, ti, abajo, 0.015F, luz, CUERDA, 0F);
            Vector3f mm = new Vector3f(md).add(mi).mul(0.5F), tm = new Vector3f(td).add(ti).mul(0.5F);
            Cuerdas.dibujar(ps, vc, mm, tm, abajo, 0.03F, luz, CUERDA * 1.1F, 0F);
            // el cabo que sobra, colgando del nudo
            Cuerdas.dibujar(ps, vc, tm, new Vector3f(tm).add(0.06F, 0.22F, 0.05F), abajo, 0.04F, luz, CUERDA, 0F);
            return;
        }
        // cualquier otro mob: aros alrededor del cuerpo (aproximados con su caja; el modelo va con y hacia abajo desde 1,501)
        float w = e.getBbWidth(), h = e.getBbHeight();
        if (est.lazo != 3) {
            Vector3f c = new Vector3f(0, 1.501F - h * 0.62F, 0);
            Cuerdas.anillo(ps, vc, c, X, Z, w * 0.58F, w * 0.62F, luz, CUERDA * 1.2F, 0F);
            if (est.lazo == 1) TorturaCliente.capturarTorso(e.getId(), ps.last().pose().transformPosition(new Vector3f(c)));
            return;
        }
        float cy = 1.501F - h * 0.5F;
        Vector3f a1 = new Vector3f(0, cy, -w * 0.28F), a2 = new Vector3f(0, cy, w * 0.28F);
        Cuerdas.anillo(ps, vc, a1, X, Y, w * 0.56F, h * 0.42F, luz, CUERDA * 1.2F, 0F);
        Cuerdas.anillo(ps, vc, a2, X, Y, w * 0.56F, h * 0.42F, luz, CUERDA * 1.2F, 1.3F);
        Vector3f b1 = new Vector3f(a1).add(0, h * 0.42F, 0), b2 = new Vector3f(a2).add(0, h * 0.42F, 0);
        Cuerdas.dibujar(ps, vc, b1, b2, abajo, 0.02F, luz, CUERDA * 1.2F, 0F);
    }

    /** Tercera persona: el aro girando por encima de la mano y la cuerda hasta su nudo. */
    private void aroGirando(PoseStack ps, MultiBufferSource buf, int luz, T e, ArmedModel am, float pt) {
        HumanoidArm brazo = LazoCliente.brazoDelLazo(e);
        PoseStack tmp = new PoseStack();
        am.translateToHand(brazo, tmp);
        Vector3f mano = tmp.last().pose().transformPosition(new Vector3f((brazo == HumanoidArm.RIGHT ? -1F : 1F) / 16F, 10F / 16F, 0));
        float carga = LazoCliente.carga(e, pt), a = LazoCliente.anguloGiro(e, pt);
        float r = 0.42F + 0.22F * carga;
        // aro casi horizontal por encima (arriba = -y), con un bamboleo; gira alrededor de la mano
        Vector3f c = new Vector3f(mano).add(Mth.cos(a) * 0.07F, -0.36F, Mth.sin(a) * 0.07F);
        Vector3f u = new Vector3f(1, Mth.sin(a * 0.5F) * 0.14F, 0).normalize();
        Vector3f w = new Vector3f(0, Mth.cos(a * 0.5F) * 0.14F, 1).normalize();
        VertexConsumer vc = buf.getBuffer(Cuerdas.TIPO);
        Cuerdas.anillo(ps, vc, c, u, w, r, r, luz, CUERDA, a);
        Vector3f honda = new Vector3f(c).add(new Vector3f(u).mul(Mth.cos(a) * r)).add(new Vector3f(w).mul(Mth.sin(a) * r));
        Cuerdas.dibujar(ps, vc, mano, honda, new Vector3f(0, 1, 0), 0.02F, luz, CUERDA * 0.9F, a * 0.3F);
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
