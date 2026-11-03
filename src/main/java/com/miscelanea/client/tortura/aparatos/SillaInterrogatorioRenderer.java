package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import static com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity.MON_MX;
import static com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity.MON_MY;
import static com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity.MON_X;
import static com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity.MON_Y;
import static com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity.MON_Z;

/**
 * El monitor cardíaco de la mesita: la traza verde (con brillo aditivo, como un fósforo) que corre por la pantalla y las
 * pulsaciones; con el corazón parado, la línea plana en rojo y "PARO" parpadeando. La pantalla (vidrio oscuro) es parte
 * del modelo del bloque; aquí se dibuja justo delante, en px del modelo (mirando al norte, la pantalla da a -z).
 */
public class SillaInterrogatorioRenderer implements BlockEntityRenderer<SillaInterrogatorioBlockEntity> {
    private static final int PUNTOS = 44;

    public SillaInterrogatorioRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(SillaInterrogatorioBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        boolean vivo = be.ocupado();
        boolean paro = be.paroCli > 0;
        float bpm = Mth.lerp(pt, be.bpmVisO, be.bpmVis);
        int col = vivo ? MonitorEcg.color(bpm, paro) : 0xFF2A6A40;
        float ahora = be.reloj + pt;
        ps.pushPose();
        ModelosAparatos.orientar(ps, be.frente());
        ps.scale(1F / 16F, 1F / 16F, 1F / 16F);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        float z = MON_Z - 0.06F;
        float x0 = MON_X + MON_MX * 0.94F, ancho = MON_MX * 2F * 0.94F;
        // rejilla tenue
        for (int i = 1; i < 6; i++) {
            float x = x0 - ancho * i / 6F;
            quad(vc, m, x - 0.02F, MON_Y - MON_MY * 0.9F, x + 0.02F, MON_Y + MON_MY * 0.9F, z, 0x1830A050);
        }
        for (int i = 1; i < 4; i++) {
            float y = MON_Y - MON_MY * 0.9F + MON_MY * 1.8F * i / 4F;
            quad(vc, m, MON_X - MON_MX * 0.94F, y - 0.02F, MON_X + MON_MX * 0.94F, y + 0.02F, z, 0x1830A050);
        }
        // traza: lo más viejo a la izquierda de quien mira (x+ del modelo) y lo nuevo a la derecha
        float base = MON_Y - MON_MY * 0.42F, amp = MON_MY * 1.05F;
        float px = 0, py = 0;
        for (int i = 0; i <= PUNTOS; i++) {
            float s = i / (float) PUNTOS;
            float t = ahora - (1F - s) * MonitorEcg.VENTANA;
            float y = !vivo ? 0F : paro ? Mth.sin(t * 9.1F) * 0.012F : MonitorEcg.valor(be, t);
            float sx = x0 - ancho * s, sy = base + Mth.clamp(y, -0.45F, 1.15F) * amp;
            if (i > 0) {
                linea(vc, m, px, py, sx, sy, 0.34F, z, (col & 0xFFFFFF) | 0x40000000);
                linea(vc, m, px, py, sx, sy, 0.13F, z - 0.01F, col);
            }
            px = sx;
            py = sy;
        }
        if (vivo) quad(vc, m, px - 0.18F, py - 0.18F, px + 0.18F, py + 0.18F, z - 0.02F, 0xFFE8FFF0);
        ps.popPose();
        // pulsaciones (arriba, a la derecha de quien mira)
        if (vivo) {
            Font f = Minecraft.getInstance().font;
            String txt = paro ? ((be.reloj / 5) % 2 == 0 ? "PARO" : "") : String.valueOf(Math.round(bpm));
            ps.pushPose();
            ModelosAparatos.orientar(ps, be.frente());
            ps.translate((MON_X - MON_MX * 0.5F) / 16F, (MON_Y + MON_MY * 0.62F) / 16F, (MON_Z - 0.09F) / 16F);
            float k = 1F / 16F / 6.5F;
            ps.scale(-k, -k, -k);
            if (!txt.isEmpty())
                f.drawInBatch(txt, -f.width(txt) / 2F, -4, col, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
            String lpm = "LPM";
            ps.scale(0.55F, 0.55F, 0.55F);
            f.drawInBatch(lpm, f.width(txt) * 0.95F + 2, 0, (col & 0xFFFFFF) | 0xC0000000, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0,
                    LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }

    /** Segmento grueso en el plano z (las dos caras, por si se mira de lado). */
    private static void linea(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float g, float z, int c) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1.0E-4F) return;
        float nx = -dy / l * g / 2, ny = dx / l * g / 2;
        // se alarga un poco para que los tramos empalmen
        float ex = dx / l * g * 0.4F, ey = dy / l * g * 0.4F;
        cuatro(vc, m, x0 + nx - ex, y0 + ny - ey, x1 + nx + ex, y1 + ny + ey, x1 - nx + ex, y1 - ny + ey, x0 - nx - ex, y0 - ny - ey, z, c);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int c) {
        cuatro(vc, m, x0, y0, x1, y0, x1, y1, x0, y1, z, c);
    }

    private static void cuatro(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float z, int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, a = c >>> 24;
        vc.vertex(m, ax, ay, z).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, z).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, z).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, z).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, z).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, z).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, z).color(r, g, b, a).endVertex();
        vc.vertex(m, ax, ay, z).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(SillaInterrogatorioBlockEntity be) {
        return true;
    }
}
