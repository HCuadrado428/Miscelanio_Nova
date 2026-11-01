package com.miscelanea.client.arcade;

import com.miscelanea.arcade.ArcadeJuegos;
import com.miscelanea.arcade.CartelRecordsBlock;
import com.miscelanea.arcade.CartelRecordsBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Matrix4f;

/**
 * Dibuja el cartel de récords. Cada grupo rectangular de carteles lo dibuja su esquina de arriba a la izquierda: marco de
 * neón con bombillas, un panel por juego (más paneles cuanto más grande es el cartel) y van rotando los juegos.
 */
public class CartelRecordsRenderer implements BlockEntityRenderer<CartelRecordsBlockEntity> {
    private static final int LUZ = 0xF000F0;
    private static final float S = 1 / 64F;

    public CartelRecordsRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public boolean shouldRenderOffScreen(CartelRecordsBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public void render(CartelRecordsBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        Direction f = be.getBlockState().getValue(CartelRecordsBlock.FACING);
        BlockPos pos = be.getBlockPos();
        int w = 1, h = 1;
        Object[] g = CartelRecordsBlock.grupo(be.getLevel(), pos, f);
        if (g != null) {
            if (!g[0].equals(pos)) return;      // lo dibuja la esquina del grupo
            w = (int) g[1];
            h = (int) g[2];
        }
        Direction izq = f.getClockWise(), der = f.getCounterClockWise();
        float nx = f.getStepX(), nz = f.getStepZ();
        float cx = 0.5F + izq.getStepX() * 0.5F - nx * 0.37F, cy = 1F, cz = 0.5F + izq.getStepZ() * 0.5F - nz * 0.37F;
        ps.pushPose();
        Matrix4f m = new Matrix4f(der.getStepX() * S, 0, der.getStepZ() * S, 0, 0, -S, 0, 0, nx * S, 0, nz * S, 0, cx, cy, cz, 1);
        ps.last().pose().mul(m);
        dibujar(ps, buf, w * 64, h * 64, w, h);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(LUZ).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(LUZ).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(LUZ).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(LUZ).endVertex();
    }

    private void dibujar(PoseStack ps, MultiBufferSource buf, int W, int H, int w, int h) {
        Font font = Minecraft.getInstance().font;
        long ms = System.currentTimeMillis();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        // fondo con degradado por bandas y marco de neón
        for (int k = 0; k < 8; k++) {
            int c = mezcla(0xFF140C2A, 0xFF2A1250, k / 7F);
            quad(vc, m, 0, H * k / 8F, W, H * (k + 1) / 8F, 0, c);
        }
        quad(vc, m, 0, 0, W, 2, 0.1F, 0xFFEC6CA8);
        quad(vc, m, 0, H - 2, W, H, 0.1F, 0xFFEC6CA8);
        quad(vc, m, 0, 0, 2, H, 0.1F, 0xFFEC6CA8);
        quad(vc, m, W - 2, 0, W, H, 0.1F, 0xFFEC6CA8);
        quad(vc, m, 3, 3, W - 3, 4, 0.1F, 0xFF5ADCFF);
        quad(vc, m, 3, H - 4, W - 3, H - 3, 0.1F, 0xFF5ADCFF);
        quad(vc, m, 3, 3, 4, H - 3, 0.1F, 0xFF5ADCFF);
        quad(vc, m, W - 4, 3, W - 3, H - 3, 0.1F, 0xFF5ADCFF);
        // bombillas que se encienden en carrusel
        int paso = (int) (ms / 180);
        int k = 0;
        for (int x = 6; x < W - 4; x += 8, k++) {
            int c = (k + paso) % 3 == 0 ? 0xFFFFE070 : 0xFF6A5020;
            quad(vc, m, x, 0.5F, x + 1.5F, 1.5F, 0.2F, c);
            quad(vc, m, W - x - 1.5F, H - 1.5F, W - x, H - 0.5F, 0.2F, c);
        }
        int y0 = 6;
        // cabecera en carteles de dos o más de alto
        if (h >= 2) {
            float kk = Math.min(2.2F, Math.max(1F, w * 0.5F));
            String tit = I18n.get("cartel.miscelanea.titulo");
            float alto = 12 * kk;
            quad(vc, m, 6, y0, W - 6, y0 + alto, 0.2F, 0xFF3E1370);
            texto(ps, buf, font, tit, W / 2F, y0 + 2 * kk, kk, (ms / 400) % 2 == 0 ? 0xFFFFE05A : 0xFFFFECA0, true);
            y0 += (int) alto + 3;
        }
        int cols = Math.max(1, w / 2), filas = Math.max(1, h / 2);
        float pw = (W - 12) / (float) cols, ph = (H - y0 - 6) / (float) filas;
        String[] ids = ArcadeJuegos.IDS;
        int pagina = (int) (ms / 8000);
        int total = cols * filas;
        for (int fila = 0; fila < filas; fila++)
            for (int col = 0; col < cols; col++) {
                int idx = Math.floorMod(pagina * total + fila * cols + col, ids.length);
                panel(ps, buf, font, 6 + col * pw, y0 + fila * ph, pw - 3, ph - 3, ids[idx], ms);
            }
    }

    private void panel(PoseStack ps, MultiBufferSource buf, Font font, float x, float y, float pw, float ph, String id, long ms) {
        float kk = Math.min(pw / 64F, ph / 58F) * 0.82F;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        quad(vc, m, x, y, x + pw, y + ph, 0.15F, 0xC0080810);
        float cab = 11 * kk;
        quad(vc, m, x, y, x + pw, y + cab, 0.2F, 0xFF612895);
        quad(vc, m, x, y + cab, x + pw, y + cab + 0.6F * kk, 0.2F, 0xFFEC6CA8);
        String nombre = I18n.get("juego.miscelanea." + id);
        texto(ps, buf, font, nombre, x + pw / 2, y + 1.6F * kk, kk, 0xFFFFE05A, true);
        ListTag tb = ArcadeCliente.tabla(id);
        float yy = y + cab + 3 * kk, linea = 9.5F * kk;
        int max = Math.max(1, Math.min(10, (int) ((y + ph - yy - 2) / linea)));
        if (tb.isEmpty()) {
            texto(ps, buf, font, I18n.get("cartel.miscelanea.vacio"), x + pw / 2, yy + linea * 0.5F, kk * 0.8F, (ms / 500) % 2 == 0 ? 0xFF5ADCFF : 0xFF78BCF0, true);
            return;
        }
        float anchoTxt = pw / kk - 6;
        for (int i = 0; i < Math.min(max, tb.size()); i++) {
            CompoundTag c = tb.getCompound(i);
            int col = i == 0 ? 0xFFFFD23C : i == 1 ? 0xFFD2D2DE : i == 2 ? 0xFFE0904A : 0xFFF0F0F8;
            if (i == 0 && (ms / 300) % 2 == 0) col = 0xFFFFF0A0;
            String pts = Integer.toString(c.getInt("p"));
            String n = (i + 1) + ". " + c.getString("n");
            int wp = font.width(pts);
            n = font.plainSubstrByWidth(n, (int) Math.max(10, anchoTxt - wp - 4));
            if (i < 3) quad(vc, m, x + 1, yy - 0.8F * kk, x + pw - 1, yy + 8.6F * kk, 0.18F, i == 0 ? 0x40FFD23C : 0x20FFFFFF);
            textoIzq(ps, buf, font, n, x + 3 * kk, yy, kk, col);
            textoIzq(ps, buf, font, pts, x + pw - 3 * kk - wp * kk, yy, kk, col);
            yy += linea;
        }
    }

    private static void texto(PoseStack ps, MultiBufferSource buf, Font font, String s, float cx, float y, float k, int col, boolean centrado) {
        float x = centrado ? cx - font.width(s) * k / 2 : cx;
        textoIzq(ps, buf, font, s, x, y, k, col);
    }

    private static void textoIzq(PoseStack ps, MultiBufferSource buf, Font font, String s, float x, float y, float k, int col) {
        ps.pushPose();
        ps.translate(x, y, 0.4F);
        ps.scale(k, k, 1);
        font.drawInBatch(s, 0, 0, col, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, LUZ);
        ps.popPose();
    }

    private static int mezcla(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bb;
    }
}
