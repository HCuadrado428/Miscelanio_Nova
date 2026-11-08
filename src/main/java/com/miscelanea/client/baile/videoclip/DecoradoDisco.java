package com.miscelanea.client.baile.videoclip;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import org.joml.Matrix4f;

/**
 * Boogie Wonderland: discoteca de los setenta. Pista de baldosas que se encienden con la música (cambian de dibujo cada
 * cuatro compases), bola de espejos que gira y llena la sala de puntitos de luz, escenario con altavoces y un sol de
 * rayos naranjas detrás, paredes a franjas marrones, naranjas y moradas con un neón que cambia de color, focos que
 * barren la pista desde el techo, sofás, mesitas y la barra. Para cualquier canción que no tenga decorado propio.
 */
final class DecoradoDisco extends Decorado {
    static final int R = 13, ALTO = 8, PISTA = 6;
    static final int[] PALETA = {0xFF3CC8, 0x3CDCFF, 0xFFDC3C, 0xFF7828, 0x5AFF78, 0xAA50FF};
    /** Dónde está la bola (en el sistema de las luces). */
    static final float BX = 0, BY = 6.3F, BZ = 0, BR = 0.55F;

    @Override
    int luz() {
        return 9;
    }

    @Override
    float[] niebla() {
        return new float[]{0.05F, 0.015F, 0.08F};
    }

    @Override
    float cerca() {
        return 12;
    }

    @Override
    float lejos() {
        return 34;
    }

    @Override
    void construir() {
        Block[] franjas = {Blocks.BROWN_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA,
                Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CONCRETE};
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                int ax = Math.abs(x), az = Math.abs(z), d = Math.max(ax, az);
                if (d == R) {
                    // paredes: la de detrás del escenario negra (para el sol de rayos), las demás a franjas setenteras
                    for (int y = -1; y <= ALTO; y++) {
                        Block b = z == -R && ax < R ? Blocks.BLACK_CONCRETE : y < 0 || y >= ALTO ? Blocks.BLACK_CONCRETE : franjas[y];
                        pon(x, y, z, b);
                    }
                    continue;
                }
                Block s;
                if (ax <= PISTA && az <= PISTA) s = ((x + z) & 1) == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
                else if (d == PISTA + 1) s = Blocks.GOLD_BLOCK;
                else s = d == PISTA + 3 ? Blocks.ORANGE_WOOL : Blocks.BROWN_WOOL;
                pon(x, -1, z, s);
                pon(x, ALTO, z, Blocks.BLACK_CONCRETE);
            }
        // escenario detrás: tarima, borde dorado, altavoces y el equipo del pinchadiscos
        for (int x = -9; x <= 9; x++)
            for (int z = -12; z <= -10; z++) pon(x, 0, z, z == -10 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE);
        for (int x : new int[]{-8, -7, 7, 8})
            for (int y = 1; y <= 3; y++) pon(x, y, -12, y == 3 && (x == -7 || x == 7) ? Blocks.AIR : Blocks.NOTE_BLOCK);
        pon(-1, 1, -11, Blocks.JUKEBOX);
        pon(0, 1, -11, Blocks.JUKEBOX);
        pon(1, 1, -11, Blocks.JUKEBOX);
        for (int x = -3; x <= 3; x += 6) pon(x, 1, -11, Blocks.END_ROD);
        // sofás a los lados y mesitas con flores
        for (int z = -4; z <= 4; z++) {
            if (z == 0) continue;
            pon(12, 0, z, Blocks.CRIMSON_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            pon(-12, 0, z, Blocks.CRIMSON_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        }
        for (int z : new int[]{-2, 2})
            for (int x : new int[]{-10, 10}) {
                pon(x, 0, z, Blocks.DARK_OAK_FENCE);
                pon(x, 1, z, z < 0 ? Blocks.POTTED_RED_TULIP : Blocks.POTTED_ORANGE_TULIP);
            }
        // la barra delante, con taburetes
        for (int x = -5; x <= 5; x++) {
            pon(x, 0, 12, Blocks.DARK_OAK_PLANKS);
            pon(x, 1, 12, Blocks.QUARTZ_SLAB);
        }
        for (int x = -4; x <= 4; x += 2) {
            pon(x, 0, 10, Blocks.DARK_OAK_FENCE);
            pon(x, 1, 10, Blocks.RED_CARPET);
        }
        // la bola cuelga de una cadena
        pon(0, 7, 0, Blocks.CHAIN);
    }

    @Override
    void luces(Matrix4f m, VertexConsumer brillo, VertexConsumer opaco, Momento t) {
        float golpe = t.golpe(), reloj = t.reloj();
        int tiempo = t.tiempo(), compas = Math.floorDiv(tiempo, 16), modo = Math.floorMod(compas, 4);
        float pared = R - 0.52F;
        // ---- baldosas de la pista
        for (int i = -PISTA; i <= PISTA; i++)
            for (int j = -PISTA; j <= PISTA; j++) {
                boolean on;
                int col;
                switch (modo) {
                    case 0 -> {
                        on = ((i + j + tiempo) & 1) == 0;
                        col = PALETA[Math.floorMod(tiempo / 2, PALETA.length)];
                    }
                    case 1 -> {
                        int d = Math.max(Math.abs(i), Math.abs(j));
                        on = Math.floorMod(d - tiempo, 3) == 0;
                        col = PALETA[Math.floorMod(d, PALETA.length)];
                    }
                    case 2 -> {
                        on = Math.floorMod(i - j + tiempo * 2, 5) < 2;
                        col = PALETA[Math.floorMod((i - j + tiempo * 2) / 5, PALETA.length)];
                    }
                    default -> {
                        on = hash(i, j, tiempo) < 0.45F;
                        col = PALETA[(int) (hash(j, i, tiempo + 7) * PALETA.length) % PALETA.length];
                    }
                }
                float a = on ? 0.45F + 0.4F * golpe : 0.05F;
                suelo(brillo, m, i - 0.47F, j - 0.47F, i + 0.47F, j + 0.47F, 0.012F, col, a);
                if (on) suelo(brillo, m, i - 0.3F, j - 0.3F, i + 0.3F, j + 0.3F, 0.014F, 0xFFFFFF, 0.25F * golpe);
            }
        // ---- bola de espejos
        float giro = reloj * 0.9F;
        int anillos = 8, gajos = 16;
        for (int a = 0; a < anillos; a++)
            for (int g = 0; g < gajos; g++) {
                float t0 = -Mth.HALF_PI + Mth.PI * a / anillos, t1 = -Mth.HALF_PI + Mth.PI * (a + 1) / anillos;
                float p0 = giro + Mth.TWO_PI * g / gajos, p1 = giro + Mth.TWO_PI * (g + 1) / gajos;
                float h = hash(a, g, 3);
                boolean destello = Math.floorMod(a * 7 + g * 13 + (int) (reloj * 9), 23) == 0;
                float gris = destello ? 1F : 0.45F + 0.4F * h;
                int c = ((int) (gris * 255) << 16) | ((int) (gris * 255) << 8) | (int) (Math.min(1, gris * 1.08F) * 255);
                bola(opaco, m, t0, p0, c);
                bola(opaco, m, t0, p1, c);
                bola(opaco, m, t1, p1, c);
                bola(opaco, m, t1, p0, c);
            }
        halo(brillo, m, BX, BY, BZ, 1.1F, 0xFFFFFF, 0.06F + 0.05F * golpe);
        // ---- puntitos de luz de la bola por toda la sala
        int n = 70;
        for (int k = 0; k < n; k++) {
            float yv = -0.15F - 0.85F * (k + 0.5F) / n;                       // casi todos hacia abajo y a los lados
            float rr = (float) Math.sqrt(1 - yv * yv), fi = k * 2.39996F + giro;
            float dx = Mth.cos(fi) * rr, dz = Mth.sin(fi) * rr, dy = yv;
            float tt = 99;
            if (dy < 0) tt = Math.min(tt, (0.02F - BY) / dy);
            if (dx > 0) tt = Math.min(tt, (pared - BX) / dx);
            if (dx < 0) tt = Math.min(tt, (-pared - BX) / dx);
            if (dz > 0) tt = Math.min(tt, (pared - BZ) / dz);
            if (dz < 0) tt = Math.min(tt, (-pared - BZ) / dz);
            float px = BX + dx * tt, py = BY + dy * tt, pz = BZ + dz * tt, s = 0.13F;
            int col = k % 5 == 0 ? PALETA[k % PALETA.length] : 0xFFFFFF;
            float al = 0.55F + 0.3F * golpe;
            if (Math.abs(py - 0.02F) < 0.01F) suelo(brillo, m, px - s, pz - s, px + s, pz + s, 0.03F, col, al);
            else if (Math.abs(Math.abs(px) - pared) < 0.01F) paredX(brillo, m, px, py - s, pz - s, py + s, pz + s, col, al);
            else paredZ(brillo, m, pz, px - s, py - s, px + s, py + s, col, al);
        }
        // ---- sol de rayos detrás del escenario
        float cz = -pared, cy = 4.2F, giroSol = reloj * 0.12F;
        for (int k = 0; k < 16; k++) {
            // sin shaders la luz suma mucho sobre las paredes naranjas: rayos más cortos y más suaves (si no, salían
            // como planos amarillos macizos tapando media pantalla)
            float a0 = giroSol + k * Mth.TWO_PI / 16, a1 = a0 + Mth.TWO_PI / 32, L = 7.5F;
            int col = (k & 1) == 0 ? 0xFF8020 : 0xFFD040;
            float al = 0.08F + 0.07F * golpe;
            v(brillo, m, 0, cy, cz, col, al);
            v(brillo, m, 0, cy, cz, col, al);
            v(brillo, m, Mth.cos(a0) * L, cy + Mth.sin(a0) * L, cz, col, al * 0.3F);
            v(brillo, m, Mth.cos(a1) * L, cy + Mth.sin(a1) * L, cz, col, al * 0.3F);
        }
        halo(brillo, m, 0, cy, cz + 0.05F, 1.4F, 0xFFE070, 0.2F + 0.15F * golpe);
        // ---- neón de las paredes (cambia de color poco a poco y late con la música)
        int neon = PALETA[Math.floorMod(compas, PALETA.length)];
        float an = 0.5F + 0.4F * golpe;
        paredX(brillo, m, pared, 5.35F, -pared, 5.65F, pared, neon, an);
        paredX(brillo, m, -pared, 5.35F, -pared, 5.65F, pared, neon, an);
        paredZ(brillo, m, pared, -pared, 5.35F, pared, 5.65F, neon, an);
        paredZ(brillo, m, -9.48F, -9.5F, 0.8F, 9.5F, 0.96F, PALETA[Math.floorMod(compas + 2, PALETA.length)], an);   // borde del escenario
        // ---- focos del techo que barren la pista
        for (int k = 0; k < 4; k++) {
            float sx = (k & 1) == 0 ? -9 : 9, sz = k < 2 ? -9 : 9;
            float ox = Mth.sin(reloj * 0.7F + k * 1.7F) * 4, oz = Mth.cos(reloj * 0.55F + k * 2.3F) * 4;
            int col = PALETA[Math.floorMod(k + compas, PALETA.length)];
            haz(brillo, m, sx, ALTO - 0.05F, sz, ox, 0.02F, oz, 0.12F, 1.3F, col, 0.10F + 0.06F * golpe);
            suelo(brillo, m, ox - 0.9F, oz - 0.9F, ox + 0.9F, oz + 0.9F, 0.02F, col, 0.18F + 0.1F * golpe);
        }
    }

    private static void bola(VertexConsumer c, Matrix4f m, float t, float p, int rgb) {
        float x = BX + BR * Mth.cos(t) * Mth.cos(p), y = BY + BR * Mth.sin(t), z = BZ + BR * Mth.cos(t) * Mth.sin(p);
        v(c, m, x, y, z, rgb, 1);
    }
}
