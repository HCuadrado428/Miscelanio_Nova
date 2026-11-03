package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/** Piezas sueltas de la feria (barras, bombillas, coches...) y cómo dibujarlas. Las cápsulas de la noria, los caballitos,
 *  las tazas del pulpo y otras piezas redondas son mallas aparte ({@link MallaRecurso}). */
public final class FeriaPiezas {
    public static final String[] BARRAS = {"metal_blanco", "metal_rojo", "dorado", "madera_a", "madera_b", "lona_roja", "lona_blanca", "agua", "torre0", "torre1", "torre2", "negro", "cromo"};
    public static final ResourceLocation BOMBILLA = rl("bombilla");
    public static final ResourceLocation[] COCHE = new ResourceLocation[4];
    public static final String[] SUELTOS = {"bruja", "esqueleto", "calabaza", "fantasma", "ojos", "vagoneta", "pato", "pato_oro", "diana", "nube", "palomita", "capsula", "simbolo0", "simbolo1", "simbolo2", "simbolo3", "simbolo4", "bolo", "bola", "dardo", "trozo", "balon", "bola_petanca", "boliche", "tablero", "escala0", "escala1", "escala2", "escala3", "escala4", "saco", "mazo", "campana"};

    static {
        for (int i = 0; i < 4; i++) {
            COCHE[i] = rl("coche" + i);
        }
    }

    private FeriaPiezas() {}

    public static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/feria/" + n);
    }

    public static ResourceLocation barra(String n) {
        return rl("barra_" + n);
    }

    public static List<ResourceLocation> todas() {
        List<ResourceLocation> l = new ArrayList<>();
        for (String b : BARRAS) l.add(barra(b));
        l.add(BOMBILLA);
        for (String n : SUELTOS) l.add(rl(n));
        for (int i = 0; i < 4; i++) l.add(new ResourceLocation(Miscelanea.MOD_ID, "block/peluche" + i));
        for (int i = 0; i < 4; i++) {
            l.add(COCHE[i]);
        }
        return l;
    }

    public static void modelo(PoseStack ps, VertexConsumer vc, ResourceLocation m, float r, float g, float b, int luz) {
        Minecraft mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), vc, null, mc.getModelManager().getModel(m), r, g, b, luz,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** Barra de sección alto x ancho entre dos puntos (coordenadas de bloque relativas a la pose). */
    public static void barra(PoseStack ps, VertexConsumer vc, String tex, double ax, double ay, double az, double bx, double by, double bz,
                             float alto, float ancho, int luz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double h = Math.sqrt(dx * dx + dz * dz), len = Math.sqrt(h * h + dy * dy);
        if (len < 1e-4) return;
        ps.pushPose();
        ps.translate(ax, ay, az);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(-dz, dx)));
        ps.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, h)));
        ps.scale((float) len, alto * 16, ancho * 16);
        ps.translate(0, -0.5, -0.5);
        modelo(ps, vc, barra(tex), 1, 1, 1, luz);
        ps.popPose();
    }

    /**
     * Porción de tarta (sector circular con grosor) entre los ángulos a0 y a1, de radio r, entre las alturas y0 e y1.
     * Para suelos redondos: las porciones encajan sin pisarse (antes las tablas se cruzaban y parpadeaban).
     */
    public static void cuna(PoseStack ps, VertexConsumer vc, String tex, double cx, double cz, double a0, double a1, double r,
                            double y0, double y1, int luz) {
        cuna(ps, vc, tex, cx, cz, a0, a1, r, y0, y1, 0, luz);
    }

    /** Igual, pero el centro {@code subida} más alto que el borde: un cono (el toldo del tiovivo). */
    public static void cuna(PoseStack ps, VertexConsumer vc, String tex, double cx, double cz, double a0, double a1, double r,
                            double y0, double y1, double subida, int luz) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sp = Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                .apply(new ResourceLocation(Miscelanea.MOD_ID, "block/feria/" + tex));
        double ri = 0.02;
        double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
        double[] i0 = {cx + c0 * ri, cz + s0 * ri}, i1 = {cx + c1 * ri, cz + s1 * ri};
        double[] p0 = {cx + c0 * r, cz + s0 * r}, p1 = {cx + c1 * r, cz + s1 * r};
        double am = (a0 + a1) / 2;
        // arriba y abajo: la veta va del centro hacia fuera
        double yc1 = y1 + subida, yc0 = y0 + subida;
        quad(ps, vc, sp, luz, 0, 1, 0,
                new double[]{i0[0], yc1, i0[1], 0, 8}, new double[]{p0[0], y1, p0[1], 16, 0},
                new double[]{p1[0], y1, p1[1], 16, 16}, new double[]{i1[0], yc1, i1[1], 0, 8});
        quad(ps, vc, sp, luz, 0, -1, 0,
                new double[]{i0[0], yc0, i0[1], 0, 8}, new double[]{p0[0], y0, p0[1], 16, 0},
                new double[]{p1[0], y0, p1[1], 16, 16}, new double[]{i1[0], yc0, i1[1], 0, 8});
        // canto exterior
        double h = (y1 - y0) * 16;
        quad(ps, vc, sp, luz, (float) Math.cos(am), 0, (float) Math.sin(am),
                new double[]{p0[0], y0, p0[1], 0, 8 + h / 2}, new double[]{p1[0], y0, p1[1], 16, 8 + h / 2},
                new double[]{p1[0], y1, p1[1], 16, 8 - h / 2}, new double[]{p0[0], y1, p0[1], 0, 8 - h / 2});
    }

    /** Cuadrilátero {x, y, z, u, v} con normal n; se le da la vuelta si hace falta para que mire hacia n. */
    private static void quad(PoseStack ps, VertexConsumer vc, net.minecraft.client.renderer.texture.TextureAtlasSprite sp, int luz,
                             float nx, float ny, float nz, double[]... v) {
        double ax = v[1][0] - v[0][0], ay = v[1][1] - v[0][1], az = v[1][2] - v[0][2];
        double bx = v[2][0] - v[0][0], by = v[2][1] - v[0][1], bz = v[2][2] - v[0][2];
        double cxn = ay * bz - az * by, cyn = az * bx - ax * bz, czn = ax * by - ay * bx;
        boolean al_reves = cxn * nx + cyn * ny + czn * nz < 0;
        // sombreado sencillo como el de los bloques: arriba 1, lados 0,8, abajo 0,5
        float sombra = ny > 0.5F ? 1F : ny < -0.5F ? 0.5F : 0.8F;
        org.joml.Matrix4f m = ps.last().pose();
        org.joml.Matrix3f nm = ps.last().normal();
        for (int k = 0; k < 4; k++) {
            double[] p = v[al_reves ? 3 - k : k];
            vc.vertex(m, (float) p[0], (float) p[1], (float) p[2]).color(sombra, sombra, sombra, 1F)
                    .uv(sp.getU(p[3]), sp.getV(p[4])).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz)
                    .normal(nm, nx, ny, nz).endVertex();
        }
    }

    /** Caja de tamaño sx, sy, sz centrada en (cx, cy, cz), sin girar. */
    public static void caja(PoseStack ps, VertexConsumer vc, String tex, double cx, double cy, double cz, double sx, double sy, double sz, int luz) {
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.scale((float) sx, (float) sy * 16, (float) sz * 16);
        ps.translate(-0.5, -0.5, -0.5);
        modelo(ps, vc, barra(tex), 1, 1, 1, luz);
        ps.popPose();
    }

    public static void bombilla(PoseStack ps, VertexConsumer vc, double x, double y, double z, float tam, int rgb) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(tam * 16, tam * 16, tam * 16);
        ps.translate(-0.5, -0.5, -0.5);
        modelo(ps, vc, BOMBILLA, (rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, 0xF000F0);
        ps.popPose();
    }

    static final int[] LUCES = {0xFF4040, 0xFFC840, 0x50E060, 0x40B0FF, 0xE060FF, 0xFFFFFF};

    /** Color de la bombilla i: una ristra que "corre" cuando la atracción gira. */
    public static int luz(int i, float t, boolean gira) {
        if (!gira) return i % 2 == 0 ? 0xFFE8B0 : 0xFFC860;
        int k = Math.floorMod(i + (int) (t / 3), LUCES.length);
        float pulso = 0.7F + 0.3F * Mth.sin(t * 0.4F + i);
        int c = LUCES[k];
        int r = (int) ((c >> 16 & 255) * pulso), g = (int) ((c >> 8 & 255) * pulso), b = (int) ((c & 255) * pulso);
        return r << 16 | g << 8 | b;
    }
}
