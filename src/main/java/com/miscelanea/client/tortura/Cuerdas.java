package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cuerda de cáñamo del lazo: cada tramo son dos planos cruzados a lo largo del recorrido (se ve redonda desde cualquier
 * lado), con la textura de hebras retorcidas repitiéndose a lo largo (coordenada V = metros de cuerda / grosor).
 * La textura (scratchpad/gen_tortura2.py) tiene la cuerda de frente en la mitad izquierda y la misma, desplazada media
 * vuelta, en la derecha, para que los dos planos no coincidan.
 */
public final class Cuerdas {
    private Cuerdas() {}

    public static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tortura/cuerda.png");
    public static final RenderType TIPO = RenderType.entityCutoutNoCull(TEX);
    /** Cuántas veces el grosor ocupa una repetición de la textura (la textura es 1:4). */
    private static final float REPETICION = 4F;

    /**
     * Cuerda de a a b colgando 'cuelga' hacia 'abajo' en el centro (parábola).
     * @param fase desplazamiento de la textura (para que "corra" al tirar o al girar)
     */
    public static void dibujar(PoseStack ps, VertexConsumer vc, Vector3f a, Vector3f b, Vector3f abajo, float cuelga, int luz, float grosor, float fase) {
        Vector3f eje = new Vector3f(b).sub(a);
        float largo = eje.length();
        if (largo < 1.0E-4F) return;
        int n = Math.max(2, Math.min(48, Math.round(largo / (grosor * 2.5F)) + 2));
        Vector3f[] pts = new Vector3f[n + 1];
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            float caida = cuelga * 4F * s * (1F - s);
            pts[i] = new Vector3f(eje).mul(s).add(a).add(new Vector3f(abajo).mul(caida));
        }
        tira(ps, vc, pts, abajo, luz, grosor, fase);
    }

    /**
     * Aro cerrado (elipse) de centro c en el plano (u, v) — unitarios y perpendiculares — con radios ru y rv.
     * @param vuelta ángulo (radianes) desde el que empieza: con la textura, hace que el aro "gire"
     */
    public static void anillo(PoseStack ps, VertexConsumer vc, Vector3f c, Vector3f u, Vector3f v, float ru, float rv, int luz, float grosor, float vuelta) {
        int n = Math.max(10, Math.min(40, Math.round((ru + rv) * Mth.PI / (grosor * 2.2F))));
        Vector3f[] pts = new Vector3f[n + 1];
        for (int i = 0; i <= n; i++) {
            float ang = vuelta + i / (float) n * Mth.TWO_PI;
            pts[i] = new Vector3f(c).add(new Vector3f(u).mul(Mth.cos(ang) * ru)).add(new Vector3f(v).mul(Mth.sin(ang) * rv));
        }
        Vector3f normal = new Vector3f(u).cross(v);
        if (normal.lengthSquared() < 1.0E-8F) normal.set(0, 1, 0);
        normal.normalize();
        tira(ps, vc, pts, normal, luz, grosor, 0F);
    }

    /** Recorrido de puntos: dos planos cruzados por tramo, con la V continua a lo largo. */
    public static void tira(PoseStack ps, VertexConsumer vc, Vector3f[] pts, Vector3f ref, int luz, float grosor, float fase) {
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        float mitad = grosor * 0.5F;
        float vAcum = fase;
        for (int i = 0; i + 1 < pts.length; i++) {
            Vector3f p0 = pts[i], p1 = pts[i + 1];
            Vector3f d = new Vector3f(p1).sub(p0);
            float dl = d.length();
            if (dl < 1.0E-5F) continue;
            d.div(dl);
            // lados de los dos planos: perpendiculares al tramo (con continuidad usando el tramo medio en las juntas)
            Vector3f r = Math.abs(d.dot(ref)) > 0.95F ? new Vector3f(1, 0, 0) : ref;
            Vector3f s1 = new Vector3f(d).cross(r).normalize();
            Vector3f s2 = new Vector3f(d).cross(s1).normalize();
            float v0 = vAcum, v1 = vAcum + dl / (grosor * REPETICION);
            vAcum = v1;
            quad(vc, m, nm, p0, p1, s1, mitad, 0F, 0.5F, v0, v1, s2, luz);
            quad(vc, m, nm, p0, p1, s2, mitad, 0.5F, 1F, v0, v1, s1, luz);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p0, Vector3f p1, Vector3f lado, float mitad,
                             float u0, float u1, float v0, float v1, Vector3f normal, int luz) {
        vertice(vc, m, n, p0, lado, -mitad, u0, v0, normal, luz);
        vertice(vc, m, n, p0, lado, mitad, u1, v0, normal, luz);
        vertice(vc, m, n, p1, lado, mitad, u1, v1, normal, luz);
        vertice(vc, m, n, p1, lado, -mitad, u0, v1, normal, luz);
    }

    private static void vertice(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, Vector3f lado, float k, float u, float v, Vector3f normal, int luz) {
        vc.vertex(m, p.x + lado.x * k, p.y + lado.y * k, p.z + lado.z * k).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, normal.x, normal.y, normal.z).endVertex();
    }
}
