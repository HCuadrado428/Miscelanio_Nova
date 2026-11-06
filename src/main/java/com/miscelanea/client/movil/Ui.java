package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.UUID;

/** Dibujo del móvil: colores, rectángulos redondeados en pixel art, iconos, glifos, caras y la fuente mini de 3x5. */
public final class Ui {
    private Ui() {}

    public static final ResourceLocation ICONOS = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/movil/iconos.png");
    public static final int ICONOS_TAM = 256;

    // paleta (tema oscuro)
    public static final int FONDO = 0xFF0F1419, CABECERA = 0xFF1A2129, LINEA = 0xFF252D36, TARJETA = 0xFF1C242C, TEXTO = 0xFFEAEFF3,
            TEXTO2 = 0xFF8B96A1, ACENTO = 0xFF35D07F, AZUL = 0xFF4EA4F6, ROJO = 0xFFEF4B4B, AMARILLO = 0xFFF5C84C,
            MIA = 0xFF1E6A50, OTRO = 0xFF232B34;

    // iconos de apps (24x24, fila 0)
    public static final int APP_CHATS = 0, APP_LLAMAR = 1, APP_AGENDA = 2, APP_CAMARA = 3, APP_FOTOS = 4, APP_RELOJ = 5, APP_LINTERNA = 6,
            APP_AJUSTES = 7, APP_MINEGRAM = 8, APP_MAPA = 9, APP_BANCO = 10;
    // glifos 9x9 blancos (fila y=32, cada 10 px)
    public static final int G_ATRAS = 0, G_ENVIAR = 1, G_MAS = 2, G_TELEFONO = 3, G_VIDEO = 4, G_COLGAR = 5, G_CAMARA = 6, G_BORRAR = 7,
            G_GIRAR = 8, G_OK = 9, G_SILENCIO = 10, G_SALIR = 11, G_SALIENTE = 12, G_ENTRANTE = 13, G_PERDIDA = 14, G_MENSAJE = 15,
            G_GRUPO = 16, G_FOTO = 17, G_ALARMA = 18, G_DERECHA = 19, G_MENOS = 20, G_BORRAR_TECLA = 21, G_LINTERNA = 22, G_VIDEO_NO = 23,
            G_CORAZON = 24, G_CORAZON_V = 25, G_COMENTARIO = 26, G_MICRO = 27, G_PLAY = 28, G_PAUSA = 29, G_PIN = 30, G_NAVEGAR = 31,
            G_ESMERALDA = 32, G_TRANSFER = 33, G_SEGUIR = 34, G_CENTRAR = 35, G_PERFIL = 36, G_CASA = 37, G_LISTA = 38, G_CAMPANA = 39,
            G_COFRE = 40;

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    /** Rectángulo con esquinas redondeadas "a píxeles" (radio r). */
    public static void redondo(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            rect(g, x, y, w, h, c);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int in = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x + in, y + i, x + w - in, y + i + 1, c);
            g.fill(x + in, y + h - i - 1, x + w - in, y + h - i, c);
        }
        g.fill(x, y + r, x + w, y + h - r, c);
    }

    public static void circulo(GuiGraphics g, int cx, int cy, int rad, int c) {
        redondo(g, cx - rad, cy - rad, rad * 2, rad * 2, rad, c);
    }

    public static void texto(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(font(), s, x, y, c, false);
    }

    public static void texto(GuiGraphics g, Component s, int x, int y, int c) {
        g.drawString(font(), s, x, y, c, false);
    }

    public static void centrado(GuiGraphics g, String s, int cx, int y, int c) {
        g.drawString(font(), s, cx - font().width(s) / 2, y, c, false);
    }

    public static int ancho(String s) {
        return font().width(s);
    }

    /** Recorta con "…" para que quepa en w. */
    public static String cortar(String s, int w) {
        Font f = font();
        if (f.width(s) <= w) return s;
        String e = "…";
        return f.plainSubstrByWidth(s, Math.max(0, w - f.width(e))) + e;
    }

    public static List<FormattedCharSequence> envolver(String s, int w) {
        return font().split(Component.literal(s), w);
    }

    public static void icono(GuiGraphics g, int app, int x, int y) {
        RenderSystem.enableBlend();
        // apps 0-9 en la fila de arriba; de la 10 en adelante, en la fila y=64
        if (app < 10) g.blit(ICONOS, x, y, app * 24, 0, 24, 24, ICONOS_TAM, ICONOS_TAM);
        else g.blit(ICONOS, x, y, (app - 10) * 24, 64, 24, 24, ICONOS_TAM, ICONOS_TAM);
    }

    public static void glifo(GuiGraphics g, int i, int x, int y, int color) {
        RenderSystem.enableBlend();
        g.setColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, ((color >>> 24) & 0xFF) / 255F);
        g.blit(ICONOS, x, y, (i % 25) * 10, 32 + (i / 25) * 10, 9, 9, ICONOS_TAM, ICONOS_TAM);
        g.setColor(1, 1, 1, 1);
    }

    /** Botón redondo con un glifo en el centro. */
    public static void boton(GuiGraphics g, int cx, int cy, int rad, int fondo, int glifo, int colorGlifo) {
        circulo(g, cx, cy, rad, fondo);
        glifo(g, glifo, cx - 4, cy - 4, colorGlifo);
    }

    public static boolean dentro(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static boolean enCirculo(double mx, double my, int cx, int cy, int rad) {
        double dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= (rad + 1) * (rad + 1);
    }

    public static ResourceLocation piel(UUID id) {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        PlayerInfo pi = c == null || id == null ? null : c.getPlayerInfo(id);
        return pi != null ? pi.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id == null ? new UUID(0, 0) : id);
    }

    /** Cara del jugador (con sombrero) con las esquinas redondeadas usando el color de fondo. */
    public static void cara(GuiGraphics g, UUID id, int x, int y, int tam, int fondo) {
        PlayerFaceRenderer.draw(g, piel(id), x, y, tam);
        int c = Math.max(1, tam / 8);
        rect(g, x, y, c, c, fondo);
        rect(g, x + tam - c, y, c, c, fondo);
        rect(g, x, y + tam - c, c, c, fondo);
        rect(g, x + tam - c, y + tam - c, c, c, fondo);
    }

    /** Avatar de grupo: círculo de color (según el nombre) con la inicial. */
    public static void avatarGrupo(GuiGraphics g, String nombre, int x, int y, int tam) {
        int[] cols = {0xFF5B8DEF, 0xFFE0795B, 0xFF4FBF8F, 0xFFB46FE0, 0xFFE0B04F, 0xFF4FB7D9};
        int c = cols[Math.floorMod(nombre.hashCode(), cols.length)];
        redondo(g, x, y, tam, tam, tam / 2, c);
        glifo(g, G_GRUPO, x + tam / 2 - 4, y + tam / 2 - 4, 0xFFFFFFFF);
    }

    /** Flecha (triángulo) que apunta en el ángulo dado (radianes, 0 = arriba, sentido horario), en píxeles. */
    public static void flecha(GuiGraphics g, int cx, int cy, int r, double ang, int relleno, int borde) {
        double s = Math.sin(ang), c = -Math.cos(ang);
        // punta, y las dos esquinas de atrás
        double px = cx + s * r, py = cy + c * r;
        double bx1 = cx - s * r * 0.75 + (-c) * r * 0.7, by1 = cy - c * r * 0.75 + s * r * 0.7;
        double bx2 = cx - s * r * 0.75 - (-c) * r * 0.7, by2 = cy - c * r * 0.75 - s * r * 0.7;
        double mx = cx - s * r * 0.35, my = cy - c * r * 0.35;
        for (int y = cy - r - 1; y <= cy + r + 1; y++)
            for (int x = cx - r - 1; x <= cx + r + 1; x++) {
                double qx = x + 0.5, qy = y + 0.5;
                boolean a = enTri(qx, qy, px, py, bx1, by1, mx, my) || enTri(qx, qy, px, py, mx, my, bx2, by2);
                if (a) g.fill(x, y, x + 1, y + 1, relleno);
            }
        if (borde != 0) {
            // contorno de un píxel: píxeles fuera que tocan a uno dentro
            for (int y = cy - r - 2; y <= cy + r + 2; y++)
                for (int x = cx - r - 2; x <= cx + r + 2; x++) {
                    double qx = x + 0.5, qy = y + 0.5;
                    if (enTri(qx, qy, px, py, bx1, by1, mx, my) || enTri(qx, qy, px, py, mx, my, bx2, by2)) continue;
                    boolean toca = false;
                    for (int k = 0; k < 4 && !toca; k++) {
                        double ox = qx + (k == 0 ? 1 : k == 1 ? -1 : 0), oy = qy + (k == 2 ? 1 : k == 3 ? -1 : 0);
                        toca = enTri(ox, oy, px, py, bx1, by1, mx, my) || enTri(ox, oy, px, py, mx, my, bx2, by2);
                    }
                    if (toca) g.fill(x, y, x + 1, y + 1, borde);
                }
        }
    }

    private static boolean enTri(double x, double y, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by);
        double d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy);
        double d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    /** Botón "pastilla" con texto centrado. */
    public static void pastilla(GuiGraphics g, int x, int y, int w, int h, String s, int fondo, int color) {
        redondo(g, x, y, w, h, h / 2, fondo);
        centrado(g, cortar(s, w - 6), x + w / 2, y + (h - 8) / 2, color);
    }

    /** Número con separador de miles (1.234.567). */
    public static String miles(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    public static void insignia(GuiGraphics g, int n, int cx, int cy, int color) {
        String s = n > 99 ? "99+" : Integer.toString(n);
        int w = Math.max(9, ancho(s) + 4);
        redondo(g, cx - w / 2, cy - 5, w, 10, 5, color);
        centrado(g, s, cx, cy - 4, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ fuente mini 3x5 (horas, porcentajes)
    private static final String MINI = "0123456789:/%-.";
    private static final String[] MINI_G = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
            "000010000010000", "001001010100100", "101001010100101", "000000111000000", "000000000000010"};

    public static int anchoMini(String s) {
        return Math.max(0, s.length() * 4 - 1);
    }

    public static void mini(GuiGraphics g, String s, int x, int y, int c) {
        for (int i = 0; i < s.length(); i++) {
            int k = MINI.indexOf(s.charAt(i));
            if (k < 0) continue;
            String m = MINI_G[k];
            for (int py = 0; py < 5; py++)
                for (int px = 0; px < 3; px++)
                    if (m.charAt(py * 3 + px) == '1') g.fill(x + i * 4 + px, y + py, x + i * 4 + px + 1, y + py + 1, c);
        }
    }

    /** Texto con escala entera (2 = el doble), alineado a píxel. */
    public static void grande(GuiGraphics g, String s, int cx, int y, int escala, int c) {
        g.pose().pushPose();
        g.pose().translate(cx - font().width(s) * escala / 2F, y, 0);
        g.pose().scale(escala, escala, 1);
        g.drawString(font(), s, 0, 0, c, false);
        g.pose().popPose();
    }
}
