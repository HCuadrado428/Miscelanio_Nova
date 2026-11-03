package com.miscelanea.client.tortura.saw;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Capa para TODAS las entidades vivas: la caja de agua en la cabeza. El marco (modelo de bloque, gen_tortura_saw.py) y,
 * dibujados aquí, el agua (que sube y se enrojece con la sangre, con la superficie meciéndose) y el cristal sucio.
 * Mismo espacio que la trampa de oso de TorturaLayer: la cabeza ocupa 4..12 en los tres ejes.
 */
public class CajaAguaLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation MARCO = new ResourceLocation(Miscelanea.MOD_ID, "block/saw/caja_agua_puesta");
    private static final ResourceLocation AGUA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/saw/caja_agua_agua.png");
    private static final RenderType TIPO_AGUA = RenderType.entityTranslucent(AGUA);
    private static final RenderType TIPO_CRISTAL = RenderType.entityTranslucent(SawCliente.CRISTAL);
    /** Cristal (px): gen_tortura_saw.py C_CRISTAL. El agua va un pelín por dentro. */
    private static final float X0 = 1.5F, Y0 = 4.2F, Z0 = 1.5F, X1 = 14.5F, Y1 = 15.6F, Z1 = 14.5F;

    public CajaAguaLayer(RenderLayerParent<T, M> padre) {
        super(padre);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, T e, float limb, float limbAmt, float pt, float edad, float yawCabeza, float pitchCabeza) {
        SawCliente.Caja c = SawCliente.caja(e.getId());
        if (c == null || e.isInvisible()) return;
        M m = getParentModel();
        float t = e.tickCount + pt;
        ps.pushPose();
        if (m instanceof HeadedModel hm) hm.getHead().translateAndRotate(ps);
        else ps.translate(0, 1.501F - e.getBbHeight() + 0.5F, 0);
        ps.translate(0, -0.25F, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(180));
        // al abrirse: la caja sube y se ladea (se la quitan)
        if (c.abierta > 0) {
            float k = Mth.clamp((c.abierta + pt) / 30F, 0F, 1F);
            ps.translate(0, k * k * 0.5F, 0);
            ps.mulPose(Axis.XP.rotationDegrees(k * 18F));
        }
        ps.translate(-0.5F, -0.5F, -0.5F);
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(MARCO);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        // agua
        float n = c.abierta > 0 ? 0F : c.nivel(pt);
        if (n > 0.005F) {
            float[] col = SawCliente.color(c.sangre);
            float alto = Y0 + 0.05F + n * (Y1 - Y0 - 0.15F);
            float ola = c.activa ? Mth.sin(t * 0.35F) * 0.12F : 0F;
            caja(ps, buf.getBuffer(TIPO_AGUA), (X0 + 0.06F) / 16F, (Y0 + 0.05F) / 16F, (Z0 + 0.06F) / 16F,
                    (X1 - 0.06F) / 16F, (alto + ola) / 16F, (Z1 - 0.06F) / 16F, col[0], col[1], col[2], 0.78F, luz, true);
        }
        // cristal
        caja(ps, buf.getBuffer(TIPO_CRISTAL), X0 / 16F, Y0 / 16F, Z0 / 16F, X1 / 16F, Y1 / 16F, Z1 / 16F, 1F, 1F, 1F, 1F, luz, false);
        ps.popPose();
    }

    /** Las 6 caras de una caja (los dos lados se ven: RenderType translúcido sin culling). agua: la V de los lados va de arriba abajo. */
    private static void caja(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, float g, float b, float a, int luz, boolean agua) {
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        float vb = agua ? Math.min(1F, (y1 - y0) * 1.4F) : 1F;
        // arriba y abajo
        quad(vc, m, nm, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, 0.1F, 0.1F, 0.9F, 0.9F, r, g, b, a, luz);
        quad(vc, m, nm, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, 0F, 0F, 1F, 1F, r, g, b, a, luz);
        // lados (u a lo ancho, v de arriba abajo)
        quad(vc, m, nm, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, 0F, 0F, 1F, vb, r, g, b, a, luz);
        quad(vc, m, nm, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1, 0F, 0F, 1F, vb, r, g, b, a, luz);
        quad(vc, m, nm, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0, 0F, 0F, 1F, vb, r, g, b, a, luz);
        quad(vc, m, nm, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, 0F, 0F, 1F, vb, r, g, b, a, luz);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
                             float u0, float v0, float u1, float v1, float r, float g, float b, float a, int luz) {
        v(vc, m, nm, ax, ay, az, u0, v0, nx, ny, nz, r, g, b, a, luz);
        v(vc, m, nm, bx, by, bz, u1, v0, nx, ny, nz, r, g, b, a, luz);
        v(vc, m, nm, cx, cy, cz, u1, v1, nx, ny, nz, r, g, b, a, luz);
        v(vc, m, nm, dx, dy, dz, u0, v1, nx, ny, nz, r, g, b, a, luz);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, float u, float vv, float nx, float ny, float nz,
                          float r, float g, float b, float a, int luz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nm, nx, ny, nz).endVertex();
    }
}
