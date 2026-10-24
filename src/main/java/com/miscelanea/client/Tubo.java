package com.miscelanea.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Dibuja un tubo de sección cuadrada con textura a lo largo de una curva de Bézier cúbica.
 * Se usa para la manguera (mochila → boquilla) y la cuerda del desatascador.
 * La textura se reparte así: U = las 4 caras alrededor del tubo, V = a lo largo (se repite).
 */
public final class Tubo {
    private Tubo() {}

    public static Vector3f bezier(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float u = 1.0F - t;
        float a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
        return new Vector3f(
                a * p0.x + b * p1.x + c * p2.x + d * p3.x,
                a * p0.y + b * p1.y + c * p2.y + d * p3.y,
                a * p0.z + b * p1.z + c * p2.z + d * p3.z);
    }

    /**
     * @param radio       medio grosor del tubo (en las unidades del PoseStack)
     * @param repeticionV cuántas unidades de longitud ocupa una repetición de la textura
     */
    public static void dibujar(PoseStack pose, VertexConsumer vc, int light,
                               Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                               float radio, int segmentos, float repeticionV) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        Vector3f[] centros = new Vector3f[segmentos + 1];
        for (int i = 0; i <= segmentos; i++) {
            centros[i] = bezier(p0, p1, p2, p3, i / (float) segmentos);
        }

        // Marco (dos normales) por anillo; se arrastra para que el tubo no se retuerza
        Vector3f prevN1 = null;
        Vector3f[][] anillos = new Vector3f[segmentos + 1][4];
        Vector3f[][] normales = new Vector3f[segmentos + 1][4];
        float[] vs = new float[segmentos + 1];
        float acumulado = 0.0F;
        for (int i = 0; i <= segmentos; i++) {
            Vector3f tan = new Vector3f(centros[Math.min(i + 1, segmentos)]).sub(centros[Math.max(i - 1, 0)]);
            if (tan.lengthSquared() < 1.0E-10F) tan.set(0, 1, 0);
            tan.normalize();

            Vector3f n1;
            if (prevN1 == null) {
                Vector3f ref = Math.abs(tan.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
                n1 = new Vector3f(tan).cross(ref).normalize();
            } else {
                // proyectar la normal anterior sobre el plano perpendicular a la tangente
                n1 = new Vector3f(prevN1).sub(new Vector3f(tan).mul(prevN1.dot(tan)));
                if (n1.lengthSquared() < 1.0E-8F) n1 = new Vector3f(tan).cross(0, 1, 0);
                n1.normalize();
            }
            prevN1 = n1;
            Vector3f n2 = new Vector3f(tan).cross(n1).normalize();

            for (int k = 0; k < 4; k++) {
                float ang = (float) (Math.PI / 4 + k * Math.PI / 2);
                float cx = (float) Math.cos(ang), sy = (float) Math.sin(ang);
                Vector3f dir = new Vector3f(n1).mul(cx).add(new Vector3f(n2).mul(sy));
                anillos[i][k] = new Vector3f(centros[i]).add(new Vector3f(dir).mul(radio * 1.41421F));
            }
            // normales de cara: entre dos esquinas consecutivas
            for (int k = 0; k < 4; k++) {
                float ang = (float) (k * Math.PI / 2 + Math.PI / 2);
                normales[i][k] = new Vector3f(n1).mul((float) Math.cos(ang)).add(new Vector3f(n2).mul((float) Math.sin(ang)));
            }
            if (i > 0) acumulado += centros[i].distance(centros[i - 1]);
            vs[i] = acumulado / repeticionV;
        }

        for (int i = 0; i < segmentos; i++) {
            for (int k = 0; k < 4; k++) {
                int k2 = (k + 1) % 4;
                float u0 = k / 4.0F, u1 = (k + 1) / 4.0F;
                Vector3f nor = normales[i][k];
                vertice(vc, m, n, anillos[i][k], u0, vs[i], nor, light);
                vertice(vc, m, n, anillos[i + 1][k], u0, vs[i + 1], nor, light);
                vertice(vc, m, n, anillos[i + 1][k2], u1, vs[i + 1], nor, light);
                vertice(vc, m, n, anillos[i][k2], u1, vs[i], nor, light);
            }
        }
    }

    private static void vertice(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float u, float v, Vector3f nor, int light) {
        vc.vertex(m, p.x, p.y, p.z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, nor.x, nor.y, nor.z)
                .endVertex();
    }
}
