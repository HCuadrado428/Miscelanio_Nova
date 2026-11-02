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
        if (be.getBlockState().getBlock() instanceof CartelRecordsBlock cb && cb.estilo == CartelRecordsBlock.FERIA) pizarra(ps, buf, w * 64, h * 64, w, h);
        else retro(ps, buf, w * 64, h * 64, w, h);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(LUZ).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(LUZ).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(LUZ).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(LUZ).endVertex();
    }

    // ------------------------------------------------------------------ marcador retro de recreativa
    static final int[] ARCOIRIS = {0xFFFF3B3B, 0xFFFF9A2E, 0xFFFFE93B, 0xFF4BFF5A, 0xFF3BE6FF, 0xFF4B7BFF, 0xFFD04BFF};

    private void retro(PoseStack ps, MultiBufferSource buf, int W, int H, int w, int h) {
        Font font = Minecraft.getInstance().font;
        long ms = System.currentTimeMillis();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        // carcasa de plástico gris oscuro y pantalla negra con esquinas redondeadas
        quad(vc, m, 0, 0, W, H, 0, 0xFF2A2A30);
        quad(vc, m, 1, 1, W - 1, 2, 0.05F, 0xFF4A4A54);
        quad(vc, m, 1, 1, 2, H - 1, 0.05F, 0xFF4A4A54);
        quad(vc, m, 1, H - 2, W - 1, H - 1, 0.05F, 0xFF18181C);
        quad(vc, m, W - 2, 1, W - 1, H - 1, 0.05F, 0xFF18181C);
        quad(vc, m, 5, 4, W - 5, H - 4, 0.1F, 0xFF04060A);
        quad(vc, m, 4, 5, W - 4, H - 5, 0.1F, 0xFF04060A);
        // líneas de barrido y un brillo que baja
        for (float y = 5; y < H - 5; y += 2) quad(vc, m, 5, y, W - 5, y + 0.6F, 0.12F, 0x14FFFFFF);
        float barrido = (ms / 30F) % (H + 20) - 10;
        quad(vc, m, 5, Math.max(5, barrido), W - 5, Math.min(H - 5, barrido + 6), 0.13F, 0x0C9FFFC8);
        int pagina = (int) (ms / 8000);
        boolean ruido = ms % 8000 < 220;
        int y0 = 8;
        if (h >= 2) {
            float kk = Math.min(2.4F, Math.max(1.1F, w * 0.55F));
            String tit = I18n.get("cartel.miscelanea.titulo_arcade");
            float x = W / 2F - font.width(tit) * kk / 2;
            for (int i = 0; i < tit.length(); i++) {
                String ch = String.valueOf(tit.charAt(i));
                int col = ARCOIRIS[Math.floorMod(i - (int) (ms / 90), ARCOIRIS.length)];
                textoIzq(ps, buf, font, ch, x, y0, kk, col);
                x += font.width(ch) * kk;
            }
            y0 += (int) (11 * kk) + 2;
        }
        int pie = h >= 2 ? 12 : 0;
        int cols = Math.max(1, w / 2), filas = Math.max(1, h / 2);
        float pw = (W - 14) / (float) cols, ph = (H - y0 - 6 - pie) / (float) filas;
        String[] ids = com.miscelanea.arcade.ArcadeJuegos.IDS;
        int total = cols * filas;
        for (int fila = 0; fila < filas; fila++)
            for (int col = 0; col < cols; col++) {
                int idx = Math.floorMod(pagina * total + fila * cols + col, ids.length);
                panelRetro(ps, buf, font, 7 + col * pw, y0 + fila * ph, pw - 3, ph - 3, ids[idx], ms);
            }
        if (pie > 0 && (ms / 600) % 2 == 0)
            texto(ps, buf, font, I18n.get("cartel.miscelanea.moneda"), W / 2F, H - 15, 1F, 0xFFFFFFFF, true);
        if (ruido) {
            java.util.Random r = new java.util.Random(ms / 40);
            for (int k = 0; k < 60; k++) {
                float x = 5 + r.nextFloat() * (W - 12), y = 5 + r.nextFloat() * (H - 12);
                quad(vc, m, x, y, x + 1 + r.nextFloat() * 6, y + 1, 0.5F, r.nextBoolean() ? 0xA0FFFFFF : 0xA0404060);
            }
        }
    }

    private void panelRetro(PoseStack ps, MultiBufferSource buf, Font font, float x, float y, float pw, float ph, String id, long ms) {
        float kk = Math.min(pw / 70F, ph / 58F) * 0.85F;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        String nombre = I18n.get("juego.miscelanea." + id).toUpperCase();
        texto(ps, buf, font, nombre, x + pw / 2, y + 1, kk, 0xFF3BE6FF, true);
        for (float dx = 2; dx < pw - 2; dx += 3 * kk) quad(vc, m, x + dx, y + 10 * kk, x + dx + 1.5F * kk, y + 10.6F * kk, 0.2F, 0xFF3B7BFF);
        ListTag tb = ArcadeCliente.tabla(id);
        float yy = y + 13 * kk, linea = 9.5F * kk;
        int max = Math.max(1, Math.min(10, (int) ((y + ph - yy - 2) / linea)));
        if (tb.isEmpty()) {
            if ((ms / 500) % 2 == 0) texto(ps, buf, font, I18n.get("cartel.miscelanea.vacio_arcade"), x + pw / 2, yy + linea * 0.5F, kk * 0.8F, 0xFFFFE93B, true);
            return;
        }
        String[] puesto = {"1ST", "2ND", "3RD", "4TH", "5TH", "6TH", "7TH", "8TH", "9TH", "10TH"};
        float anchoTxt = pw / kk - 6;
        for (int i = 0; i < Math.min(max, tb.size()); i++) {
            CompoundTag c = tb.getCompound(i);
            int col = ARCOIRIS[i % ARCOIRIS.length];
            if (i == 0 && (ms / 250) % 2 == 0) col = 0xFFFFFFFF;
            String pts = String.format("%07d", c.getInt("p"));
            int wp = font.width(pts), wr = font.width("10TH ");
            String n = c.getString("n").toUpperCase();
            n = font.plainSubstrByWidth(n, (int) Math.max(10, anchoTxt - wp - wr - 4));
            textoIzq(ps, buf, font, puesto[i], x + 2 * kk, yy, kk, col);
            textoIzq(ps, buf, font, n, x + 2 * kk + wr * kk, yy, kk, col);
            textoIzq(ps, buf, font, pts, x + pw - 2 * kk - wp * kk, yy, kk, col);
            yy += linea;
        }
    }

    // ------------------------------------------------------------------ pizarra de tiza de la feria
    static final int[] TIZAS = {0xFFFFE97A, 0xFFFF9CC8, 0xFF9CDCFF, 0xFFB8F59A, 0xFFFFB27A, 0xFFE0C0FF};

    private void pizarra(PoseStack ps, MultiBufferSource buf, int W, int H, int w, int h) {
        Font font = Minecraft.getInstance().font;
        long ms = System.currentTimeMillis();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        // marco de madera con vetas
        quad(vc, m, 0, 0, W, H, 0, 0xFF7A4A24);
        for (int k = 0; k < 6; k++) {
            float y = 1 + k * 0.7F;
            quad(vc, m, 0, y, W, y + 0.25F, 0.02F, 0xFF5E3618);
            quad(vc, m, 0, H - y - 0.25F, W, H - y, 0.02F, 0xFF5E3618);
        }
        quad(vc, m, 0, 0, W, 0.8F, 0.03F, 0xFF9A6434);
        // pizarra verde con manchas de borrador
        quad(vc, m, 5, 5, W - 5, H - 7, 0.1F, 0xFF1F3A2C);
        java.util.Random r = new java.util.Random(W * 31L + H);
        for (int k = 0; k < (w * h) * 6; k++) {
            float x = 6 + r.nextFloat() * (W - 22), y = 6 + r.nextFloat() * (H - 24), sw = 6 + r.nextFloat() * 14;
            quad(vc, m, x, y, x + sw, y + 2 + r.nextFloat() * 4, 0.11F, 0x0EFFFFFF);
        }
        // repisa con tizas y borrador
        quad(vc, m, 3, H - 7, W - 3, H - 4, 0.15F, 0xFF8A5530);
        quad(vc, m, 3, H - 4, W - 3, H - 3.2F, 0.15F, 0xFF5E3618);
        for (int k = 0; k < Math.min(5, w + 1); k++) quad(vc, m, 8 + k * 7, H - 8.6F, 13 + k * 7, H - 7, 0.2F, TIZAS[k % TIZAS.length] | 0xFF000000);
        quad(vc, m, W - 20, H - 9.5F, W - 9, H - 7, 0.2F, 0xFF3A3A44);
        quad(vc, m, W - 20, H - 10.5F, W - 9, H - 9.5F, 0.2F, 0xFFD8C090);
        // guirnalda de banderines
        int nb = Math.max(4, w * 4);
        float paso = (W - 10) / (float) nb;
        quad(vc, m, 5, 6.2F, W - 5, 6.6F, 0.2F, 0xFFF0E8D0);
        int[] bandera = {0xFFE03030, 0xFFFFD040, 0xFF3070E0, 0xFFF4F4F4, 0xFF40B060};
        for (int k = 0; k < nb; k++) {
            float bx = 5 + k * paso + paso * 0.15F, bw = paso * 0.7F;
            int c = bandera[k % bandera.length];
            for (int s = 0; s < 4; s++) {
                float inset = bw * s / 8F;
                quad(vc, m, bx + inset, 6.6F + s * 1.2F, bx + bw - inset, 6.6F + (s + 1) * 1.2F, 0.21F, c);
            }
        }
        int y0 = 13;
        if (h >= 2) {
            float kk = Math.min(2.2F, Math.max(1F, w * 0.5F));
            String tit = I18n.get("cartel.miscelanea.titulo_feria");
            texto(ps, buf, font, tit, W / 2F, y0, kk, 0xF0FFFFFF, true);
            float tw = font.width(tit) * kk;
            for (float dx = 0; dx < tw; dx += 2) {
                float yy = y0 + 10 * kk + (float) Math.sin(dx * 0.4F) * 0.6F;
                quad(vc, m, W / 2F - tw / 2 + dx, yy, W / 2F - tw / 2 + dx + 2.2F, yy + 0.7F, 0.2F, 0xC0FFFFFF);
            }
            y0 += (int) (12 * kk) + 3;
        }
        int cols = Math.max(1, w / 2), filas = Math.max(1, h / 2);
        float pw = (W - 14) / (float) cols, ph = (H - y0 - 10) / (float) filas;
        String[] ids = com.miscelanea.arcade.ArcadeJuegos.FERIA;
        int total = cols * filas, pagina = (int) (ms / 9000);
        for (int fila = 0; fila < filas; fila++)
            for (int col = 0; col < cols; col++) {
                int idx = Math.floorMod(pagina * total + fila * cols + col, ids.length);
                panelTiza(ps, buf, font, 7 + col * pw, y0 + fila * ph, pw - 3, ph - 3, ids[idx], idx);
            }
    }

    private void panelTiza(PoseStack ps, MultiBufferSource buf, Font font, float x, float y, float pw, float ph, String id, int n) {
        float kk = Math.min(pw / 66F, ph / 56F) * 0.85F;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        int tiza = TIZAS[n % TIZAS.length];
        String nombre = "★ " + I18n.get("juego.miscelanea." + id) + " ★";
        texto(ps, buf, font, nombre, x + pw / 2, y + 1, kk, tiza, true);
        float tw = font.width(nombre) * kk;
        quad(vc, m, x + pw / 2 - tw / 2, y + 10 * kk, x + pw / 2 + tw / 2, y + 10.6F * kk, 0.2F, (tiza & 0x00FFFFFF) | 0xA0000000);
        ListTag tb = ArcadeCliente.tabla(id);
        float yy = y + 13 * kk, linea = 9.5F * kk;
        int max = Math.max(1, Math.min(10, (int) ((y + ph - yy - 2) / linea)));
        String unidad = I18n.get("cartel.miscelanea.unidad." + id);
        if (tb.isEmpty()) {
            texto(ps, buf, font, I18n.get("cartel.miscelanea.vacio_feria"), x + pw / 2, yy + linea * 0.5F, kk * 0.8F, 0xC8FFFFFF, true);
            return;
        }
        float anchoTxt = pw / kk - 6;
        for (int i = 0; i < Math.min(max, tb.size()); i++) {
            CompoundTag c = tb.getCompound(i);
            int col = i == 0 ? 0xFFFFE97A : 0xE6FFFFFF;
            String pts = c.getInt("p") + " " + unidad;
            String nm = (i + 1) + ". " + c.getString("n");
            int wp = font.width(pts);
            nm = font.plainSubstrByWidth(nm, (int) Math.max(10, anchoTxt - wp - 4));
            textoIzq(ps, buf, font, nm, x + 3 * kk, yy, kk, col);
            textoIzq(ps, buf, font, pts, x + pw - 3 * kk - wp * kk, yy, kk, col);
            if (i == 0) {
                // subrayado de tiza bajo el ganador
                for (float dx = 0; dx < pw - 6 * kk; dx += 2)
                    quad(vc, m, x + 3 * kk + dx, yy + 8.4F * kk + (float) Math.sin(dx * 0.5F) * 0.4F, x + 3 * kk + dx + 2.2F, yy + 9F * kk + (float) Math.sin(dx * 0.5F) * 0.4F, 0.19F, 0xB0FFE97A);
            }
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
