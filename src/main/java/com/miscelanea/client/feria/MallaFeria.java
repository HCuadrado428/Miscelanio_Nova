package com.miscelanea.client.feria;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Geometría de la feria "horneada": se graba UNA vez (posición, color, uv y normal de cada vértice, ya transformados a
 * coordenadas del bloque) pasando las piezas de siempre ({@link FeriaPiezas}) por una {@link Grabadora}, y luego cada
 * fotograma solo se multiplica por la matriz del momento (con el giro de la rueda) y se vuelca al buffer. Así no hay que
 * buscar modelos, apilar poses ni recorrer cientos de BakedQuad por fotograma.
 * <p>La luz no se graba: se pone al emitir (la del bloque, o 0xF000F0 para las bombillas). Las bombillas se graban en
 * blanco con una "etiqueta" (su número en la ristra) y al emitir se tiñen con el color que toque en ese fotograma.
 */
final class MallaFeria {
    private float[] pos = new float[3 * 256], nor = new float[3 * 256], uv = new float[2 * 256], col = new float[4 * 256];
    private int[] etiq = new int[256];
    private int n;

    /** Vectores de trabajo (solo se dibuja en el hilo de render). */
    private static final Vector3f P = new Vector3f(), N = new Vector3f();

    int vertices() {
        return n;
    }

    private void anadir(float x, float y, float z, float r, float g, float b, float a, float u, float v, float nx, float ny, float nz, int e) {
        if (n == etiq.length) {
            int c = n * 2;
            pos = Arrays.copyOf(pos, 3 * c);
            nor = Arrays.copyOf(nor, 3 * c);
            uv = Arrays.copyOf(uv, 2 * c);
            col = Arrays.copyOf(col, 4 * c);
            etiq = Arrays.copyOf(etiq, c);
        }
        pos[3 * n] = x; pos[3 * n + 1] = y; pos[3 * n + 2] = z;
        nor[3 * n] = nx; nor[3 * n + 1] = ny; nor[3 * n + 2] = nz;
        uv[2 * n] = u; uv[2 * n + 1] = v;
        col[4 * n] = r; col[4 * n + 1] = g; col[4 * n + 2] = b; col[4 * n + 3] = a;
        etiq[n] = e;
        n++;
    }

    private void recortar() {
        pos = Arrays.copyOf(pos, 3 * n);
        nor = Arrays.copyOf(nor, 3 * n);
        uv = Arrays.copyOf(uv, 2 * n);
        col = Arrays.copyOf(col, 4 * n);
        etiq = Arrays.copyOf(etiq, n);
    }

    /**
     * Vuelca la malla con la pose dada. {@code color}: de la etiqueta al color 0xRRGGBB que multiplica al grabado (null =
     * tal cual). Las bombillas de una misma etiqueta van seguidas, así que el color se calcula una vez por bombilla.
     */
    void emitir(VertexConsumer vc, PoseStack.Pose pose, int luz, @Nullable IntUnaryOperator color) {
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        int ant = Integer.MIN_VALUE;
        float cr = 1, cg = 1, cb = 1;
        for (int i = 0; i < n; i++) {
            if (color != null && etiq[i] != ant) {
                ant = etiq[i];
                int rgb = color.applyAsInt(ant);
                cr = (rgb >> 16 & 255) / 255F;
                cg = (rgb >> 8 & 255) / 255F;
                cb = (rgb & 255) / 255F;
            }
            m.transformPosition(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2], P);
            nm.transform(nor[3 * i], nor[3 * i + 1], nor[3 * i + 2], N);
            vc.vertex(P.x, P.y, P.z, col[4 * i] * cr, col[4 * i + 1] * cg, col[4 * i + 2] * cb, col[4 * i + 3],
                    uv[2 * i], uv[2 * i + 1], OverlayTexture.NO_OVERLAY, luz, N.x, N.y, N.z);
        }
    }

    /** VertexConsumer que en vez de dibujar guarda los vértices en una malla. */
    static final class Grabadora implements VertexConsumer {
        private final MallaFeria m = new MallaFeria();
        /** Etiqueta de lo que se graba a continuación (el número de la bombilla). */
        int etiqueta;
        private float x, y, z, r = 1, g = 1, b = 1, a = 1, u, v, nx, ny = 1, nz;

        @Override
        public VertexConsumer vertex(double px, double py, double pz) {
            x = (float) px; y = (float) py; z = (float) pz;
            return this;
        }

        @Override
        public VertexConsumer color(int pr, int pg, int pb, int pa) {
            r = pr / 255F; g = pg / 255F; b = pb / 255F; a = pa / 255F;
            return this;
        }

        @Override
        public VertexConsumer uv(float pu, float pv) {
            u = pu; v = pv;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int pu, int pv) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int pu, int pv) {
            return this;
        }

        @Override
        public VertexConsumer normal(float px, float py, float pz) {
            nx = px; ny = py; nz = pz;
            return this;
        }

        @Override
        public void endVertex() {
            m.anadir(x, y, z, r, g, b, a, u, v, nx, ny, nz, etiqueta);
            r = g = b = a = 1;
        }

        @Override
        public void defaultColor(int pr, int pg, int pb, int pa) {}

        @Override
        public void unsetDefaultColor() {}

        MallaFeria terminar() {
            m.recortar();
            return m;
        }
    }
}
