package com.miscelanea.client.montana;

import com.miscelanea.Miscelanea;
import com.miscelanea.montana.TrenMontana;
import com.miscelanea.montana.Via;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Los vagones del tren. Un vagón es un modelo de 5 cajas (pocos polígonos) con textura a 32 px por bloque; cada uno se
 * coloca en su sitio de la vía y se orienta con la dirección y el "arriba" de la vía (así se inclina en las curvas y da
 * la vuelta en el rizo). Se dibuja entre el tic anterior y el actual, igual que los pasajeros: van siempre dentro.
 */
public class TrenMontanaRenderer extends EntityRenderer<TrenMontana> {
    public static final ResourceLocation TEXTURA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tren_montana.png");
    private final ModelPart vagon;

    public TrenMontanaRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.6F;
        vagon = modelo().bakeRoot();
    }

    /** Medidas en 1/32 de bloque (el modelo se dibuja a media escala); y hacia abajo, como los modelos de Minecraft. */
    static LayerDefinition modelo() {
        MeshDefinition m = new MeshDefinition();
        var r = m.getRoot();
        r.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-14, -6, -16, 28, 4, 32), PartPose.ZERO);
        r.addOrReplaceChild("cuerpo", CubeListBuilder.create().texOffs(0, 40).addBox(-18, -22, -22, 36, 16, 44), PartPose.ZERO);
        r.addOrReplaceChild("respaldo", CubeListBuilder.create().texOffs(0, 104).addBox(-16, -36, -22, 32, 14, 6), PartPose.ZERO);
        r.addOrReplaceChild("morro", CubeListBuilder.create().texOffs(80, 104).addBox(-18, -28, 22, 36, 6, 4), PartPose.ZERO);
        r.addOrReplaceChild("barra", CubeListBuilder.create().texOffs(0, 124).addBox(-16, -30, 4, 32, 2, 2), PartPose.ZERO);
        return LayerDefinition.create(m, 256, 128);
    }

    /** Dirección, arriba y derecha de la vía en un sitio (los tres perpendiculares entre sí). */
    static Vec3[] base(Via.Sitio s) {
        Vec3 tg = s.pieza().tangente(s.t());
        Vec3 up = s.pieza().arriba(s.t());
        up = up.subtract(tg.scale(up.dot(tg)));
        if (up.lengthSqr() < 1e-6) up = new Vec3(0, 1, 0);
        up = up.normalize();
        Vec3 der = tg.cross(up).normalize();
        return new Vec3[]{tg, up, der};
    }

    @Override
    public boolean shouldRender(TrenMontana e, Frustum f, double x, double y, double z) {
        // la caja de la entidad ya abarca todos los vagones
        return e.shouldRender(x, y, z) && f.isVisible(e.getBoundingBox().inflate(0.5));
    }

    @Override
    public void render(TrenMontana e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        Vec3 ojo = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEXTURA));
        for (int k = 0; k < e.vagones(); k++) {
            Via.Sitio s = e.sitioDibujo(k, pt);
            if (s == null) continue;
            Vec3 p = s.punto();
            Vec3[] b = base(s);
            Vec3 tg = b[0], up = b[1], der = b[2];
            ps.pushPose();
            ps.translate(p.x - ojo.x, p.y - ojo.y, p.z - ojo.z);
            // columnas: X = izquierda (para que con la escala (-1,-1,1) quede la derecha), Y = arriba, Z = delante
            Vec3 izq = der.scale(-1);
            Matrix4f m = new Matrix4f(
                    (float) izq.x, (float) izq.y, (float) izq.z, 0,
                    (float) up.x, (float) up.y, (float) up.z, 0,
                    (float) tg.x, (float) tg.y, (float) tg.z, 0,
                    0, 0, 0, 1);
            ps.last().pose().mul(m);
            ps.last().normal().mul(new Matrix3f(m));
            ps.scale(-0.5F, -0.5F, 0.5F);
            int l = LevelRenderer.getLightColor(e.level(), BlockPos.containing(p.add(up.scale(0.5))));
            vagon.render(ps, vc, l, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TrenMontana e) {
        return TEXTURA;
    }
}
