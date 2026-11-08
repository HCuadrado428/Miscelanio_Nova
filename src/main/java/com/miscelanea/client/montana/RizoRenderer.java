package com.miscelanea.client.montana;

import com.miscelanea.Miscelanea;
import com.miscelanea.montana.RizoBlock;
import com.miscelanea.montana.Via;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * La vuelta del rizo: dos raíles de tubo y unas traviesas a lo largo de la curva. Son ~600 caras fijas por rizo y no
 * hay nada animado; se calcula una sola vez por bloque y se reutiliza.
 */
public class RizoRenderer implements BlockEntityRenderer<RizoBlock.RizoBlockEntity> {
    private static final ResourceLocation RIEL = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/montana_riel.png");
    private static final ResourceLocation TRAVIESA = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/montana_traviesa.png");
    private static final int N = 48;
    private static final double GROSOR = 1 / 16.0, ANCHO = 5.5 / 16, ALTO_RIEL = 0;

    public RizoRenderer(BlockEntityRendererProvider.Context c) {}

    /** Puntos, derecha y arriba de la vuelta (relativos al bloque), guardados en el propio bloque-entidad. */
    static Vec3[][] muestras(RizoBlock.RizoBlockEntity be) {
        if (be.cache instanceof Vec3[][] m) return m;
        Via.Pieza p = Via.en(be.getLevel(), be.getBlockPos());
        if (p == null) return null;
        Vec3 o = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3[][] m = new Vec3[N + 1][];
        for (int i = 0; i <= N; i++) {
            Via.Sitio s = new Via.Sitio(p, i / (double) N);
            Vec3[] b = TrenMontanaRenderer.base(s);
            m[i] = new Vec3[]{s.punto().subtract(o), b[2], b[1]};
        }
        be.cache = m;
        return m;
    }

    @Override
    public void render(RizoBlock.RizoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Vec3[][] m = muestras(be);
        if (m == null) return;
        Matrix4f pose = ps.last().pose();
        Matrix3f nor = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(RIEL));
        for (int lado = -1; lado <= 1; lado += 2) {
            for (int i = 0; i < N; i++) {
                Vec3 a = m[i][0].add(m[i][1].scale(lado * ANCHO)).add(m[i][2].scale(ALTO_RIEL));
                Vec3 b = m[i + 1][0].add(m[i + 1][1].scale(lado * ANCHO)).add(m[i + 1][2].scale(ALTO_RIEL));
                tubo(vc, pose, nor, a, m[i][1], m[i][2], b, m[i + 1][1], m[i + 1][2], luz, i);
            }
        }
        VertexConsumer vt = buf.getBuffer(RenderType.entityCutoutNoCull(TRAVIESA));
        for (int i = 1; i < N; i += 3) {
            Vec3 c = m[i][0].subtract(m[i][2].scale(GROSOR * 1.5));
            Vec3 d = m[i][1], u = m[i][2];
            Vec3 tg = d.cross(u).scale(-1).normalize().scale(1 / 16.0);
            Vec3 a = c.subtract(d.scale(ANCHO + GROSOR)), b = c.add(d.scale(ANCHO + GROSOR));
            // tabla plana: dos caras (arriba y abajo) bastan
            cara(vt, pose, nor, a.subtract(tg), b.subtract(tg), b.add(tg), a.add(tg), u, luz);
        }
    }

    /** Un tramo de tubo cuadrado entre dos secciones. */
    private static void tubo(VertexConsumer vc, Matrix4f pose, Matrix3f nor, Vec3 a, Vec3 da, Vec3 ua, Vec3 b, Vec3 db, Vec3 ub, int luz, int i) {
        double g = GROSOR;
        Vec3[] ra = {da.scale(g).add(ua.scale(g)), da.scale(-g).add(ua.scale(g)), da.scale(-g).add(ua.scale(-g)), da.scale(g).add(ua.scale(-g))};
        Vec3[] rb = {db.scale(g).add(ub.scale(g)), db.scale(-g).add(ub.scale(g)), db.scale(-g).add(ub.scale(-g)), db.scale(g).add(ub.scale(-g))};
        Vec3[] n = {ua, da.scale(-1), ua.scale(-1), da};
        float v0 = (i % 4) / 4F, v1 = v0 + 0.25F;
        for (int k = 0; k < 4; k++) {
            int k2 = (k + 1) % 4;
            Vec3 nn = n[k];
            float u0 = 0.25F * k, u1 = u0 + 0.25F;
            v(vc, pose, nor, a.add(ra[k]), u0, v0, nn, luz);
            v(vc, pose, nor, a.add(ra[k2]), u1, v0, nn, luz);
            v(vc, pose, nor, b.add(rb[k2]), u1, v1, nn, luz);
            v(vc, pose, nor, b.add(rb[k]), u0, v1, nn, luz);
        }
    }

    private static void cara(VertexConsumer vc, Matrix4f pose, Matrix3f nor, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, int luz) {
        v(vc, pose, nor, a, 0, 0, n, luz);
        v(vc, pose, nor, b, 1, 0, n, luz);
        v(vc, pose, nor, c, 1, 0.125F, n, luz);
        v(vc, pose, nor, d, 0, 0.125F, n, luz);
    }

    private static void v(VertexConsumer vc, Matrix4f pose, Matrix3f nor, Vec3 p, float u, float v, Vec3 n, int luz) {
        vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nor, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(RizoBlock.RizoBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
