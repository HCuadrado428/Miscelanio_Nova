package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Dibujo de luz (aditiva, sin luz del mundo): haces cónicos y manchas redondas. Lo usan el foco y la bola de discoteca. */
final class Luces {
    private Luces() {}

    static final ResourceLocation MANCHA = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/mancha_luz.png");
    static final ResourceLocation HAZ = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/haz_luz.png");
    private static final int LUZ = 0xF000F0;

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float u, float v, float r, float g, float b) {
        vc.vertex(m, p.x, p.y, p.z).color(r, g, b, 1F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LUZ).normal(n, 0, 1, 0).endVertex();
    }

    /** Haz cónico desde {@code o} en la dirección {@code d} (unitaria): radios r0 → r1 y largo; se apaga hacia la punta. */
    static void haz(PoseStack ps, MultiBufferSource buf, Vector3f o, Vector3f d, float largo, float r0, float r1, int rgb, float fuerza) {
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(HAZ));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        Vector3f a = new Vector3f(Math.abs(d.y) < 0.9F ? 0 : 1, Math.abs(d.y) < 0.9F ? 1 : 0, 0).cross(d).normalize();
        Vector3f b = new Vector3f(d).cross(a).normalize();
        float cr = ((rgb >> 16) & 255) / 255F * fuerza, cg = ((rgb >> 8) & 255) / 255F * fuerza, cb = (rgb & 255) / 255F * fuerza;
        int lados = 10;
        Vector3f fin = new Vector3f(d).mul(largo).add(o);
        for (int i = 0; i < lados; i++) {
            double t0 = Math.PI * 2 * i / lados, t1 = Math.PI * 2 * (i + 1) / lados;
            Vector3f e0 = new Vector3f(a).mul((float) Math.cos(t0)).add(new Vector3f(b).mul((float) Math.sin(t0)));
            Vector3f e1 = new Vector3f(a).mul((float) Math.cos(t1)).add(new Vector3f(b).mul((float) Math.sin(t1)));
            Vector3f p0 = new Vector3f(e0).mul(r0).add(o), p1 = new Vector3f(e1).mul(r0).add(o);
            Vector3f q0 = new Vector3f(e0).mul(r1).add(fin), q1 = new Vector3f(e1).mul(r1).add(fin);
            // por los dos lados (se ve desde dentro y desde fuera)
            v(vc, m, n, p0, 0, 0, cr, cg, cb);
            v(vc, m, n, p1, 1, 0, cr, cg, cb);
            v(vc, m, n, q1, 1, 1, 0, 0, 0);
            v(vc, m, n, q0, 0, 1, 0, 0, 0);
            v(vc, m, n, q0, 0, 1, 0, 0, 0);
            v(vc, m, n, q1, 1, 1, 0, 0, 0);
            v(vc, m, n, p1, 1, 0, cr, cg, cb);
            v(vc, m, n, p0, 0, 0, cr, cg, cb);
        }
    }

    /** Mancha de luz redonda en {@code c}, en el plano perpendicular a {@code nrm}. */
    static void mancha(PoseStack ps, MultiBufferSource buf, Vector3f c, Vector3f nrm, float radio, int rgb, float fuerza) {
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(MANCHA));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        Vector3f a = new Vector3f(Math.abs(nrm.y) < 0.9F ? 0 : 1, Math.abs(nrm.y) < 0.9F ? 1 : 0, 0).cross(nrm).normalize().mul(radio);
        Vector3f b = new Vector3f(nrm).cross(a).normalize().mul(radio);
        Vector3f o = new Vector3f(nrm).mul(0.02F).add(c);
        float cr = ((rgb >> 16) & 255) / 255F * fuerza, cg = ((rgb >> 8) & 255) / 255F * fuerza, cb = (rgb & 255) / 255F * fuerza;
        Vector3f p0 = new Vector3f(o).sub(a).sub(b), p1 = new Vector3f(o).add(a).sub(b), p2 = new Vector3f(o).add(a).add(b), p3 = new Vector3f(o).sub(a).add(b);
        v(vc, m, n, p0, 0, 0, cr, cg, cb);
        v(vc, m, n, p1, 1, 0, cr, cg, cb);
        v(vc, m, n, p2, 1, 1, cr, cg, cb);
        v(vc, m, n, p3, 0, 1, cr, cg, cb);
        v(vc, m, n, p3, 0, 1, cr, cg, cb);
        v(vc, m, n, p2, 1, 1, cr, cg, cb);
        v(vc, m, n, p1, 1, 0, cr, cg, cb);
        v(vc, m, n, p0, 0, 0, cr, cg, cb);
    }
}
