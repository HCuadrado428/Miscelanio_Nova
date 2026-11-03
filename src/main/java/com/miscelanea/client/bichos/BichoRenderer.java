package com.miscelanea.client.bichos;

import com.miscelanea.Miscelanea;
import com.miscelanea.bichos.BichoEntity;
import com.miscelanea.bichos.Especie;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Bichos en 3D (GeckoLib): cada especie tiene su modelo, textura y animaciones (bicho_&lt;especie&gt;.*), generados con
 * gen_bichos3d.py. El modelo mide 16 unidades = tam bloques y tiene la cabeza hacia -z; GeckoLib lo gira 180-yaw, así
 * que la cabeza apunta hacia donde mira el bicho (vector (-sin yaw, cos yaw)). Las alas transparentes usan una capa
 * translúcida con culling (sin caras dobles). La luciérnaga lleva el farol del abdomen brillando y un halo.
 */
public class BichoRenderer extends GeoEntityRenderer<BichoEntity> {
    private static final ResourceLocation HALO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/bicho_halo.png");
    private static final ResourceLocation GLOWMASK = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/bicho_luciernaga_glowmask.png");

    public BichoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Modelo());
        this.shadowRadius = 0.1F;
        addRenderLayer(new Farol(this));
    }

    /** Recursos por especie (solo bichos; los peces no son entidades). */
    static class Modelo extends GeoModel<BichoEntity> {
        private static final ResourceLocation[] GEO = new ResourceLocation[Especie.values().length];
        private static final ResourceLocation[] TEX = new ResourceLocation[Especie.values().length];
        private static final ResourceLocation[] ANIM = new ResourceLocation[Especie.values().length];

        static {
            for (Especie e : Especie.values()) {
                String n = "bicho_" + e.id();
                GEO[e.ordinal()] = new ResourceLocation(Miscelanea.MOD_ID, "geo/entity/" + n + ".geo.json");
                TEX[e.ordinal()] = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/" + n + ".png");
                ANIM[e.ordinal()] = new ResourceLocation(Miscelanea.MOD_ID, "animations/entity/" + n + ".animation.json");
            }
        }

        private static int i(BichoEntity e) {
            Especie sp = e.especie();
            return sp.esPez() ? 0 : sp.ordinal();
        }

        @Override
        public ResourceLocation getModelResource(BichoEntity e) {
            return GEO[i(e)];
        }

        @Override
        public ResourceLocation getTextureResource(BichoEntity e) {
            return TEX[i(e)];
        }

        @Override
        public ResourceLocation getAnimationResource(BichoEntity e) {
            return ANIM[i(e)];
        }

        @Override
        public RenderType getRenderType(BichoEntity e, ResourceLocation tex) {
            // translúcido con culling: alas de membrana medio transparentes y cada cara de las alas solo por su lado
            return RenderType.entityTranslucentCull(tex);
        }
    }

    /** El farol de la luciérnaga: el abdomen brilla (sin luz del mundo) y palpita. */
    static class Farol extends GeoRenderLayer<BichoEntity> {
        Farol(GeoRenderer<BichoEntity> r) {
            super(r);
        }

        @Override
        public void render(PoseStack ps, BichoEntity e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc,
                           float partial, int luz, int overlay) {
            if (e.especie() != Especie.LUCIERNAGA) return;
            float b = brillo(e, partial);
            RenderType glow = RenderType.eyes(GLOWMASK);
            getRenderer().reRender(model, ps, buf, e, glow, buf.getBuffer(glow), partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, b, b, b, 1F);
        }
    }

    static float brillo(BichoEntity e, float partial) {
        float t = e.tickCount + partial;
        return 0.55F + 0.45F * Mth.sin(t * 0.25F + e.getId());
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack ps, BichoEntity e, BakedGeoModel model,
                                    boolean isReRender, float partial, int luz, int overlay) {
        float tam = e.especie().tam;
        super.scaleModelForRender(widthScale * tam, heightScale * tam, ps, e, model, isReRender, partial, luz, overlay);
    }

    @Override
    protected void applyRotations(BichoEntity e, PoseStack ps, float ageInTicks, float rotationYaw, float partial) {
        // el yaw de verdad (no el del cuerpo, que en los Mob va con retraso): la cabeza hacia donde va
        super.applyRotations(e, ps, ageInTicks, Mth.rotLerp(partial, e.yRotO, e.getYRot()), partial);
        if (e.especie().vuela() && !e.expuesto) {
            float t = e.tickCount + partial;
            ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.2F) * 6));   // cabeceo suave en vuelo
        }
    }

    @Override
    public void render(BichoEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        Especie sp = e.especie();
        ps.pushPose();
        if (sp.vuela() && !e.expuesto) ps.translate(0, 0.15, 0);
        super.render(e, yaw, partial, ps, buf, luz);
        if (sp == Especie.LUCIERNAGA) halo(e, partial, ps, buf, sp.tam);
        ps.popPose();
    }

    /** Halo de luz alrededor de la luciérnaga, siempre de cara a la cámara. */
    private void halo(BichoEntity e, float partial, PoseStack ps, MultiBufferSource buf, float tam) {
        float a = brillo(e, partial) * 0.55F;
        float r = tam * 0.9F;
        ps.pushPose();
        ps.translate(0, tam * 0.12F, 0);
        // el farol va detrás: lo desplazamos hacia la cola según el yaw
        float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
        ps.translate(Mth.sin(yaw) * tam * 0.25F, 0, -Mth.cos(yaw) * tam * 0.25F);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        vert(vc, m, n, -r, -r, 0, 1, a);
        vert(vc, m, n, r, -r, 1, 1, a);
        vert(vc, m, n, r, r, 1, 0, a);
        vert(vc, m, n, -r, r, 0, 0, a);
        ps.popPose();
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, float a) {
        vc.vertex(m, x, y, 0).color(1F, 1F, 1F, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0, 1, 0).endVertex();
    }
}
