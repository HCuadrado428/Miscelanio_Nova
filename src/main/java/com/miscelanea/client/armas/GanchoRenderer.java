package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.GanchoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Gancho (garfio GeckoLib) y la cadena que lo une a la boca de la escopeta. */
public class GanchoRenderer extends GeoEntityRenderer<GanchoEntity> {
    private static final ResourceLocation CADENA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/cadena.png");

    public GanchoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "gancho")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(GanchoEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.translate(0, 0.125, 0);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial, e.rumboYO, e.rumboY)));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.rumboXO, e.rumboX)));
        ps.translate(0, -0.125, 0);
        super.render(e, 0, partial, ps, buf, luz);
        ps.popPose();
        if (e.getOwner() instanceof Player o) cadena(e, o, partial, ps, buf, luz);
    }

    private static void cadena(GanchoEntity e, Player o, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 ojo = o.getEyePosition(pt);
        Vec3 mira = o.getViewVector(pt);
        Vec3 der = mira.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        Vec3 arriba = der.cross(mira).normalize();
        boolean primera = o == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 boca = primera
                ? ojo.add(mira.scale(0.9)).add(der.scale(0.30)).add(arriba.scale(-0.32))
                : ojo.add(mira.scale(1.0)).add(der.scale(0.35)).add(arriba.scale(-0.55));
        Vec3 aqui = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + 0.125, Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 d = boca.subtract(aqui);
        double largo = d.length();
        if (largo < 0.1) return;
        Vec3 dir = d.scale(1 / largo);
        Vec3 p1 = dir.cross(new Vec3(0, 1, 0));
        p1 = p1.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : p1.normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        int n = Math.max(1, (int) (largo / 0.2));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(CADENA));
        ps.pushPose();
        ps.translate(0, 0.125, 0);
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        double w = 0.07;
        for (int i = 0; i < n; i++) {
            Vec3 a = d.scale(i / (double) n), b = d.scale((i + 1) / (double) n);
            Vec3 q = (i % 2 == 0 ? p1 : p2).scale(w);
            punto(vc, m, nm, a.subtract(q), 0, 0, luz);
            punto(vc, m, nm, a.add(q), 1, 0, luz);
            punto(vc, m, nm, b.add(q), 1, 1, luz);
            punto(vc, m, nm, b.subtract(q), 0, 1, luz);
        }
        ps.popPose();
    }

    private static void punto(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int luz) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }
}
