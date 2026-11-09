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
 * La vuelta del rizo: dos raíles de tubo y unas traviesas a lo largo de la curva. Son ~400 caras fijas por rizo y no hay
 * nada animado: la malla (posiciones, UV y normales) se calcula una sola vez por bloque, se guarda en el propio
 * bloque-entidad y en cada fotograma solo se copian los vértices, sin crear objetos.
 */
public class RizoRenderer implements BlockEntityRenderer<RizoBlock.RizoBlockEntity> {
    private static final ResourceLocation RIEL = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/montana_riel.png");
    private static final ResourceLocation TRAVIESA = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/montana_traviesa.png");
    private static final int N = 48;
    /** Medio grosor del tubo y media separación entre raíles (igual que en los modelos de las vías). */
    private static final double GROSOR = 1 / 16.0, ANCHO = 5.5 / 16;
    /** Bandas de la textura del raíl (en fracción de la textura): lado, arriba y abajo. */
    private static final float V_LADO = 0, V_ARRIBA = 2 / 16F, V_ABAJO = 4 / 16F, BANDA = 2 / 16F;
    /** Floats por vértice: x, y, z, u, v, nx, ny, nz. */
    private static final int F = 8;

    public RizoRenderer(BlockEntityRendererProvider.Context c) {}

    /** Las dos mallas del rizo (raíles y traviesas), relativas al bloque. */
    record Malla(float[] rieles, float[] traviesas) {}

    static Malla malla(RizoBlock.RizoBlockEntity be) {
        if (be.cache instanceof Malla m) return m;
        Via.Pieza p = Via.en(be.getLevel(), be.getBlockPos());
        if (p == null) return null;
        Vec3 o = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3[] pt = new Vec3[N + 1], der = new Vec3[N + 1], up = new Vec3[N + 1];
        for (int i = 0; i <= N; i++) {
            Via.Sitio s = new Via.Sitio(p, i / (double) N);
            Vec3[] b = TrenMontanaRenderer.base(s);
            pt[i] = s.punto().subtract(o);
            der[i] = b[2];
            up[i] = b[1];
        }
        // raíles: 2 lados x N tramos x 4 caras x 4 vértices
        float[] r = new float[2 * N * 4 * 4 * F];
        int n = 0;
        for (int lado = -1; lado <= 1; lado += 2)
            for (int i = 0; i < N; i++) {
                Vec3 a = pt[i].add(der[i].scale(lado * ANCHO)), b = pt[i + 1].add(der[i + 1].scale(lado * ANCHO));
                n = tubo(r, n, a, der[i], up[i], b, der[i + 1], up[i + 1], i);
            }
        // traviesas: una tabla plana cada 3 muestras (cara de arriba y de abajo)
        int nt = 0;
        for (int i = 1; i < N; i += 3) nt++;
        float[] t = new float[nt * 2 * 4 * F];
        int m = 0;
        for (int i = 1; i < N; i += 3) {
            Vec3 c = pt[i].subtract(up[i].scale(GROSOR * 1.5));
            Vec3 d = der[i], u = up[i];
            Vec3 tg = d.cross(u).scale(-1).normalize().scale(1 / 16.0);
            Vec3 a = c.subtract(d.scale(ANCHO + GROSOR * 2)), b = c.add(d.scale(ANCHO + GROSOR * 2));
            m = cara(t, m, a.subtract(tg), b.subtract(tg), b.add(tg), a.add(tg), u);
            m = cara(t, m, a.add(tg), b.add(tg), b.subtract(tg), a.subtract(tg), u.scale(-1));
        }
        Malla res = new Malla(r, t);
        be.cache = res;
        return res;
    }

    @Override
    public void render(RizoBlock.RizoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Malla m = malla(be);
        if (m == null) return;
        Matrix4f pose = ps.last().pose();
        Matrix3f nor = ps.last().normal();
        volcar(buf.getBuffer(RenderType.entityCutoutNoCull(RIEL)), pose, nor, m.rieles(), luz);
        volcar(buf.getBuffer(RenderType.entityCutoutNoCull(TRAVIESA)), pose, nor, m.traviesas(), luz);
    }

    private static void volcar(VertexConsumer vc, Matrix4f pose, Matrix3f nor, float[] d, int luz) {
        for (int i = 0; i < d.length; i += F)
            vc.vertex(pose, d[i], d[i + 1], d[i + 2]).color(255, 255, 255, 255).uv(d[i + 3], d[i + 4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nor, d[i + 5], d[i + 6], d[i + 7]).endVertex();
    }

    /** Un tramo de tubo cuadrado entre dos secciones: arriba y abajo con su banda de la textura, y los lados. */
    private static int tubo(float[] o, int n, Vec3 a, Vec3 da, Vec3 ua, Vec3 b, Vec3 db, Vec3 ub, int i) {
        double g = GROSOR;
        Vec3[] ra = {da.scale(g).add(ua.scale(g)), da.scale(-g).add(ua.scale(g)), da.scale(-g).add(ua.scale(-g)), da.scale(g).add(ua.scale(-g))};
        Vec3[] rb = {db.scale(g).add(ub.scale(g)), db.scale(-g).add(ub.scale(g)), db.scale(-g).add(ub.scale(-g)), db.scale(g).add(ub.scale(-g))};
        Vec3[] nn = {ua, da.scale(-1), ua.scale(-1), da};
        float[] banda = {V_ARRIBA, V_LADO, V_ABAJO, V_LADO};
        float u0 = (i % 4) / 4F, u1 = u0 + 0.25F;
        for (int k = 0; k < 4; k++) {
            int k2 = (k + 1) % 4;
            float v0 = banda[k], v1 = v0 + BANDA;
            n = v(o, n, a.add(ra[k]), u0, v0, nn[k]);
            n = v(o, n, a.add(ra[k2]), u0, v1, nn[k]);
            n = v(o, n, b.add(rb[k2]), u1, v1, nn[k]);
            n = v(o, n, b.add(rb[k]), u1, v0, nn[k]);
        }
        return n;
    }

    private static int cara(float[] o, int n, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 nn) {
        n = v(o, n, a, 0, 0, nn);
        n = v(o, n, b, 1, 0, nn);
        n = v(o, n, c, 1, 0.125F, nn);
        return v(o, n, d, 0, 0.125F, nn);
    }

    private static int v(float[] o, int n, Vec3 p, float u, float v, Vec3 nn) {
        o[n] = (float) p.x;
        o[n + 1] = (float) p.y;
        o[n + 2] = (float) p.z;
        o[n + 3] = u;
        o[n + 4] = v;
        o[n + 5] = (float) nn.x;
        o[n + 6] = (float) nn.y;
        o[n + 7] = (float) nn.z;
        return n + F;
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
