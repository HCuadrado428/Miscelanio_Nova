package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cadena de eslabones: cada eslabón son dos planos cruzados (anillo visto de frente + de canto) y el siguiente va
 * girado 90º, como la cadena de Minecraft. La textura tiene el anillo en la mitad izquierda y el canto en la derecha.
 */
public final class Cadenas {
    private Cadenas() {}

    public static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tortura/cadena.png");
    public static final RenderType TIPO = RenderType.entityCutoutNoCull(TEX);

    /**
     * @param a, b    extremos (en el espacio del PoseStack)
     * @param abajo   hacia dónde cuelga (unitario, en el mismo espacio)
     * @param cuelga  cuánto cuelga el centro
     * @param grosor  ancho del eslabón (unidades del PoseStack); el largo es ~1,9 veces
     */
    public static void dibujar(PoseStack ps, VertexConsumer vc, Vector3f a, Vector3f b, Vector3f abajo, float cuelga, int luz, float grosor) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        Vector3f eje = new Vector3f(b).sub(a);
        float largo = eje.length();
        if (largo < 1.0E-4F) return;
        float paso = grosor * 1.55F;
        int num = Math.max(2, Math.min(160, Math.round(largo / paso)));
        Vector3f prev = punto(a, eje, abajo, cuelga, 0);
        for (int i = 0; i < num; i++) {
            Vector3f sig = punto(a, eje, abajo, cuelga, (i + 1) / (float) num);
            Vector3f d = new Vector3f(sig).sub(prev);
            float dl = d.length();
            if (dl < 1.0E-5F) {
                prev = sig;
                continue;
            }
            d.div(dl);
            Vector3f ref = Math.abs(d.dot(abajo)) > 0.95F ? new Vector3f(1, 0, 0) : abajo;
            Vector3f u = new Vector3f(d).cross(ref).normalize();
            Vector3f w = new Vector3f(d).cross(u).normalize();
            if ((i & 1) == 1) {
                Vector3f t = u;
                u = w;
                w = t;
            }
            // un poco más largo que el paso para que los eslabones se entrelacen
            Vector3f c = new Vector3f(prev).add(sig).mul(0.5F);
            float mitadL = dl * 0.5F * 1.32F, mitadA = grosor * 0.5F;
            quad(vc, m, n, c, d, u, mitadL, mitadA, 0F, 0.5F, w, luz);
            quad(vc, m, n, c, d, w, mitadL, mitadA * 0.55F, 0.5F, 1F, u, luz);
            prev = sig;
        }
    }

    private static Vector3f punto(Vector3f a, Vector3f eje, Vector3f abajo, float cuelga, float s) {
        float caida = cuelga * 4F * s * (1F - s);
        return new Vector3f(eje).mul(s).add(a).add(new Vector3f(abajo).mul(caida));
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f c, Vector3f d, Vector3f lado, float mitadL, float mitadA,
                             float u0, float u1, Vector3f normal, int luz) {
        float[][] esq = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        float[][] uv = {{u0, 1}, {u1, 1}, {u1, 0}, {u0, 0}};
        for (int k = 0; k < 4; k++) {
            float sl = esq[k][1] * mitadL, sa = esq[k][0] * mitadA;
            float x = c.x + d.x * sl + lado.x * sa, y = c.y + d.y * sl + lado.y * sa, z = c.z + d.z * sl + lado.z * sa;
            vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(luz).normal(n, normal.x, normal.y, normal.z).endVertex();
        }
    }
}
