package com.miscelanea.client.baile.videoclip;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Un decorado de videoclip. Los bloques van en coordenadas del decorado: el protagonista está en el bloque (0, 0, 0),
 * mirando hacia +Z (la cámara de frente lo mira desde +Z), y el suelo es la fila y = -1. Las luces se dibujan en el
 * mismo sistema pero centradas en el protagonista (el bloque (0,0,0) va de -0.5 a 0.5).
 */
abstract class Decorado {
    final Map<BlockPos, BlockState> bloques = new HashMap<>();

    /** Lo que pasa en este fotograma del videoclip. */
    record Momento(float pulso, float golpe, float reloj, String paso) {
        int tiempo() {
            return (int) Math.floor(pulso);
        }
    }

    void pon(int x, int y, int z, BlockState s) {
        bloques.put(new BlockPos(x, y, z), s);
    }

    void pon(int x, int y, int z, Block b) {
        pon(x, y, z, b.defaultBlockState());
    }

    abstract void construir();

    /** Luz de bloque con la que se ve todo (decorado y bailarines). */
    abstract int luz();

    /** Color de la niebla (r, g, b de 0 a 1) y dónde empieza y acaba. */
    abstract float[] niebla();

    abstract float cerca();

    abstract float lejos();

    /** Si el decorado se repite cada tantos bloques en Z (para pasear sin fin), o 0. */
    float periodo() {
        return 0;
    }

    /** ¿Se mueve con el baile (paseos y vuelos)? */
    boolean viaja() {
        return false;
    }

    /** Luces que se mueven con el decorado. */
    void luces(Matrix4f m, VertexConsumer brillo, VertexConsumer opaco, Momento t) {}

    // ------------------------------------------------------------------ ayudas de dibujo
    static void v(VertexConsumer c, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        c.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }

    static void v(VertexConsumer c, Matrix4f m, float x, float y, float z, int rgb, float a) {
        v(c, m, x, y, z, ((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, a);
    }

    /** Rectángulo horizontal a la altura y. */
    static void suelo(VertexConsumer c, Matrix4f m, float x0, float z0, float x1, float z1, float y, int rgb, float a) {
        v(c, m, x0, y, z0, rgb, a);
        v(c, m, x0, y, z1, rgb, a);
        v(c, m, x1, y, z1, rgb, a);
        v(c, m, x1, y, z0, rgb, a);
    }

    /** Rectángulo en la pared de x fija. */
    static void paredX(VertexConsumer c, Matrix4f m, float x, float y0, float z0, float y1, float z1, int rgb, float a) {
        v(c, m, x, y0, z0, rgb, a);
        v(c, m, x, y1, z0, rgb, a);
        v(c, m, x, y1, z1, rgb, a);
        v(c, m, x, y0, z1, rgb, a);
    }

    /** Rectángulo en la pared de z fija. */
    static void paredZ(VertexConsumer c, Matrix4f m, float z, float x0, float y0, float x1, float y1, int rgb, float a) {
        v(c, m, x0, y0, z, rgb, a);
        v(c, m, x0, y1, z, rgb, a);
        v(c, m, x1, y1, z, rgb, a);
        v(c, m, x1, y0, z, rgb, a);
    }

    /** Halo: tres cruces de planos cada vez más pequeñas (se suman y queda un brillo escalonado, muy de píxel). */
    static void halo(VertexConsumer c, Matrix4f m, float x, float y, float z, float r, int rgb, float a) {
        for (int k = 0; k < 3; k++) {
            float s = r * (1 - k * 0.3F);
            paredX(c, m, x, y - s, z - s, y + s, z + s, rgb, a);
            paredZ(c, m, z, x - s, y - s, x + s, y + s, rgb, a);
            suelo(c, m, x - s, z - s, x + s, z + s, y, rgb, a * 0.6F);
        }
    }

    /** Haz de luz: dos planos cruzados que se abren desde 'desde' (estrecho) hasta 'hasta' (ancho) y se apagan al final. */
    static void haz(VertexConsumer c, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float w0, float w1, int rgb, float a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-3) return;
        dx /= l;
        dy /= l;
        dz /= l;
        // u = dir x arriba, w = dir x u
        float ux = -dz, uy = 0, uz = dx;
        float ul = (float) Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-3) {
            ux = 1;
            uz = 0;
            ul = 1;
        }
        ux /= ul;
        uz /= ul;
        float wx = dy * uz - dz * uy, wy = dz * ux - dx * uz, wz = dx * uy - dy * ux;
        for (int k = 0; k < 2; k++) {
            float px = k == 0 ? ux : wx, py = k == 0 ? uy : wy, pz = k == 0 ? uz : wz;
            v(c, m, ax - px * w0, ay - py * w0, az - pz * w0, rgb, a);
            v(c, m, ax + px * w0, ay + py * w0, az + pz * w0, rgb, a);
            v(c, m, bx + px * w1, by + py * w1, bz + pz * w1, rgb, 0);
            v(c, m, bx - px * w1, by - py * w1, bz - pz * w1, rgb, 0);
        }
    }

    static float hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }
}
