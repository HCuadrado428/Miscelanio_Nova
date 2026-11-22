package com.miscelanea.client.camaras;

import com.miscelanea.camaras.Montaje;
import com.miscelanea.camaras.TeleBlock;
import com.miscelanea.camaras.TeleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
        int ef = CamarasCliente.efectivo(canal);
        boolean senal = ef > 0 && CamarasCliente.hayEnvio(ef);
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
            vc.vertex(m, (float) p[i][0], (float) p[i][1], (float) p[i][2]).color(255, 255, 255, 255).uv(uv[i][0], uv[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LUZ).normal(n, d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();

        // los textos, en el plano de la pantalla (x hacia la derecha del espectador, y hacia abajo)
        Font f = Minecraft.getInstance().font;
        double[] o = pt(d, TeleBlock.PX1, TeleBlock.PY1 + 0.001);          // esquina superior izquierda del espectador
        double[] ox = pt(d, TeleBlock.PX1 - 1, TeleBlock.PY1 + 0.001), oy = pt(d, TeleBlock.PX1, TeleBlock.PY1 - 1 + 0.001);
        // "arriba" del modelo es +y en el plano: la esquina superior del espectador es y = PY1 (más alto) y la imagen va hacia abajo
        float esc = (float) ((TeleBlock.PX1 - TeleBlock.PX0) / 16.0 / 64.0);
        float rx = (float) (ox[0] - o[0]), ry = (float) (ox[1] - o[1]), rz = (float) (ox[2] - o[2]);   // derecha (por pixel del modelo)
        float dx = (float) (oy[0] - o[0]), dy = (float) (oy[1] - o[1]), dz = (float) (oy[2] - o[2]);   // abajo (por pixel del modelo)
        float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
        Matrix4f t = new Matrix4f(ps.last().pose());
        t.mul(new Matrix4f(rx * esc * 16, ry * esc * 16, rz * esc * 16, 0, dx * esc * 16, dy * esc * 16, dz * esc * 16, 0,
                nx * esc, ny * esc, nz * esc, 0, (float) o[0], (float) o[1], (float) o[2], 1));
        String nombre = ef > 0 ? String.format("CAM %02d", ef) : canal == 0 ? "AUTO" : String.format("CAM %02d", canal);
        f.drawInBatch(Component.literal(nombre), 3, 3, 0xFFEFEFEF, false, t, buf, Font.DisplayMode.NORMAL, 0, LUZ);
        if ((Util.getMillis() / 500) % 2 == 0 && senal)
            f.drawInBatch(Component.literal("●"), 50, 3, 0xFFFF3030, false, t, buf, Font.DisplayMode.NORMAL, 0, LUZ);
        if (!senal) {
            String s = "SIN SEÑAL";
            f.drawInBatch(Component.literal(s), 32 - f.width(s) / 2.0F, 16, 0xFFFFFFFF, true, t, buf, Font.DisplayMode.NORMAL, 0, LUZ);
        }
        LocalTime h = LocalTime.now();
        f.drawInBatch(Component.literal(String.format("%02d:%02d:%02d", h.getHour(), h.getMinute(), h.getSecond())), 3, 29, 0xFFEFEFEF, false, t, buf,
                Font.DisplayMode.NORMAL, 0, LUZ);
    }

    /** Punto de la pantalla (x, y del modelo mirando al norte) puesto en el bloque, en bloques (0..1), un pelín delante. */
    private static double[] pt(Direction d, double x, double y) {
        double[] r = com.miscelanea.camaras.Montaje.mapa(d, x, y, TeleBlock.PZ - 0.02);
        return new double[]{r[0] / 16.0, r[1] / 16.0, r[2] / 16.0};
    }
}
