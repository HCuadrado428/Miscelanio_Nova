package com.miscelanea.client.tortura.qte;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** Primitivas para los HUD de los minijuegos: arcos y discos suaves, líneas gruesas, triángulos, paneles, teclas, barras. */
public final class Dibujo {
    private Dibujo() {}

    public static int alfa(int argb, float k) {
        int a = (int) ((argb >>> 24) * Math.max(0, Math.min(1, k)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int mezcla(int c0, int c1, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c0 >>> 24) & 255) * (1 - t) + ((c1 >>> 24) & 255) * t);
        int r = (int) (((c0 >> 16) & 255) * (1 - t) + ((c1 >> 16) & 255) * t);
        int gg = (int) (((c0 >> 8) & 255) * (1 - t) + ((c1 >> 8) & 255) * t);
        int b = (int) ((c0 & 255) * (1 - t) + (c1 & 255) * t);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private static BufferBuilder empezar(GuiGraphics g, VertexFormat.Mode modo) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(modo, DefaultVertexFormat.POSITION_COLOR);
        return bb;
    }

    private static void acabar(BufferBuilder bb) {
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, int c) {
        bb.vertex(m, x, y, 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, c >>> 24).endVertex();
    }

    /** Arco de corona circular. Ángulos en grados: 0 = arriba, sentido horario. */
    public static void arco(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int c) {
        arco(g, cx, cy, r0, r1, a0, a1, c, c);
    }

    /** Igual, con un color en el borde interior y otro en el exterior (degradado radial). */
    public static void arco(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int cDentro, int cFuera) {
        if (a1 < a0) {
            float t = a0;
            a0 = a1;
            a1 = t;
        }
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = empezar(g, VertexFormat.Mode.TRIANGLE_STRIP);
        int n = Math.max(2, (int) Math.ceil((a1 - a0) / 3F));
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / n);
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            v(bb, m, cx + sx * r1, cy + sy * r1, cFuera);
            v(bb, m, cx + sx * r0, cy + sy * r0, cDentro);
        }
        acabar(bb);
    }

    public static void disco(GuiGraphics g, float cx, float cy, float r, int c) {
        arco(g, cx, cy, 0, r, 0, 360, c);
    }

    /** Disco con halo suave alrededor. */
    public static void brillo(GuiGraphics g, float cx, float cy, float r, float halo, int c) {
        arco(g, cx, cy, r, r + halo, 0, 360, c, c & 0xFFFFFF);
        disco(g, cx, cy, r, c);
    }

    public static void linea(GuiGraphics g, float x0, float y0, float x1, float y1, float grosor, int c) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1.0E-3F) return;
        float nx = -dy / l * grosor / 2, ny = dx / l * grosor / 2;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = empezar(g, VertexFormat.Mode.QUADS);
        v(bb, m, x0 + nx, y0 + ny, c);
        v(bb, m, x1 + nx, y1 + ny, c);
        v(bb, m, x1 - nx, y1 - ny, c);
        v(bb, m, x0 - nx, y0 - ny, c);
        acabar(bb);
    }

    public static void triangulo(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, int c) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = empezar(g, VertexFormat.Mode.TRIANGLES);
        v(bb, m, x0, y0, c);
        v(bb, m, x1, y1, c);
        v(bb, m, x2, y2, c);
        acabar(bb);
    }

    /** Flecha (0 arriba, 1 abajo, 2 izquierda, 3 derecha) centrada en (cx, cy). */
    public static void flecha(GuiGraphics g, float cx, float cy, float t, int dir, int c) {
        float[][] p = {{0, -1}, {0.9F, 0.15F}, {-0.9F, 0.15F}};
        float[][] q = {{-0.36F, 0.15F}, {0.36F, 0.15F}, {0.36F, 0.95F}, {-0.36F, 0.95F}};
        float[] xy = new float[14];
        int k = 0;
        for (float[] a : p) {
            float[] r = rot(a, dir);
            xy[k++] = cx + r[0] * t;
            xy[k++] = cy + r[1] * t;
        }
        triangulo(g, xy[0], xy[1], xy[2], xy[3], xy[4], xy[5], c);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = empezar(g, VertexFormat.Mode.QUADS);
        for (float[] a : q) {
            float[] r = rot(a, dir);
            v(bb, m, cx + r[0] * t, cy + r[1] * t, c);
        }
        acabar(bb);
    }

    private static float[] rot(float[] a, int dir) {
        return switch (dir) {
            case 1 -> new float[]{-a[0], -a[1]};
            case 2 -> new float[]{a[1], -a[0]};
            case 3 -> new float[]{-a[1], a[0]};
            default -> a;
        };
    }

    /** Texto centrado y escalado. */
    public static void texto(GuiGraphics g, Font f, Component c, float cx, float y, float esc, int col, boolean sombra) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(esc, esc, 1);
        g.drawString(f, c, -f.width(c) / 2, 0, col, sombra);
        g.pose().popPose();
    }

    /** Panel oscuro de videojuego: fondo degradado, borde fino claro, esquinas marcadas. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int acento) {
        g.fillGradient(x0, y0, x1, y1, 0xD0101216, 0xD8060708);
        g.fill(x0, y0, x1, y0 + 1, 0x60FFFFFF);
        g.fill(x0, y1 - 1, x1, y1, 0x50000000);
        g.fill(x0, y0, x0 + 1, y1, 0x30FFFFFF);
        g.fill(x1 - 1, y0, x1, y1, 0x30FFFFFF);
        int l = 6;
        for (int[] e : new int[][]{{x0, y0, 1, 1}, {x1, y0, -1, 1}, {x0, y1, 1, -1}, {x1, y1, -1, -1}}) {
            int x = e[0], y = e[1], sx = e[2], sy = e[3];
            g.fill(Math.min(x, x + sx * l), Math.min(y, y + sy * 2), Math.max(x, x + sx * l), Math.max(y, y + sy * 2), acento);
            g.fill(Math.min(x, x + sx * 2), Math.min(y, y + sy * l), Math.max(x, x + sx * 2), Math.max(y, y + sy * l), acento);
        }
    }

    /** Placa metálica con remaches (reloj de la trampa). */
    public static void panelMetal(GuiGraphics g, int x0, int y0, int x1, int y1, int fondo) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
        g.fillGradient(x0, y0, x1, y1, mezcla(fondo, 0xFF5A5A60, 0.35F), fondo);
        g.fill(x0, y0, x1, y0 + 1, 0x70FFFFFF);
        g.fill(x0, y1 - 1, x1, y1, 0x90000000);
        for (int[] r : new int[][]{{x0 + 2, y0 + 2}, {x1 - 4, y0 + 2}, {x0 + 2, y1 - 4}, {x1 - 4, y1 - 4}}) {
            g.fill(r[0], r[1], r[0] + 2, r[1] + 2, 0xFF2A2A2E);
            g.fill(r[0], r[1], r[0] + 1, r[1] + 1, 0xFFB0B0B8);
        }
    }

    /**
     * Tecla de teclado con relieve. estado: 0 normal, 1 la que toca (brilla), 2 pulsada, 3 acertada (verde), 4 fallada (roja).
     */
    public static void tecla(GuiGraphics g, Font f, int x, int y, int w, int h, String txt, int estado, float brilloK) {
        int base = switch (estado) {
            case 1 -> 0xFFE8E2D0;
            case 2 -> 0xFFB8B2A0;
            case 3 -> 0xFF5AD06A;
            case 4 -> 0xFFE04040;
            default -> 0xFF8A8C92;
        };
        int hundir = estado == 2 ? 2 : 0;
        if (estado == 1 && brilloK > 0) {
            int halo = alfa(0xFFFFE8A0, 0.45F * brilloK);
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, halo);
        }
        g.fill(x, y + 2, x + w, y + h + 2, 0xFF1A1A1E);                      // sombra / lado
        g.fill(x, y + hundir, x + w, y + h + hundir, mezcla(base, 0xFF000000, 0.45F)); // canto
        g.fillGradient(x + 1, y + hundir, x + w - 1, y + h - 2 + hundir, mezcla(base, 0xFFFFFFFF, 0.25F), base);
        g.fill(x + 1, y + hundir, x + w - 1, y + 1 + hundir, mezcla(base, 0xFFFFFFFF, 0.6F));
        if (!txt.isEmpty()) {
            int col = estado == 0 ? 0xFF1E1E22 : 0xFF141414;
            g.drawString(f, txt, x + (w - f.width(txt)) / 2, y + (h - 2 - 8) / 2 + 1 + hundir, col, false);
        }
    }

    /** Barra horizontal con degradado y brillo. */
    public static void barra(GuiGraphics g, int x, int y, int w, int h, float v, int c0, int c1) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fillGradient(x, y, x + w, y + h, 0xFF1C1C20, 0xFF2A2A30);
        int lleno = (int) (w * Math.max(0, Math.min(1, v)));
        if (lleno > 0) {
            g.fillGradient(x, y, x + lleno, y + h, c0, c1);
            g.fill(x, y, x + lleno, y + 1, 0x70FFFFFF);
            g.fill(x + lleno - 1, y, x + lleno, y + h, 0xC0FFFFFF);
        }
        for (int i = 1; i < 10; i++) g.fill(x + w * i / 10, y + h - 2, x + w * i / 10 + 1, y + h, 0x50000000);
    }
}
