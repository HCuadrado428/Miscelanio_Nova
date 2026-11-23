package com.miscelanea.client.camaras;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Dibujo de una pantalla de cámara en el mundo (la tele y la pared de monitores): la imagen del canal (o estática), recortada
 * para llenar el rectángulo sin deformarse, en gris verdoso de laboratorio, y encima los rótulos (canal y nombre, «REC», la
 * hora, «SIN SEÑAL»). El rectángulo se da en píxeles del modelo mirando al norte (x crece hacia la izquierda de quien mira)
 * y se coloca con {@link com.miscelanea.camaras.Montaje#mapa}, así que vale para cualquier orientación y para varios bloques.
 */
public final class Pantalla {
    private Pantalla() {}

    private static final int LUZ = 0xF000F0, FONDO = 0x99000000;
    /** Alto de la pantalla en "píxeles de texto" (en las pantallas grandes se limita el tamaño de letra). */
    private static final float ALTO_TEXTO = 31.5F;
    private static final Map<BlockPos, long[]> CAMBIOS = new HashMap<>();

    /**
     * @param xIzq x del borde izquierdo (para quien mira), xDer del derecho (xIzq &gt; xDer), yAbajo/yArriba, z del plano de la
     *             pantalla; todo en píxeles del modelo mirando al norte, relativo al bloque que dibuja.
     */
    public static void dibujar(PoseStack ps, MultiBufferSource buf, Direction d, BlockPos pos, int canal,
                               double xIzq, double xDer, double yAbajo, double yArriba, double z) {
        CamarasCliente.visto(canal);
        CamarasCliente.pedirLista();      // para el nombre de la cámara en el rótulo
        int ef = CamarasCliente.efectivo(canal);
        // al cambiar de canal, medio segundo de estática (como una tele de verdad)
        long ahora = Util.getMillis();
        long[] ult = CAMBIOS.computeIfAbsent(pos.immutable(), k -> new long[]{canal, 0});
        if (ult[0] != canal) {
            ult[0] = canal;
            ult[1] = ahora;
            if (CAMBIOS.size() > 128) CAMBIOS.clear();
        }
        boolean cambiando = ahora - ult[1] < 450;
        boolean senal = ef > 0 && CamarasCliente.hayEnvio(ef) && !cambiando;
        ResourceLocation rl = senal ? CamarasCliente.senal(ef).rl : CamarasCliente.estatica();

        // la imagen, recortada (no deformada) para llenar el rectángulo
        double ancho = xIzq - xDer, alto = yArriba - yAbajo, a = ancho / alto, img = 16.0 / 9.0;
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (a > img) {
            float k = (float) (img / a);
            v0 = (1 - k) / 2;
            v1 = 1 - v0;
        } else {
            float k = (float) (a / img);
            u0 = (1 - k) / 2;
            u1 = 1 - u0;
        }
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(rl));
        double[][] p = {pt(d, xDer, yAbajo, z), pt(d, xIzq, yAbajo, z), pt(d, xIzq, yArriba, z), pt(d, xDer, yArriba, z)};
        float[][] uv = {{u1, v1}, {u0, v1}, {u0, v0}, {u1, v0}};
        var m = ps.last().pose();
        var n = ps.last().normal();
        int r = senal ? 205 : 255, g = senal ? 245 : 255, b = senal ? 225 : 255;     // gris verdoso de laboratorio
        for (int i = 0; i < 4; i++)
            vc.vertex(m, (float) p[i][0], (float) p[i][1], (float) p[i][2]).color(r, g, b, 255).uv(uv[i][0], uv[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LUZ).normal(n, d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();

        // los rótulos, en el plano de la pantalla (x hacia la derecha de quien mira, y hacia abajo), un pelín por delante
        Font f = Minecraft.getInstance().font;
        double porPx = Math.min(alto / ALTO_TEXTO, 0.45);          // píxeles del modelo por píxel de texto
        float wt = (float) (ancho / porPx), ht = (float) (alto / porPx);
        double[] o = pt(d, xIzq, yArriba, z), ox = pt(d, xIzq - 1, yArriba, z), oy = pt(d, xIzq, yArriba - 1, z);
        float rx = (float) ((ox[0] - o[0]) * porPx), ry = (float) ((ox[1] - o[1]) * porPx), rz = (float) ((ox[2] - o[2]) * porPx);
        float dx = (float) ((oy[0] - o[0]) * porPx), dy = (float) ((oy[1] - o[1]) * porPx), dz = (float) ((oy[2] - o[2]) * porPx);
        float k = (float) (porPx / 16.0);
        Matrix4f t = new Matrix4f(ps.last().pose());
        t.mul(new Matrix4f(rx, ry, rz, 0, dx, dy, dz, 0,
                -d.getStepX() * k, -d.getStepY() * k, -d.getStepZ() * k, 0,   // z del texto hacia dentro: el fondo queda detrás de las letras
                (float) (o[0] + d.getStepX() * 0.004), (float) (o[1] + d.getStepY() * 0.004), (float) (o[2] + d.getStepZ() * 0.004), 1));
        String nombre = ef > 0 ? CamarasCliente.nombre(ef) : "";
        String cab = ef > 0 ? String.format("%02d", ef) : canal == 0 ? "AUTO" : String.format("%02d", canal);
        int maxNombre = Math.max(0, (int) ((wt - 46) / 6));
        if (!nombre.isEmpty() && maxNombre > 2)
            cab += " " + (nombre.length() > maxNombre ? nombre.substring(0, maxNombre) : nombre).toUpperCase(java.util.Locale.ROOT);
        f.drawInBatch(Component.literal(cab).withStyle(ChatFormatting.BOLD), 2, 1, 0xFFFFFFFF, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
        if (senal) {
            boolean punto = (ahora / 500) % 2 == 0;
            f.drawInBatch(Component.literal("●").withStyle(ChatFormatting.BOLD), wt - 26, 1, punto ? 0xFFFF3030 : 0xFF601010, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
            f.drawInBatch(Component.literal("REC").withStyle(ChatFormatting.BOLD), wt - 17, 1, 0xFFFFFFFF, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
        } else {
            Component s = Component.literal(cambiando ? "CAMBIANDO..." : "SIN SEÑAL").withStyle(ChatFormatting.BOLD);
            f.drawInBatch(s, (wt - f.width(s)) / 2.0F, ht / 2 - 4.5F, 0xFFFFFFFF, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
        }
        LocalTime h = LocalTime.now();
        f.drawInBatch(Component.literal(String.format("%02d:%02d:%02d", h.getHour(), h.getMinute(), h.getSecond())), 2, ht - 10.5F, 0xFFFFFFFF, false, t, buf,
                Font.DisplayMode.NORMAL, FONDO, LUZ);
    }

    /** Punto del modelo mirando al norte (en píxeles) puesto en el bloque, en bloques, un pelín por delante. */
    private static double[] pt(Direction d, double x, double y, double z) {
        double[] r = com.miscelanea.camaras.Montaje.mapa(d, x, y, z - 0.02);
        return new double[]{r[0] / 16.0, r[1] / 16.0, r[2] / 16.0};
    }
}
