package com.miscelanea.client.tortura.castigo;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.Cadenas;
import com.miscelanea.tortura.castigo.JaulaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * La jaula colgante: la cadena desde la argolla del techo y la jaula (cuerpo, techo abombado con su argolla y la puerta,
 * que se abre sobre su bisagra) girada con el péndulo. Caída: recta; rota: tumbada de lado y la puerta arrancada al lado.
 * Modelos de bloque de scratchpad/gen_tortura3a.py (la jaula mira al norte en el modelo; la puerta está en la cara norte).
 */
public class JaulaRenderer extends EntityRenderer<JaulaEntity> {
    public static final ResourceLocation CUERPO = rl("jaula_cuerpo"), TECHO = rl("jaula_techo"), PUERTA = rl("jaula_puerta");
    /** Bisagra de la puerta (píxeles del modelo) y altura del techo. */
    static final float BISAGRA_X = -0.15F, BISAGRA_Z = -1.65F, TECHO_Y = 2.0F;

    private static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/" + n);
    }

    public JaulaRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(JaulaEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(JaulaEntity e, Frustum f, double x, double y, double z) {
        BlockPos a = e.ancla();
        AABB caja = e.getBoundingBox().inflate(1.5);
        if (a != null && e.estado() == JaulaEntity.COLGANDO) caja = caja.minmax(new AABB(a).inflate(1));
        return e.shouldRender(x, y, z) && f.isVisible(caja);
    }

    @Override
    public void render(JaulaEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        BlockPos a = e.ancla();
        Vec3 pos = e.getPosition(pt);
        float abierta = Mth.lerp(pt, e.puertaO, e.puertaA);
        if (e.estado() == JaulaEntity.COLGANDO && a != null) {
            float ax = Mth.lerp(pt, e.axO, e.axC), az = Mth.lerp(pt, e.azO, e.azC), L = Mth.lerp(pt, e.largoO, e.largoC);
            Vec3 p = JaulaEntity.punto(a);
            ps.pushPose();
            ps.translate(p.x - pos.x, p.y - pos.y, p.z - pos.z);
            ps.mulPose(CastigoCliente.giroJaula(ax, az));
            int luzCad = LevelRenderer.getLightColor(e.level(), BlockPos.containing(p.x, p.y - L * 0.5, p.z));
            Cadenas.dibujar(ps, buf.getBuffer(Cadenas.TIPO), new Vector3f(0, 0, 0), new Vector3f(0, -L - 0.05F, 0), new Vector3f(1, 0, 0), 0F, luzCad, 0.1F);
            ps.translate(0, -(L + JaulaEntity.ALTO), 0);
            jaula(ps, buf, luz, e, abierta, false);
            ps.popPose();
        } else if (e.estado() == JaulaEntity.CAYENDO) {
            ps.pushPose();
            jaula(ps, buf, luz, e, abierta, false);
            ps.popPose();
        } else {
            // rota: tumbada de espaldas (la puerta, arrancada, al lado)
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180F - e.getYRot()));
            ps.translate(0, 0.66, 0.2);
            ps.mulPose(Axis.XP.rotationDegrees(-84F));
            ps.translate(0, -1.25, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-(180F - e.getYRot())));
            jaula(ps, buf, luz, e, 0F, true);
            ps.popPose();
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180F - e.getYRot()));
            ps.translate(-0.4, 0.06, -1.25);
            ps.mulPose(Axis.YP.rotationDegrees(23F));
            ps.mulPose(Axis.XP.rotationDegrees(90F));
            ps.translate(-0.5, -0.1, 0.12);
            modelo(ps, buf, PUERTA, luz);
            ps.popPose();
        }
        super.render(e, yaw, pt, ps, buf, luz);
    }

    /** La jaula con el suelo en el origen del PoseStack. sinPuerta: rota. */
    private static void jaula(PoseStack ps, MultiBufferSource buf, int luz, JaulaEntity e, float abierta, boolean sinPuerta) {
        ps.mulPose(Axis.YP.rotationDegrees(180F - e.getYRot()));
        ps.translate(-0.5, 0, -0.5);
        modelo(ps, buf, CUERPO, luz);
        ps.pushPose();
        ps.translate(0, TECHO_Y, 0);
        modelo(ps, buf, TECHO, luz);
        ps.popPose();
        if (!sinPuerta) {
            ps.pushPose();
            ps.translate(BISAGRA_X / 16F, 0, BISAGRA_Z / 16F);
            ps.mulPose(Axis.YP.rotationDegrees(abierta * 105F));
            ps.translate(-BISAGRA_X / 16F, 0, -BISAGRA_Z / 16F);
            modelo(ps, buf, PUERTA, luz);
            ps.popPose();
        }
    }

    private static void modelo(PoseStack ps, MultiBufferSource buf, ResourceLocation rl, int luz) {
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(rl);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }
}
