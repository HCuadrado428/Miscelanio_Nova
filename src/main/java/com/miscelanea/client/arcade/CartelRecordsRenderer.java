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
    /** Separación entre capas (para que no parpadeen al verlas de lejos). */
    private static final float CAPAS = 4F;

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
        if (be.getLevel() == null || !be.getBlockState().hasProperty(CartelRecordsBlock.FACING)) return;
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
        // plano del dibujo: justo delante de la cara del modelo (cartel retro: 14,2 px; pizarra: panel de tiza a 13,8 px)
        boolean feria = be.getBlockState().getBlock() instanceof CartelRecordsBlock cb0 && cb0.estilo == CartelRecordsBlock.FERIA;
        float fondo = feria ? 14F / 16F - 0.5F - 0.004F : 0.37F;
        float cx = 0.5F + izq.getStepX() * 0.5F - nx * fondo, cy = 1F, cz = 0.5F + izq.getStepZ() * 0.5F - nz * fondo;
        ps.pushPose();
        Matrix4f m = new Matrix4f(der.getStepX() * S, 0, der.getStepZ() * S, 0, 0, -S, 0, 0, nx * S, 0, nz * S, 0, cx, cy, cz, 1);
        ps.last().pose().mul(m);
        int estilo = be.getBlockState().getBlock() instanceof CartelRecordsBlock cb ? cb.estilo : CartelRecordsBlock.ARCADE;
        if (estilo == CartelRecordsBlock.FERIA) pizarra(ps, buf, w * 64, h * 64, w, h, luz, nx, nz);
        else if (estilo == CartelRecordsBlock.TABLON) tablon(ps, buf, w * 64, h * 64, w, h, be.categoria, luz);
        else retro(ps, buf, w * 64, h * 64, w, h);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        z *= CAPAS;
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
        quad(buf.getBuffer(RenderType.textBackground()), m, 0, 0, W, H, 0, 0xFF2A2A30);
        quad(buf.getBuffer(RenderType.textBackground()), m, 1, 1, W - 1, 2, 0.05F, 0xFF4A4A54);
        quad(buf.getBuffer(RenderType.textBackground()), m, 1, 1, 2, H - 1, 0.05F, 0xFF4A4A54);
        quad(buf.getBuffer(RenderType.textBackground()), m, 1, H - 2, W - 1, H - 1, 0.05F, 0xFF18181C);
        quad(buf.getBuffer(RenderType.textBackground()), m, W - 2, 1, W - 1, H - 1, 0.05F, 0xFF18181C);
        quad(buf.getBuffer(RenderType.textBackground()), m, 5, 4, W - 5, H - 4, 0.1F, 0xFF04060A);
        quad(buf.getBuffer(RenderType.textBackground()), m, 4, 5, W - 4, H - 5, 0.1F, 0xFF04060A);
        // líneas de barrido y un brillo que baja
        for (float y = 5; y < H - 5; y += 2) quad(buf.getBuffer(RenderType.textBackground()), m, 5, y, W - 5, y + 0.6F, 0.12F, 0x14FFFFFF);
        float barrido = (ms / 30F) % (H + 20) - 10;
        quad(buf.getBuffer(RenderType.textBackground()), m, 5, Math.max(5, barrido), W - 5, Math.min(H - 5, barrido + 6), 0.13F, 0x0C9FFFC8);
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
                quad(buf.getBuffer(RenderType.textBackground()), m, x, y, x + 1 + r.nextFloat() * 6, y + 1, 0.5F, r.nextBoolean() ? 0xA0FFFFFF : 0xA0404060);
            }
        }
    }

    private void panelRetro(PoseStack ps, MultiBufferSource buf, Font font, float x, float y, float pw, float ph, String id, long ms) {
        float kk = Math.min(pw / 70F, ph / 58F) * 0.85F;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        String nombre = I18n.get("juego.miscelanea." + id).toUpperCase();
        texto(ps, buf, font, nombre, x + pw / 2, y + 1, kk, 0xFF3BE6FF, true);
        for (float dx = 2; dx < pw - 2; dx += 3 * kk) quad(buf.getBuffer(RenderType.textBackground()), m, x + dx, y + 10 * kk, x + dx + 1.5F * kk, y + 10.6F * kk, 0.2F, 0xFF3B7BFF);
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
    private static final int TIZA_BLANCA = 0xFFF2EFE4;

    /** Pizarra: el dibujo es pixel art a 32 px por bloque ({@link PizarraTextura}); encima, la tiza con la luz del sitio. */
    private void pizarra(PoseStack ps, MultiBufferSource buf, int W, int H, int w, int h, int luzSitio, float nx, float nz) {
        Font font = Minecraft.getInstance().font;
        long ms = System.currentTimeMillis();
        Matrix4f m = ps.last().pose();
        // un poco de luz siempre, para que la tiza se lea de noche
        int luz = net.minecraft.client.renderer.LightTexture.pack(Math.max(5, net.minecraft.client.renderer.LightTexture.block(luzSitio)),
                net.minecraft.client.renderer.LightTexture.sky(luzSitio));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PizarraTextura.de(w, h)));
        var nm = ps.last().normal();
        float z = 0;
        vc.vertex(m, 0, 0, z).color(255, 255, 255, 255).uv(0, 0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(luz).normal(nm, nx, 0, nz).endVertex();
        vc.vertex(m, 0, H, z).color(255, 255, 255, 255).uv(0, 1).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(luz).normal(nm, nx, 0, nz).endVertex();
        vc.vertex(m, W, H, z).color(255, 255, 255, 255).uv(1, 1).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(luz).normal(nm, nx, 0, nz).endVertex();
        vc.vertex(m, W, 0, z).color(255, 255, 255, 255).uv(1, 0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(luz).normal(nm, nx, 0, nz).endVertex();
        // zona donde se escribe (en unidades de 1/64 de bloque: 1 píxel de la textura = 2)
        float arriba = 2 * (PizarraTextura.MARCO + PizarraTextura.GUIRNALDA) + 1, abajo = H - 2 * (PizarraTextura.MARCO + PizarraTextura.REPISA) - 4;
        float izq = 2 * PizarraTextura.MARCO + 3, der = W - 2 * PizarraTextura.MARCO - 3;
        float y0 = arriba;
        if (h >= 2) {
            String tit = I18n.get("cartel.miscelanea.titulo_feria").toUpperCase();
            float kk = Math.min(2F, (der - izq) / Math.max(1, font.width(tit)));
            kk = Math.max(1F, (float) Math.floor(kk * 2) / 2F);        // a medios píxeles, para que no se emborrone
            float tx = (W - font.width(tit) * kk) / 2;
            textoL(ps, buf, font, tit, tx, y0, kk, 0.4F, TIZA_BLANCA, luz);
            // subrayado de tiza a trozos, en la rejilla de píxeles
            float tw = font.width(tit) * kk;
            for (float dx = 0; dx < tw; dx += 4)
                if (((int) dx / 4) % 5 != 3) q(buf, m, tx + dx, y0 + 9 * kk, tx + dx + 3, y0 + 9 * kk + 1, 0.3F, 0xD0F2EFE4, luz);
            y0 += 9 * kk + 5;
        }
        int cols = Math.max(1, w / 2), filas = Math.max(1, h / 2);
        float pw = (der - izq) / cols, ph = (abajo - y0) / filas;
        String[] ids = com.miscelanea.arcade.ArcadeJuegos.FERIA;
        int total = cols * filas, pagina = (int) (ms / 9000);
        for (int fila = 0; fila < filas; fila++)
            for (int col = 0; col < cols; col++) {
                int idx = Math.floorMod(pagina * total + fila * cols + col, ids.length);
                panelTiza(ps, buf, font, izq + col * pw, y0 + fila * ph, pw - 4, ph - 2, ids[idx], idx, luz);
            }
    }

    private void panelTiza(PoseStack ps, MultiBufferSource buf, Font font, float x, float y, float pw, float ph, String id, int n, int luz) {
        Matrix4f m = ps.last().pose();
        int tiza = TIZAS[n % TIZAS.length];
        String nombre = I18n.get("juego.miscelanea." + id);
        float kt = Math.min(1F, pw / Math.max(1, font.width(nombre)));
        textoL(ps, buf, font, nombre, x + pw / 2 - font.width(nombre) * kt / 2, y, kt, 0.4F, tiza, luz);
        float tw = font.width(nombre) * kt;
        for (float dx = 0; dx < tw; dx += 2) q(buf, m, x + pw / 2 - tw / 2 + dx, y + 9, x + pw / 2 - tw / 2 + dx + 1, y + 10, 0.3F, (tiza & 0x00FFFFFF) | 0xB0000000, luz);
        ListTag tb = ArcadeCliente.tabla(id);
        float yy = y + 13, linea = 9.5F;
        int max = Math.max(1, Math.min(10, (int) ((y + ph - yy) / linea)));
        String unidad = I18n.get("cartel.miscelanea.unidad." + id);
        if (tb.isEmpty()) {
            String v = I18n.get("cartel.miscelanea.vacio_feria");
            float kv = Math.min(1F, pw / Math.max(1, font.width(v)));
            textoL(ps, buf, font, v, x + pw / 2 - font.width(v) * kv / 2, yy, kv, 0.4F, 0xC8F2EFE4, luz);
            return;
        }
        for (int i = 0; i < Math.min(max, tb.size()); i++) {
            CompoundTag c = tb.getCompound(i);
            int col = i == 0 ? 0xFFFFE97A : i < 3 ? 0xFFF2EFE4 : 0xD8D8D4C8;
            String pts = c.getInt("p") + (unidad.isEmpty() ? "" : " " + unidad);
            String nm = (i + 1) + ". " + c.getString("n");
            int wp = font.width(pts);
            nm = font.plainSubstrByWidth(nm, (int) Math.max(10, pw - wp - 4));
            textoL(ps, buf, font, nm, x, yy, 1F, 0.4F, col, luz);
            textoL(ps, buf, font, pts, x + pw - wp, yy, 1F, 0.4F, col, luz);
            yy += linea;
        }
    }

    // ------------------------------------------------------------------ tablón de corcho del ranking del servidor
    static final int[] MEDALLA = {0xFFF2C230, 0xFFD5DAE2, 0xFFD08A48}, MEDALLA_O = {0xFFA87A10, 0xFF8A909A, 0xFF8A5020};
    static final int TINTA = 0xFF262636;

    private static void q(MultiBufferSource buf, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int luz) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        z *= CAPAS;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(luz).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(luz).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(luz).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(luz).endVertex();
    }

    private static void disco(MultiBufferSource buf, Matrix4f m, float cx, float cy, float r, float z, int argb, int luz) {
        int n = Math.max(3, Math.round(r * 2.5F));
        for (int i = 0; i < n; i++) {
            float y0 = cy - r + 2 * r * i / n, y1 = cy - r + 2 * r * (i + 1) / n, ym = (y0 + y1) / 2 - cy;
            float hw = (float) Math.sqrt(Math.max(0, r * r - ym * ym));
            q(buf, m, cx - hw, y0, cx + hw, y1, z, argb, luz);
        }
    }

    private static void textoL(PoseStack ps, MultiBufferSource buf, Font font, String s, float x, float y, float k, float z, int col, int luz) {
        ps.pushPose();
        ps.translate(x, y, z * CAPAS);
        ps.scale(k, k, 1);
        font.drawInBatch(s, 0, 0, col, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, luz);
        ps.popPose();
    }

    static String miles(int n) {
        String s = Integer.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    /**
     * Tablón de corcho con marco de madera: letrero de neón arriba, una etiqueta con la categoría y una hoja clavada con
     * chinchetas con los 10 primeros (medallas para el podio). La luz del tablón es la del sitio (el neón siempre brilla).
     */
    private void tablon(PoseStack ps, MultiBufferSource buf, int W, int H, int w, int h, String cat, int luzSitio) {
        Font font = Minecraft.getInstance().font;
        long ms = System.currentTimeMillis();
        Matrix4f m = ps.last().pose();
        int luz = net.minecraft.client.renderer.LightTexture.pack(Math.max(7, net.minecraft.client.renderer.LightTexture.block(luzSitio)),
                net.minecraft.client.renderer.LightTexture.sky(luzSitio));
        float F = 4;
        // marco de madera con bisel
        q(buf, m, 0, 0, W, H, 0, 0xFF7B4B26, luz);
        q(buf, m, 0, 0, W, 1, 0.02F, 0xFFA06A38, luz);
        q(buf, m, 0, 0, 1, H, 0.02F, 0xFFA06A38, luz);
        q(buf, m, 0, H - 1, W, H, 0.02F, 0xFF4E2C16, luz);
        q(buf, m, W - 1, 0, W, H, 0.02F, 0xFF4E2C16, luz);
        for (int k = 0; k < 3; k++) {
            float y = 1.6F + k * 0.8F;
            q(buf, m, 1, y, W - 1, y + 0.3F, 0.03F, 0xFF694020, luz);
            q(buf, m, 1, H - y - 0.3F, W - 1, H - y, 0.03F, 0xFF694020, luz);
        }
        q(buf, m, F - 0.7F, F - 0.7F, W - F + 0.7F, H - F + 0.7F, 0.04F, 0xFF3E2410, luz);
        // corcho con motas
        q(buf, m, F, F, W - F, H - F, 0.05F, 0xFFBF8B55, luz);
        java.util.Random r = new java.util.Random(W * 7919L + H);
        int[] motas = {0xFF9E6B3A, 0xFFD6A672, 0xFF8A5A2E, 0xFFCC9A64, 0xFFA87444};
        int nm = (int) ((W - 2 * F) * (H - 2 * F) / 12);
        for (int k = 0; k < nm; k++) {
            float x = F + r.nextFloat() * (W - 2 * F - 1.6F), y = F + r.nextFloat() * (H - 2 * F - 1.6F), sz = 0.5F + r.nextFloat() * 1.1F;
            q(buf, m, x, y, x + sz, y + sz * (0.6F + r.nextFloat() * 0.6F), 0.07F, motas[r.nextInt(motas.length)], luz);
        }
        // letrero de neón
        boolean grande = h >= 2;
        float y0 = F + 2, ph = grande ? 15 : 10;
        float px0 = F + 4, px1 = W - F - 4;
        q(buf, m, px0 + 0.8F, y0 + 0.8F, px1 + 0.8F, y0 + ph + 0.8F, 0.1F, 0x60000000, luz);
        q(buf, m, px0, y0, px1, y0 + ph, 0.12F, 0xFF1C1226, LUZ);
        boolean parpadeo = ms % 7000 < 140 || (ms % 7000 > 300 && ms % 7000 < 380);
        int tubo = parpadeo ? 0xFF4A8A90 : 0xFF8FF8FF, halo = parpadeo ? 0x105AF0FF : 0x385AF0FF;
        float in = 1.4F;
        q(buf, m, px0 + in - 0.6F, y0 + in - 0.6F, px1 - in + 0.6F, y0 + in + 1.2F, 0.14F, halo, LUZ);
        q(buf, m, px0 + in - 0.6F, y0 + ph - in - 1.2F, px1 - in + 0.6F, y0 + ph - in + 0.6F, 0.14F, halo, LUZ);
        q(buf, m, px0 + in, y0 + in, px1 - in, y0 + in + 0.6F, 0.15F, tubo, LUZ);
        q(buf, m, px0 + in, y0 + ph - in - 0.6F, px1 - in, y0 + ph - in, 0.15F, tubo, LUZ);
        q(buf, m, px0 + in, y0 + in, px0 + in + 0.6F, y0 + ph - in, 0.15F, tubo, LUZ);
        q(buf, m, px1 - in - 0.6F, y0 + in, px1 - in, y0 + ph - in, 0.15F, tubo, LUZ);
        String tit = I18n.get("ranking.miscelanea.tablon.titulo");
        float kt = Math.min((ph - 5) / 7.5F, (px1 - px0 - 8) / Math.max(1, font.width(tit)));
        float tx = (W - font.width(tit) * kt) / 2, tyt = y0 + (ph - 7 * kt) / 2;
        int nucleo = parpadeo ? 0xFFB070A0 : 0xFFFFE0F8, brillo = parpadeo ? 0x30FF3CC8 : 0x90FF3CC8;
        for (float[] o : new float[][]{{-0.5F, 0}, {0.5F, 0}, {0, -0.5F}, {0, 0.5F}})
            textoL(ps, buf, font, tit, tx + o[0], tyt + o[1], kt, 0.35F, brillo, LUZ);
        textoL(ps, buf, font, tit, tx, tyt, kt, 0.4F, nucleo, LUZ);
        // etiqueta de papel con la categoría (y dos trozos de celo)
        float y1 = y0 + ph + 2.5F, lh = grande ? 11 : 8;
        String nombre = com.miscelanea.ranking.Ranking.nombre(cat).getString();
        float maxW = W - 2 * F - 26;
        float kl = Math.min((lh - 3) / 7.5F, maxW / Math.max(1, font.width(nombre)));
        float lw = Math.max(30, font.width(nombre) * kl + 10), lx = (W - lw) / 2;
        q(buf, m, lx + 0.8F, y1 + 0.8F, lx + lw + 0.8F, y1 + lh + 0.8F, 0.16F, 0x50000000, luz);
        q(buf, m, lx, y1, lx + lw, y1 + lh, 0.2F, 0xFFFFF4C8, luz);
        q(buf, m, lx - 2, y1 - 1, lx + 4, y1 + 2.5F, 0.3F, 0x90E8E4D0, luz);
        q(buf, m, lx + lw - 4, y1 - 1, lx + lw + 2, y1 + 2.5F, 0.3F, 0x90E8E4D0, luz);
        textoL(ps, buf, font, nombre, (W - font.width(nombre) * kl) / 2, y1 + (lh - 7 * kl) / 2, kl, 0.4F, 0xFF4A2A10, luz);
        // hoja con la tabla, clavada con chinchetas
        float sx0 = F + 5, sx1 = W - F - 5, sy0 = y1 + lh + 3.5F, sy1 = H - F - 3;
        q(buf, m, sx0 + 1.2F, sy0 + 1.2F, sx1 + 1.2F, sy1 + 1.2F, 0.16F, 0x55000000, luz);
        q(buf, m, sx0, sy0, sx1, sy1, 0.2F, 0xFFF6F0E0, luz);
        float margen = sx0 + 10;
        q(buf, m, margen, sy0, margen + 0.5F, sy1, 0.22F, 0x90E06060, luz);
        for (float[] c : new float[][]{{sx0 + 3.5F, sy0 + 2.5F, 0}, {sx1 - 3.5F, sy0 + 2.5F, 1}}) {
            int col = c[2] == 0 ? 0xFFD23C3C : 0xFF3C6CD2;
            disco(buf, m, c[0] + 0.5F, c[1] + 0.6F, 2F, 0.45F, 0x60000000, luz);
            disco(buf, m, c[0], c[1], 2F, 0.5F, col, luz);
            disco(buf, m, c[0] - 0.6F, c[1] - 0.6F, 0.7F, 0.55F, 0xC0FFFFFF, luz);
        }
        ListTag tb = ArcadeCliente.tabla(cat);
        int filas = grande ? 10 : 5;
        float alto = (sy1 - sy0 - 5) / filas, k = Math.min(alto / 9.2F, 1.3F);
        String unidad = I18n.get(cat.startsWith(com.miscelanea.ranking.Ranking.MESA) || cat.equals("global/victorias") ? "ranking.miscelanea.corta.victorias"
                : cat.equals("global/campeones") ? "ranking.miscelanea.corta.records" : "ranking.miscelanea.corta.puntos");
        for (int i = 0; i < filas; i++) {
            float yy = sy0 + 4 + i * alto;
            q(buf, m, sx0 + 1, yy + alto - 0.4F, sx1 - 1, yy + alto, 0.21F, 0x607FA8D8, luz);
        }
        if (tb.isEmpty()) {
            String v = I18n.get("ranking.miscelanea.tablon.vacio");
            float kv = Math.min(k * 1.1F, (sx1 - margen - 6) / Math.max(1, font.width(v)));
            textoL(ps, buf, font, v, (margen + sx1 - font.width(v) * kv) / 2, (sy0 + sy1) / 2 - 4 * kv, kv, 0.4F, 0xFF6A6A7A, luz);
            return;
        }
        for (int i = 0; i < Math.min(filas, tb.size()); i++) {
            CompoundTag c = tb.getCompound(i);
            float yy = sy0 + 4 + i * alto, ty = yy + (alto - 7.2F * k) / 2;
            if (i < 3) {
                float mx = sx0 + 5, my = yy + alto / 2 - 0.2F, mr = Math.min(3.4F, alto * 0.42F);
                disco(buf, m, mx, my, mr, 0.3F, MEDALLA_O[i], luz);
                disco(buf, m, mx, my, mr * 0.78F, 0.32F, MEDALLA[i], luz);
                String n = Integer.toString(i + 1);
                float kn = mr * 0.28F;
                textoL(ps, buf, font, n, mx - font.width(n) * kn / 2 + 0.2F, my - 3.6F * kn, kn, 0.4F, MEDALLA_O[i] & 0xFF7F7F7F | 0xFF000000, luz);
            } else {
                String n = (i + 1) + ".";
                float kn = k * 0.8F;
                textoL(ps, buf, font, n, margen - 1.5F - font.width(n) * kn, ty + 0.6F, kn, 0.4F, 0xFF6A6A7A, luz);
            }
            String pts = miles(c.getInt("p")) + " " + unidad;
            float wp = font.width(pts) * k, xn = margen + 2;
            float libre = sx1 - 2 - wp - 4 - xn;
            String n = font.plainSubstrByWidth(c.getString("n"), (int) Math.max(8, libre / k));
            int tinta = i == 0 ? 0xFF9A1E1E : TINTA;
            textoL(ps, buf, font, n, xn, ty, k, 0.4F, tinta, luz);
            textoL(ps, buf, font, pts, sx1 - 2 - wp, ty, k, 0.4F, tinta, luz);
            // puntitos entre el nombre y la marca
            for (float x = xn + font.width(n) * k + 2; x < sx1 - 3.5F - wp; x += 2.2F)
                q(buf, m, x, ty + 6 * k, x + 0.55F, ty + 6 * k + 0.55F, 0.3F, 0x70262636, luz);
        }
    }

    private static void texto(PoseStack ps, MultiBufferSource buf, Font font, String s, float cx, float y, float k, int col, boolean centrado) {
        float x = centrado ? cx - font.width(s) * k / 2 : cx;
        textoIzq(ps, buf, font, s, x, y, k, col);
    }

    private static void textoIzq(PoseStack ps, MultiBufferSource buf, Font font, String s, float x, float y, float k, int col) {
        ps.pushPose();
        ps.translate(x, y, 0.4F * CAPAS);
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
