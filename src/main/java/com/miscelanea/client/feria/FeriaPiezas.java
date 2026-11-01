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

/** Piezas sueltas de la feria (barras, bombillas, cabinas, caballitos, coches) y cómo dibujarlas. */
public final class FeriaPiezas {
    public static final String[] BARRAS = {"metal_blanco", "metal_rojo", "dorado", "madera_a", "madera_b", "lona_roja", "lona_blanca", "agua"};
    public static final ResourceLocation BOMBILLA = rl("bombilla");
    public static final ResourceLocation[] CABINA = new ResourceLocation[4], CABALLO = new ResourceLocation[4], COCHE = new ResourceLocation[4];
    public static final String[] SUELTOS = {"bruja", "esqueleto", "calabaza", "fantasma", "ojos", "vagoneta", "pato", "pato_oro", "diana"};

    static {
        for (int i = 0; i < 4; i++) {
            CABINA[i] = rl("cabina" + i);
            CABALLO[i] = rl("caballo" + i);
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
            l.add(CABINA[i]);
            l.add(CABALLO[i]);
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
