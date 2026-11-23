package com.miscelanea.client.camaras;

import com.miscelanea.camaras.Montaje;
import com.miscelanea.camaras.TeleBlock;
import com.miscelanea.camaras.TeleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.time.LocalTime;

/**
 * Dibuja en la pantalla de la tele el último fotograma del canal que enseña (o estática si no llega señal), con el nombre
 * del canal, un punto rojo parpadeante y la hora, como las cámaras de vigilancia. Al dibujarse avisa de que alguien mira ese
 * canal, que es lo que hace que el servidor pida a alguien que transmita.
 */
public class TeleRenderer implements BlockEntityRenderer<TeleBlockEntity> {
    private static final int LUZ = 0xF000F0;

    public TeleRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return 40;
    }

    @Override
    public void render(TeleBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Direction d = be.getBlockState().getValue(Montaje.FACING);
        int canal = be.canal();
        CamarasCliente.visto(canal);
        CamarasCliente.pedirLista();      // para el nombre de la cámara en el rótulo
        int ef = CamarasCliente.efectivo(canal);
        // al cambiar de canal, medio segundo de estática (como una tele de verdad)
        long ahora = Util.getMillis();
        net.minecraft.core.BlockPos pos = be.getBlockPos();
        long[] ult = CAMBIOS.computeIfAbsent(pos, k -> new long[]{canal, 0});
        if (ult[0] != canal) {
            ult[0] = canal;
            ult[1] = ahora;
            if (CAMBIOS.size() > 64) CAMBIOS.clear();
        }
        boolean cambiando = ahora - ult[1] < 450;
        boolean senal = ef > 0 && CamarasCliente.hayEnvio(ef) && !cambiando;
        ResourceLocation rl = senal ? CamarasCliente.senal(ef).rl : CamarasCliente.estatica();

        // la imagen
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(rl));
        double[][] p = {pt(d, TeleBlock.PX0, TeleBlock.PY0), pt(d, TeleBlock.PX1, TeleBlock.PY0), pt(d, TeleBlock.PX1, TeleBlock.PY1), pt(d, TeleBlock.PX0, TeleBlock.PY1)};
        // visto de frente (el espectador a la altura del frente), la x del modelo crece hacia la izquierda; el orden de abajo
        // pone u = 0 en el lado izquierdo del espectador y v = 0 arriba
        float[][] uv = {{1, 1}, {0, 1}, {0, 0}, {1, 0}};
        var m = ps.last().pose();
        var n = ps.last().normal();
        for (int i = 0; i < 4; i++)
            vc.vertex(m, (float) p[i][0], (float) p[i][1], (float) p[i][2]).color(senal ? 205 : 255, senal ? 245 : 255, senal ? 225 : 255, 255).uv(uv[i][0], uv[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LUZ).normal(n, d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();

        // los textos, en el plano de la pantalla: 56 "píxeles de texto" de ancho (x hacia la derecha del espectador, y hacia abajo)
        Font f = Minecraft.getInstance().font;
        double[] o = pt(d, TeleBlock.PX1, TeleBlock.PY1);                       // esquina superior izquierda del espectador
        double[] ox = pt(d, TeleBlock.PX1 - 1, TeleBlock.PY1), oy = pt(d, TeleBlock.PX1, TeleBlock.PY1 - 1);
        double porPx = (TeleBlock.PX1 - TeleBlock.PX0) / ANCHO;                 // píxeles del modelo por píxel de texto
        float rx = (float) ((ox[0] - o[0]) * porPx), ry = (float) ((ox[1] - o[1]) * porPx), rz = (float) ((ox[2] - o[2]) * porPx);
        float dx = (float) ((oy[0] - o[0]) * porPx), dy = (float) ((oy[1] - o[1]) * porPx), dz = (float) ((oy[2] - o[2]) * porPx);
        float k = (float) (porPx / 16.0);
        Matrix4f t = new Matrix4f(ps.last().pose());
        t.mul(new Matrix4f(rx, ry, rz, 0, dx, dy, dz, 0, -d.getStepX() * k, -d.getStepY() * k, -d.getStepZ() * k, 0,   // z del texto hacia dentro (como los rótulos de nombre): el fondo queda detrás de las letras
                (float) (o[0] + d.getStepX() * 0.004), (float) (o[1] + d.getStepY() * 0.004), (float) (o[2] + d.getStepZ() * 0.004), 1));   // un pelín por delante de la imagen
        String nombre = ef > 0 ? CamarasCliente.nombre(ef) : "";
        String cab = ef > 0 ? String.format("%02d", ef) : canal == 0 ? "AUTO" : String.format("%02d", canal);
        if (!nombre.isEmpty()) cab += " " + (nombre.length() > 9 ? nombre.substring(0, 9) : nombre).toUpperCase(java.util.Locale.ROOT);
        f.drawInBatch(Component.literal(cab).withStyle(ChatFormatting.BOLD), 2, 1, 0xFFFFFFFF, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
        if (senal) {
            boolean punto = (ahora / 500) % 2 == 0;
            f.drawInBatch(Component.literal("●").withStyle(ChatFormatting.BOLD), ANCHO - 26, 1, punto ? 0xFFFF3030 : 0xFF601010, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
            f.drawInBatch(Component.literal("REC").withStyle(ChatFormatting.BOLD), ANCHO - 17, 1, 0xFFFFFFFF, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
        } else {
            Component s = Component.literal(cambiando ? "CAMBIANDO..." : "SIN SEÑAL").withStyle(ChatFormatting.BOLD);
            f.drawInBatch(s, (ANCHO - f.width(s)) / 2.0F, 11, 0xFFFFFFFF, false, t, buf, Font.DisplayMode.NORMAL, FONDO, LUZ);
        }
        LocalTime h = LocalTime.now();
        f.drawInBatch(Component.literal(String.format("%02d:%02d:%02d", h.getHour(), h.getMinute(), h.getSecond())), 2, 21, 0xFFFFFFFF, false, t, buf,
                Font.DisplayMode.NORMAL, FONDO, LUZ);
    }

    private static final float ANCHO = 56;
    private static final int FONDO = 0x99000000;
    private static final java.util.Map<net.minecraft.core.BlockPos, long[]> CAMBIOS = new java.util.HashMap<>();

    /** Punto de la pantalla (x, y del modelo mirando al norte) puesto en el bloque, en bloques (0..1), un pelín delante. */
    private static double[] pt(Direction d, double x, double y) {
        double[] r = com.miscelanea.camaras.Montaje.mapa(d, x, y, TeleBlock.PZ - 0.02);
        return new double[]{r[0] / 16.0, r[1] / 16.0, r[2] / 16.0};
    }
}
