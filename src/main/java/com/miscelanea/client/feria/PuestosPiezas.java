package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Piezas de los puestos de feria: la caseta de tiro entera (modelo a media escala), los carteles de tejado, los
 * marcadores de dígitos LED (pintados en el panel, nada de letras flotando) y los juegos de luces de las bombillas.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class PuestosPiezas {
    public static final ResourceLocation CASETA = rl("caseta"), COPETE_PATITOS = rl("copete_patitos"),
            COPETE_TOMBOLA = rl("copete_tombola"), COPETE_CHURROS = rl("copete_churros");
    private static final ResourceLocation DIGITOS = new ResourceLocation(Miscelanea.MOD_ID, "block/puestos/digitos");
    /** Orden de las celdas de la textura de dígitos (4 x 4): la 11 es el «8» apagado y la 12 está vacía. */
    private static final String CELDAS = "0123456789-8 PEH";
    /** Modo de las luces: en reposo, con alguien jugando y celebrando un premio. */
    public static final int REPOSO = 0, JUEGO = 1, PREMIO = 2;
    private static final int[] COLORES = {0xFF4040, 0xFFC840, 0x50E060, 0x40B0FF, 0xE060FF, 0xFFFFFF};

    private PuestosPiezas() {}

    static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/puestos/" + n);
    }

    public static List<ResourceLocation> todas() {
        return List.of(CASETA, COPETE_PATITOS, COPETE_TOMBOLA, COPETE_CHURROS);
    }

    /** Los modelos sueltos de los puestos se registran aquí (no hace falta tocar ClientEvents). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void registrar(net.minecraftforge.client.event.ModelEvent.RegisterAdditional e) {
        for (ResourceLocation r : todas()) e.register(r);
    }

    /** Modelo de pieza a escala, con la esquina (0,0,0) del modelo en el origen de la pose. */
    public static void modelo(PoseStack ps, VertexConsumer vc, ResourceLocation m, float esc, int luz) {
        ps.pushPose();
        ps.scale(esc, esc, esc);
        FeriaPiezas.modelo(ps, vc, m, 1, 1, 1, luz);
        ps.popPose();
    }

    /** Cartel de tejado (modelo de 16 px de ancho) centrado en x=cx, apoyado en y, de frente a -z. */
    public static void copete(PoseStack ps, VertexConsumer vc, ResourceLocation m, double cx, double y, double z, float esc, int luz) {
        ps.pushPose();
        ps.translate(cx, y, z);
        ps.scale(esc, esc, esc);
        ps.translate(-0.5, 0, -0.5);
        FeriaPiezas.modelo(ps, vc, m, 1, 1, 1, luz);
        ps.popPose();
    }

    // ------------------------------------------------------------------ luces
    private static int escala(int c, float k) {
        k = Mth.clamp(k, 0, 1);
        return (int) ((c >> 16 & 255) * k) << 16 | (int) ((c >> 8 & 255) * k) << 8 | (int) ((c & 255) * k);
    }

    private static float azar(int i) {
        int h = i * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535F;
    }

    /**
     * Color de la bombilla i de una ristra de n. En reposo: blanco cálido que respira y una ola más brillante que
     * recorre la ristra despacio (para llamar la atención). Jugando: colores que corren. Premio: todo destella.
     */
    public static int bombilla(int i, int n, float t, int modo) {
        switch (modo) {
            case JUEGO -> {
                int c = COLORES[Math.floorMod(i + (int) (t / 2.5F), COLORES.length)];
                return escala(c, 0.75F + 0.25F * Mth.sin(t * 0.5F + i));
            }
            case PREMIO -> {
                int fase = (int) (t / 3);
                boolean par = (i + fase) % 2 == 0;
                int c = fase % 4 < 2 ? (par ? 0xFFE060 : 0xFF3030) : (par ? 0xFFFFFF : 0xFFB020);
                return escala(c, par ? 1 : 0.55F);
            }
            default -> {
                float ola = (t * 0.12F) % (n + 6) - 3;
                float d = Math.abs(i - ola);
                float k = 0.62F + 0.12F * Mth.sin(t * 0.07F + i * 1.7F) + (d < 2.5F ? 0.3F * (1 - d / 2.5F) : 0);
                // alguna bombilla vieja que tiembla de vez en cuando
                if (azar(i * 31 + (int) (t / 40)) < 0.04F && ((int) t & 2) == 0) k *= 0.4F;
                return escala(i % 2 == 0 ? 0xFFE2A8 : 0xFFC468, k);
            }
        }
    }

    /** Ristra recta de n bombillas entre dos puntos (coordenadas de la pose). */
    public static void ristra(PoseStack ps, VertexConsumer vc, double ax, double ay, double az, double bx, double by, double bz,
                              int n, float tam, float t, int modo, int desfase) {
        for (int i = 0; i < n; i++) {
            double k = n == 1 ? 0.5 : i / (double) (n - 1);
            FeriaPiezas.bombilla(ps, vc, ax + (bx - ax) * k, ay + (by - ay) * k, az + (bz - az) * k, tam, bombilla(i + desfase, n, t, modo));
        }
    }

    // ------------------------------------------------------------------ dígitos LED
    private static TextureAtlasSprite sprite(ResourceLocation r) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(r);
    }

    /**
     * Texto en dígitos de siete segmentos pintado en un panel que mira hacia -z (el frente de la pose), centrado en
     * (cx, cy) y a la profundidad z; h es el alto de cada celda. Debajo de cada dígito se ve el «8» apagado.
     */
    public static void led(PoseStack ps, VertexConsumer vc, String s, double cx, double cy, double z, double h, int rgb, boolean fantasma) {
        TextureAtlasSprite sp = sprite(DIGITOS);
        double paso = h * 0.62, ancho = (s.length() - 1) * paso + h;
        // de frente, la x local crece hacia la izquierda del que mira: el primer carácter va en +x
        double x0 = cx + ancho / 2 - h / 2;
        int apagado = escala(rgb, 0.16F);
        for (int i = 0; i < s.length(); i++) {
            double x = x0 - i * paso;
            if (fantasma) celda(ps, vc, sp, CELDAS.indexOf('8', 10), x, cy, z + 0.002, h, apagado);
            int c = CELDAS.indexOf(s.charAt(i));
            if (c < 0 || c == 12) continue;
            celda(ps, vc, sp, c, x, cy, z, h, rgb);
        }
    }

    private static void celda(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, int celda, double x, double y, double z, double h, int rgb) {
        float u0 = (celda % 4) * 4, v0 = (celda / 4) * 4;
        double r = h / 2;
        // vértices en orden antihorario visto desde -z
        cuad(ps, vc, sp, rgb, 0xF000F0, 0, 0, -1,
                new double[]{x + r, y + r, z, u0, v0}, new double[]{x - r, y + r, z, u0 + 4, v0},
                new double[]{x - r, y - r, z, u0 + 4, v0 + 4}, new double[]{x + r, y - r, z, u0, v0 + 4});
    }

    /** Cuadrilátero {x, y, z, u, v} con normal n; si los vértices vienen al revés se les da la vuelta. */
    static void cuad(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, int rgb, int luz, float nx, float ny, float nz, double[]... v) {
        double ax = v[1][0] - v[0][0], ay = v[1][1] - v[0][1], az = v[1][2] - v[0][2];
        double bx = v[2][0] - v[0][0], by = v[2][1] - v[0][1], bz = v[2][2] - v[0][2];
        boolean reves = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz < 0;
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        for (int k = 0; k < 4; k++) {
            double[] p = v[reves ? 3 - k : k];
            vc.vertex(m, (float) p[0], (float) p[1], (float) p[2]).color(r, g, b, 1F)
                    .uv(sp.getU(p[3]), sp.getV(p[4])).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz)
                    .normal(nm, nx, ny, nz).endVertex();
        }
    }

    // ------------------------------------------------------------------ peluches
    /** Peluche (modelo de bloque) en x, y, z mirando hacia yaw, a escala esc; salta 'bote' bloques. */
    public static void peluche(PoseStack ps, VertexConsumer vc, int color, double x, double y, double z, float yaw, float esc, float bote, float ladeo, int luz) {
        ps.pushPose();
        ps.translate(x, y + bote, z);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        if (ladeo != 0) ps.mulPose(Axis.ZP.rotationDegrees(ladeo));
        ps.scale(esc, esc, esc);
        ps.translate(-0.5, 0, -0.5);
        FeriaPiezas.modelo(ps, vc, CasetaTiroRenderer.PELUCHE[Math.floorMod(color, 4)], 1, 1, 1, luz);
        ps.popPose();
    }

    /** Peluche colgado de un cordel desde (x, yArriba, z), balanceándose. */
    public static void pelucheColgado(PoseStack ps, VertexConsumer vc, int color, double x, double yArriba, double z, double largo, float t, float fase, float esc, int luz) {
        float ang = Mth.sin(t * 0.045F + fase) * 7 + Mth.sin(t * 0.11F + fase * 2) * 2;
        ps.pushPose();
        ps.translate(x, yArriba, z);
        ps.mulPose(Axis.XP.rotationDegrees(ang));
        ps.mulPose(Axis.ZP.rotationDegrees(ang * 0.5F));
        FeriaPiezas.barra(ps, vc, "negro", 0, 0, 0, 0, -largo, 0, 0.008F, 0.008F, luz);
        ps.translate(0, -largo - esc * 0.85, 0);
        ps.mulPose(Axis.YP.rotationDegrees(180 + Mth.sin(t * 0.03F + fase) * 25));
        ps.scale(esc, esc, esc);
        ps.translate(-0.5, 0, -0.5);
        FeriaPiezas.modelo(ps, vc, CasetaTiroRenderer.PELUCHE[Math.floorMod(color, 4)], 1, 1, 1, luz);
        ps.popPose();
    }

    /** Altura de un saltito (0..alto) que dura 'dura' tics a partir de dt. */
    public static float salto(float dt, float dura, float alto) {
        if (dt < 0 || dt > dura) return 0;
        float k = dt / dura;
        return alto * 4 * k * (1 - k);
    }
}
