package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minegram: feed (todos / siguiendo) con me gusta (corazón o doble clic en la foto) y comentarios, publicación con sus
 * comentarios, nueva publicación (foto de la galería o de la cámara + pie), perfil de cada jugador (cara, números y
 * rejilla de fotos, seguir), personas y notificaciones. Barra inferior como la de la app de verdad.
 */
final class VistasMinegram {
    private VistasMinegram() {}

    static final int Y0 = VistasChat.Y0, NAV = 17, FOTO = Vista.W;
    static final int ROSA = 0xFFE1306C, NARANJA = 0xFFF7A034, MORADO = 0xFF8A3AB9, AZUL_MG = 0xFF3897F0;
    static final int T_FEED = 0, T_PERSONAS = 1, T_NUEVO = 2, T_NOTIS = 3, T_YO = 4;

    static int finLista() {
        return Vista.H - Vista.ABAJO - NAV;
    }

    static UUID yo() {
        var p = Minecraft.getInstance().player;
        return p == null ? new UUID(0, 0) : p.getUUID();
    }

    static String hace(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return Vista.t("mg_ahora");
        if (s < 3600) return Vista.t("mg_min", s / 60);
        if (s < 86400) return Vista.t("mg_h", s / 3600);
        return Vista.t("mg_d", s / 86400);
    }

    /** "Minegram" con degradado morado → rosa → naranja. */
    static void logo(GuiGraphics g, int x, int y) {
        String s = "Minegram";
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            float k = i / (float) (s.length() - 1);
            int c = k < 0.5F ? mezcla(MORADO, ROSA, k * 2) : mezcla(ROSA, NARANJA, (k - 0.5F) * 2);
            String ch = s.substring(i, i + 1);
            g.drawString(Ui.font(), Component.literal(ch).withStyle(ChatFormatting.BOLD), cx, y, c, false);
            cx += Ui.font().width(Component.literal(ch).withStyle(ChatFormatting.BOLD));
        }
    }

    static int mezcla(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 0xFF, (b >> 16) & 0xFF), gg = (int) Mth.lerp(k, (a >> 8) & 0xFF, (b >> 8) & 0xFF),
                bb = (int) Mth.lerp(k, a & 0xFF, b & 0xFF);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }

    /** Cabecera de Minegram: atrás + logo (o título). */
    static void cabeceraMg(Vista v, GuiGraphics g, String titulo, int mx, int my) {
        Ui.rect(g, 0, 0, Vista.W, Vista.BARRA + Vista.CAB, Ui.CABECERA);
        boolean sobre = Ui.dentro(mx, my, 0, Vista.BARRA, 18, Vista.CAB);
        if (sobre) Ui.circulo(g, 9, Vista.BARRA + 10, 7, 0x22FFFFFF);
        Ui.glifo(g, Ui.G_ATRAS, 5, Vista.BARRA + 6, Ui.TEXTO);
        if (titulo == null) logo(g, 20, Vista.BARRA + 6);
        else Ui.texto(g, Ui.cortar(titulo, Vista.W - 44), 20, Vista.BARRA + 6, Ui.TEXTO);
        Ui.rect(g, 0, Vista.BARRA + Vista.CAB, Vista.W, 1, Ui.LINEA);
    }

    private static final int[] NAV_G = {Ui.G_CASA, Ui.G_GRUPO, Ui.G_MAS, Ui.G_CAMPANA, Ui.G_PERFIL};

    static void nav(GuiGraphics g, int activo, int mx, int my) {
        int y = finLista();
        Ui.rect(g, 0, y, Vista.W, NAV + Vista.ABAJO, Ui.CABECERA);
        Ui.rect(g, 0, y, Vista.W, 1, Ui.LINEA);
        for (int i = 0; i < 5; i++) {
            int cx = 13 + i * 26, cy = y + 8;
            boolean s = Ui.dentro(mx, my, cx - 13, y, 26, NAV);
            if (i == T_NUEVO) {
                Ui.redondo(g, cx - 8, cy - 6, 16, 13, 4, s ? 0xFF3A434D : 0xFF2A333D);
                Ui.glifo(g, Ui.G_MAS, cx - 4, cy - 4, Ui.TEXTO);
                continue;
            }
            if (i == T_YO) {
                if (activo == i) Ui.circulo(g, cx, cy, 6, Ui.TEXTO);
                Ui.cara(g, yo(), cx - 5, cy - 5, 10, activo == i ? Ui.TEXTO : Ui.CABECERA);
                continue;
            }
            Ui.glifo(g, NAV_G[i], cx - 4, cy - 4, activo == i ? 0xFFFFFFFF : s ? 0xFFB8C2CC : Ui.TEXTO2);
            if (i == T_NOTIS && MinegramCliente.noVistas > 0) Ui.circulo(g, cx + 5, cy - 4, 2, ROSA);
        }
    }

    /** Toque en la barra inferior: cambia de pestaña (sin apilar). */
    static boolean clicNav(Vista v, double x, double y, int activo) {
        int y0 = finLista();
        if (y < y0 || y >= y0 + NAV) return false;
        int i = Mth.clamp((int) (x / 26), 0, 4);
        MovilCliente.clic();
        if (i == T_NUEVO) {
            v.m.abrir(new Nuevo());
            return true;
        }
        if (i == activo) {
            v.scroll = 0;
            return true;
        }
        v.m.cambiar(switch (i) {
            case T_FEED -> new Feed();
            case T_PERSONAS -> new Personas();
            case T_NOTIS -> new Notis();
            default -> new Perfil(yo());
        });
        return true;
    }

    // ------------------------------------------------------------------ una publicación
    enum Z {FOTO, LIKE, COMENT, AUTOR, VER, BORRAR}

    record Zona(Z tipo, int x, int y, int w, int h, MinegramCliente.Post p) {}

    private static final Map<String, List<FormattedCharSequence>> PIES = new HashMap<>();

    static List<FormattedCharSequence> pie(MinegramCliente.Post p) {
        if (p.pie.isEmpty()) return List.of();
        return PIES.computeIfAbsent(p.id + "|" + p.pie, k -> {
            if (PIES.size() > 200) PIES.clear();
            Component c = Component.literal(p.nombre).withStyle(ChatFormatting.BOLD).append(Component.literal(" " + p.pie).withStyle(ChatFormatting.RESET));
            return Ui.font().split(c, Vista.W - 10);
        });
    }

    static int alto(MinegramCliente.Post p, boolean detalle) {
        return 18 + FOTO + 16 + 10 + pie(p).size() * 9 + (!detalle && p.comentarios > 0 ? 10 : 0) + 10 + 4;
    }

    /** Dibuja una publicación desde y; apunta las zonas que se pueden tocar. */
    static void dibujarPost(GuiGraphics g, MinegramCliente.Post p, int y, int mx, int my, List<Zona> zonas, Map<Integer, Long> corazones, boolean detalle,
                        int recorteY0, int recorteY1) {
        int W = Vista.W;
        boolean dentro = my >= recorteY0 && my < recorteY1;
        // autor
        Ui.circulo(g, 11, y + 9, 7, ROSA);
        Ui.circulo(g, 11, y + 9, 6, Ui.FONDO);
        Ui.cara(g, p.autor, 6, y + 4, 10, Ui.FONDO);
        Ui.texto(g, Component.literal(Ui.cortar(p.nombre, W - 60)).withStyle(ChatFormatting.BOLD), 21, y + 5, Ui.TEXTO);
        String h = hace(p.t);
        Ui.texto(g, h, W - 6 - Ui.ancho(h), y + 5, Ui.TEXTO2);
        zonas.add(new Zona(Z.AUTOR, 0, y, W - 40, 18, p));
        if (detalle && p.puedeBorrar) zonas.add(new Zona(Z.BORRAR, W - 40, y, 40, 18, p));
        int fy = y + 18;
        // foto
        FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_MINEGRAM, p.id);
        if (t != null) FotoCache.cubrir(g, t, 0, fy, W, FOTO);
        else {
            Ui.rect(g, 0, fy, W, FOTO, 0xFF161C22);
            Ui.glifo(g, Ui.G_FOTO, W / 2 - 4, fy + FOTO / 2 - 4, FotoCache.falta(MovilRed.FOTO_MINEGRAM, p.id) ? Ui.ROJO : Ui.TEXTO2);
        }
        zonas.add(new Zona(Z.FOTO, 0, fy, W, FOTO, p));
        Long tc = corazones.get(p.id);
        if (tc != null) {
            long d = Util.getMillis() - tc;
            if (d > 800) corazones.remove(p.id);
            else {
                float k = d < 150 ? d / 150F : 1;
                float a = d > 550 ? (800 - d) / 250F : 1;
                int esc = d < 150 ? 3 + (int) (2 * k) : 5;
                g.pose().pushPose();
                g.pose().translate(W / 2F - 4.5F * esc, fy + FOTO / 2F - 4.5F * esc, 0);
                g.pose().scale(esc, esc, 1);
                Ui.glifo(g, Ui.G_CORAZON, 0, 1, ((int) (a * 120) << 24));
                Ui.glifo(g, Ui.G_CORAZON, 0, 0, ((int) (a * 255) << 24) | 0xFFFFFF);
                g.pose().popPose();
            }
        }
        int ay = fy + FOTO + 4;
        // acciones
        boolean sl = dentro && Ui.dentro(mx, my, 2, ay - 2, 14, 13);
        Ui.glifo(g, p.meGusta ? Ui.G_CORAZON : Ui.G_CORAZON_V, 5, ay, p.meGusta ? ROSA : sl ? 0xFFFFFFFF : Ui.TEXTO);
        zonas.add(new Zona(Z.LIKE, 2, ay - 3, 14, 14, p));
        boolean sc = dentro && Ui.dentro(mx, my, 16, ay - 2, 14, 13);
        Ui.glifo(g, Ui.G_COMENTARIO, 19, ay, sc ? 0xFFFFFFFF : Ui.TEXTO);
        zonas.add(new Zona(Z.COMENT, 16, ay - 3, 14, 14, p));
        if (detalle && p.puedeBorrar) Ui.glifo(g, Ui.G_BORRAR, W - 13, ay, Ui.TEXTO2);
        int ty = ay + 13;
        Ui.texto(g, Component.literal(Vista.t(p.likes == 1 ? "mg_like1" : "mg_likes", Ui.miles(p.likes))).withStyle(ChatFormatting.BOLD), 5, ty, Ui.TEXTO);
        ty += 10;
        for (FormattedCharSequence l : pie(p)) {
            g.drawString(Ui.font(), l, 5, ty, Ui.TEXTO, false);
            ty += 9;
        }
        if (!detalle && p.comentarios > 0) {
            String vc = Vista.t(p.comentarios == 1 ? "mg_ver_comentario" : "mg_ver_comentarios", p.comentarios);
            boolean sv = dentro && Ui.dentro(mx, my, 0, ty, W, 10);
            Ui.texto(g, vc, 5, ty, sv ? Ui.TEXTO : Ui.TEXTO2);
            zonas.add(new Zona(Z.VER, 0, ty, W, 10, p));
            ty += 10;
        }
        g.pose().pushPose();
        g.pose().translate(5, ty + 1, 0);
        g.pose().scale(0.5F, 0.5F, 1);
        g.drawString(Ui.font(), Vista.cuando(p.t) + " · " + h, 0, 0, 0xFF66717C, false);
        g.pose().popPose();
    }

    /** Toque en una zona de publicación (devuelve true si se gestionó). */
    static boolean tocar(Vista v, Zona z, Map<Integer, Long> corazones, long[] ultFoto, int[] ultFotoId, boolean detalle) {
        MinegramCliente.Post p = z.p();
        switch (z.tipo()) {
            case FOTO -> {
                long ahora = Util.getMillis();
                if (ultFotoId[0] == p.id && ahora - ultFoto[0] < 350) {
                    MinegramCliente.like(p, true);
                    corazones.put(p.id, ahora);
                    MovilCliente.sonido(com.miscelanea.movil.MovilRegistro.CLIC.get(), 0.6F, 1.6F);
                    ultFoto[0] = 0;
                } else {
                    ultFoto[0] = ahora;
                    ultFotoId[0] = p.id;
                }
            }
            case LIKE -> {
                MinegramCliente.like(p, !p.meGusta);
                if (p.meGusta) corazones.put(p.id, Util.getMillis());
                MovilCliente.clic();
            }
            case COMENT, VER -> {
                MovilCliente.clic();
                if (!detalle) v.m.abrir(new PostV(p.id));
            }
            case AUTOR -> {
                MovilCliente.clic();
                v.m.abrir(new Perfil(p.autor));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================== feed
    static final class Feed extends Vista {
        private int modo;
        private final List<Zona> zonas = new ArrayList<>();
        private final Map<Integer, Long> corazones = new HashMap<>();
        private final long[] ultFoto = {0};
        private final int[] ultFotoId = {-1};
        private long pidiendo;
        private static final int TABS = 14;

        @Override
        void abierta() {
            MinegramCliente.pedirFeed(modo, false);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int y0 = Y0 + TABS, fin = finLista();
            zonas.clear();
            recortar(g, y0, fin);
            List<MinegramCliente.Post> l = MinegramCliente.FEED[modo];
            int y = y0 - (int) scroll, total = 0;
            for (MinegramCliente.Post p : l) {
                int h = alto(p, false);
                if (y + h > y0 && y < fin) dibujarPost(g, p, y, mx, my, zonas, corazones, false, y0, fin);
                y += h;
                total += h;
            }
            if (MinegramCliente.HAY_MAS[modo]) {
                Ui.centrado(g, t("cargando"), W / 2, y + 4, Ui.TEXTO2);
                total += 16;
                if (scroll > total - (fin - y0) - 60 && Util.getMillis() - pidiendo > 1500) {
                    pidiendo = Util.getMillis();
                    MinegramCliente.pedirFeed(modo, true);
                }
            }
            scrollMax = total - (fin - y0);
            barraScroll(g, y0, fin);
            soltarRecorte(g);
            if (l.isEmpty()) {
                int cy = (y0 + fin) / 2 - 20;
                Ui.circulo(g, W / 2, cy, 14, 0xFF1C242C);
                Ui.glifo(g, Ui.G_CAMARA, W / 2 - 4, cy - 4, Ui.TEXTO2);
                String s = !MinegramCliente.CARGADO[modo] ? t("cargando") : t(modo == 0 ? "mg_vacio" : "mg_vacio_siguiendo");
                int yy = cy + 20;
                for (FormattedCharSequence f : Ui.envolver(s, W - 24)) {
                    g.drawCenteredString(Ui.font(), f, W / 2, yy, Ui.TEXTO2);
                    yy += 10;
                }
            }
            // pestañas
            Ui.rect(g, 0, Y0, W, TABS, Ui.FONDO);
            String[] tt = {t("mg_para_ti"), t("mg_siguiendo")};
            for (int i = 0; i < 2; i++) {
                int cx = W / 4 + i * W / 2;
                Ui.centrado(g, tt[i], cx, Y0 + 3, modo == i ? Ui.TEXTO : Ui.TEXTO2);
                if (modo == i) Ui.rect(g, cx - Ui.ancho(tt[i]) / 2, Y0 + TABS - 2, Ui.ancho(tt[i]), 1, Ui.TEXTO);
            }
            Ui.rect(g, 0, Y0 + TABS - 1, W, 1, Ui.LINEA);
            cabeceraMg(this, g, null, mx, my);
            nav(g, T_FEED, mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (clicNav(this, x, y, T_FEED)) return true;
            if (y >= Y0 && y < Y0 + TABS) {
                int nm = x < W / 2.0 ? 0 : 1;
                if (nm != modo) {
                    modo = nm;
                    scroll = 0;
                    MovilCliente.clic();
                    MinegramCliente.pedirFeed(modo, false);
                }
                return true;
            }
            if (y < Y0 + TABS || y >= finLista()) return false;
            for (Zona z : zonas)
                if (Ui.dentro(x, y, z.x(), z.y(), z.w(), z.h())) return tocar(this, z, corazones, ultFoto, ultFotoId, false);
            return false;
        }
    }

    // ================================================================== una publicación con sus comentarios
    static final class PostV extends Vista {
        private final int id;
        private final VistasChat.Campo campo = new VistasChat.Campo(256);
        private final List<Zona> zonas = new ArrayList<>();
        private final Map<Integer, Long> corazones = new HashMap<>();
        private final long[] ultFoto = {0};
        private final int[] ultFotoId = {-1};
        private final List<int[]> borrarComs = new ArrayList<>();
        private long confirmar;
        private int confirmarCom = -1;
        private long confirmarComT;
        private static final int ENTRADA = 18;

        PostV(int id) {
            this.id = id;
        }

        @Override
        void abierta() {
            MinegramCliente.abiertoId = id;
            MinegramCliente.abiertoNoExiste = false;
            MinegramCliente.abierto = null;
            for (List<MinegramCliente.Post> l : MinegramCliente.FEED)
                for (MinegramCliente.Post p : l) if (p.id == id) MinegramCliente.abierto = p;
            MinegramCliente.COMENTARIOS.clear();
            MovilRed.alServidor(MovilRed.C_MG_POST, b -> b.writeVarInt(id));
        }

        @Override
        boolean escribiendo() {
            return true;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int fin = H - ABAJO - ENTRADA;
            zonas.clear();
            borrarComs.clear();
            MinegramCliente.Post p = MinegramCliente.abiertoId == id ? MinegramCliente.abierto : null;
            if (p == null)
                Ui.centrado(g, t(MinegramCliente.abiertoNoExiste ? "mg_no_existe" : "cargando"), W / 2, H / 2 - 4, Ui.TEXTO2);
            else {
                recortar(g, Y0, fin);
                int y = Y0 - (int) scroll;
                int h = alto(p, true);
                dibujarPost(g, p, y, mx, my, zonas, corazones, true, Y0, fin);
                y += h;
                Ui.rect(g, 0, y, W, 1, Ui.LINEA);
                y += 4;
                if (MinegramCliente.COMENTARIOS.isEmpty()) {
                    Ui.centrado(g, t("mg_sin_comentarios"), W / 2, y + 2, 0xFF59636D);
                    y += 14;
                }
                boolean conf = Util.getMillis() - confirmarComT < 3000;
                for (MinegramCliente.Comentario c : MinegramCliente.COMENTARIOS) {
                    Component txt = Component.literal(c.nombre()).withStyle(ChatFormatting.BOLD).append(Component.literal(" " + c.texto()).withStyle(ChatFormatting.RESET));
                    List<FormattedCharSequence> ls = Ui.font().split(txt, W - 34);
                    int ch = Math.max(14, ls.size() * 9 + 9);
                    if (y + ch > Y0 && y < fin) {
                        Ui.cara(g, c.autor(), 5, y + 1, 10, Ui.FONDO);
                        int yy = y;
                        for (FormattedCharSequence f : ls) {
                            g.drawString(Ui.font(), f, 19, yy, Ui.TEXTO, false);
                            yy += 9;
                        }
                        g.pose().pushPose();
                        g.pose().translate(19, yy + 1, 0);
                        g.pose().scale(0.5F, 0.5F, 1);
                        g.drawString(Ui.font(), hace(c.t()), 0, 0, 0xFF66717C, false);
                        g.pose().popPose();
                        if (c.borrable()) {
                            boolean esta = conf && confirmarCom == c.id();
                            Ui.glifo(g, Ui.G_BORRAR, W - 12, y + 1, esta ? Ui.ROJO : 0xFF4A535C);
                            borrarComs.add(new int[]{c.id(), y});
                        }
                    }
                    y += ch + 3;
                }
                scrollMax = y + (int) scroll - fin + 4;
                barraScroll(g, Y0, fin);
                soltarRecorte(g);
            }
            // escribir comentario
            Ui.rect(g, 0, fin, W, ENTRADA + ABAJO, Ui.CABECERA);
            Ui.cara(g, yo(), 4, fin + 4, 10, Ui.CABECERA);
            Ui.redondo(g, 17, fin + 2, W - 37, 14, 7, 0xFF2A333D);
            campo.dibujar(g, 23, fin + 5, W - 47, t("mg_comenta"), true);
            boolean hay = campo.s.length() > 0;
            Ui.texto(g, Component.literal(t("mg_publicar")).withStyle(ChatFormatting.BOLD), W - 19, fin + 5, hay ? AZUL_MG : 0xFF3A5A78);
            // cabecera
            cabeceraMg(this, g, t("mg_publicacion"), mx, my);
            if (p != null && p.puedeBorrar) {
                boolean c = Util.getMillis() - confirmar < 3000;
                if (c) Ui.texto(g, t("seguro"), W - 16 - Ui.ancho(t("seguro")), BARRA + 6, Ui.ROJO);
                Ui.glifo(g, Ui.G_BORRAR, W - 13, BARRA + 6, c ? Ui.ROJO : Ui.TEXTO2);
            }
        }

        private void enviar() {
            String s = campo.s.toString().trim();
            if (s.isEmpty()) return;
            MovilRed.alServidor(MovilRed.C_MG_COMENTAR, b -> {
                b.writeVarInt(id);
                b.writeUtf(s);
            });
            campo.s.setLength(0);
            MovilCliente.clic();
        }

        @Override
        boolean caracter(char c) {
            return campo.caracter(c);
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                enviar();
                return true;
            }
            return campo.tecla(key);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            MinegramCliente.Post p = MinegramCliente.abiertoId == id ? MinegramCliente.abierto : null;
            int fin = H - ABAJO - ENTRADA;
            if (Ui.dentro(x, y, W - 20, BARRA, 20, CAB) && p != null && p.puedeBorrar) {
                MovilCliente.clic();
                if (Util.getMillis() - confirmar < 3000) {
                    MovilRed.alServidor(MovilRed.C_MG_BORRAR, b -> {
                        b.writeBoolean(false);
                        b.writeVarInt(id);
                        b.writeVarInt(0);
                    });
                    MinegramCliente.quitarDelFeed(id);
                    FotoCache.olvidar(MovilRed.FOTO_MINEGRAM, id);
                    m.atras();
                } else confirmar = Util.getMillis();
                return true;
            }
            if (y >= fin) {
                if (x >= W - 22) enviar();
                return true;
            }
            if (y < Y0) return false;
            for (int[] c : borrarComs)
                if (Ui.dentro(x, y, W - 16, c[1] - 2, 16, 13)) {
                    MovilCliente.clic();
                    if (confirmarCom == c[0] && Util.getMillis() - confirmarComT < 3000) {
                        int com = c[0];
                        MovilRed.alServidor(MovilRed.C_MG_BORRAR, b -> {
                            b.writeBoolean(true);
                            b.writeVarInt(id);
                            b.writeVarInt(com);
                        });
                        confirmarCom = -1;
                    } else {
                        confirmarCom = c[0];
                        confirmarComT = Util.getMillis();
                    }
                    return true;
                }
            for (Zona z : zonas)
                if (Ui.dentro(x, y, z.x(), z.y(), z.w(), z.h()) && z.tipo() != Z.BORRAR) return tocar(this, z, corazones, ultFoto, ultFotoId, true);
            return false;
        }
    }

    // ================================================================== nueva publicación
    static final class Nuevo extends Vista {
        private int foto = -1, esperaCamara = -2;
        private final VistasChat.Campo pie = new VistasChat.Campo(150);
        private static final int PW = 54, PH = 96;

        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_FOTOS, null);
        }

        @Override
        void tick() {
            // al volver de la cámara se elige la última foto hecha (la lista del servidor llega un poco después)
            if (esperaCamara > -2 && !MovilCliente.FOTOS.isEmpty() && MovilCliente.FOTOS.get(0).id() > esperaCamara) {
                foto = MovilCliente.FOTOS.get(0).id();
                esperaCamara = -2;
            }
        }

        @Override
        boolean escribiendo() {
            return true;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int px = 8, py = Y0 + 6;
            Ui.redondo(g, px - 1, py - 1, PW + 2, PH + 2, 3, 0xFF2A333D);
            FotoCache.Tex t = foto >= 0 ? FotoCache.get(MovilRed.FOTO_PROPIA, foto) : null;
            if (t != null) FotoCache.cubrir(g, t, px, py, PW, PH);
            else {
                Ui.rect(g, px, py, PW, PH, 0xFF151B20);
                Ui.glifo(g, Ui.G_FOTO, px + PW / 2 - 4, py + PH / 2 - 10, Ui.TEXTO2);
                for (FormattedCharSequence f : Ui.envolver(t("mg_elige"), PW - 6))
                    g.drawCenteredString(Ui.font(), f, px + PW / 2, py + PH / 2 + 2, 0xFF66717C);
            }
            int bx = px + PW + 6, bw = W - bx - 7;
            boolean sg = Ui.dentro(mx, my, bx, py, bw, 16), sc = Ui.dentro(mx, my, bx, py + 20, bw, 16);
            Ui.redondo(g, bx, py, bw, 16, 8, sg ? 0xFF33404C : 0xFF222A31);
            Ui.glifo(g, Ui.G_FOTO, bx + 5, py + 4, Ui.TEXTO);
            Ui.texto(g, Ui.cortar(t("mg_galeria"), bw - 20), bx + 17, py + 4, Ui.TEXTO);
            Ui.redondo(g, bx, py + 20, bw, 16, 8, sc ? 0xFF33404C : 0xFF222A31);
            Ui.glifo(g, Ui.G_CAMARA, bx + 5, py + 24, Ui.TEXTO);
            Ui.texto(g, Ui.cortar(t("mg_camara"), bw - 20), bx + 17, py + 24, Ui.TEXTO);
            int ty = py + 44;
            g.pose().pushPose();
            g.pose().translate(bx, ty, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            int yy = 0;
            for (FormattedCharSequence f : Ui.font().split(Component.literal(t("mg_privacidad")), bw * 2)) {
                g.drawString(Ui.font(), f, 0, yy, 0xFF66717C, false);
                yy += 9;
            }
            g.pose().popPose();
            // pie
            int cy = py + PH + 8;
            Ui.texto(g, t("mg_pie"), 8, cy, Ui.TEXTO2);
            Ui.redondo(g, 6, cy + 10, W - 12, 34, 5, 0xFF1C242C);
            String s = pie.s.toString();
            int ly = cy + 13;
            List<FormattedCharSequence> ls = Ui.envolver(s.isEmpty() ? " " : s, W - 22);
            int desde = Math.max(0, ls.size() - 3);
            if (s.isEmpty()) Ui.texto(g, t("mg_pie_marcador"), 10, ly, 0xFF66717C);
            int lx = 10;
            for (int i = desde; i < ls.size(); i++) {
                if (!s.isEmpty()) g.drawString(Ui.font(), ls.get(i), 10, ly, Ui.TEXTO, false);
                lx = 10 + Ui.font().width(ls.get(i));
                if (i < ls.size() - 1) ly += 10;
            }
            if ((Util.getMillis() / 500) % 2 == 0) Ui.rect(g, s.isEmpty() ? 10 : lx, ly - 1, 1, 9, AZUL_MG);
            String n = s.length() + "/150";
            Ui.mini(g, n, W - 9 - Ui.anchoMini(n), cy + 1, 0xFF59636D);
            // compartir
            boolean ok = foto >= 0;
            int by = H - ABAJO - 20;
            boolean sb = Ui.dentro(mx, my, 10, by, W - 20, 16);
            Ui.pastilla(g, 10, by, W - 20, 16, t("mg_compartir"), ok ? (sb ? 0xFF2B86E0 : AZUL_MG) : 0xFF26313B, ok ? 0xFFFFFFFF : 0xFF59636D);
            cabeceraMg(this, g, t("mg_nueva"), mx, my);
        }

        @Override
        boolean caracter(char c) {
            return pie.caracter(c);
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            return pie.tecla(key);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int px = 8, py = Y0 + 6, bx = px + PW + 6, bw = W - bx - 7;
            if (Ui.dentro(x, y, bx, py, bw, 16) || Ui.dentro(x, y, px, py, PW, PH)) {
                MovilCliente.clic();
                esperaCamara = -2;
                m.abrir(new VistasFotos.Galeria(id -> foto = id));
                return true;
            }
            if (Ui.dentro(x, y, bx, py + 20, bw, 16)) {
                MovilCliente.clic();
                esperaCamara = MovilCliente.FOTOS.isEmpty() ? 0 : MovilCliente.FOTOS.get(0).id();
                m.abrir(new VistasFotos.Camara());
                return true;
            }
            int by = H - ABAJO - 20;
            if (Ui.dentro(x, y, 10, by, W - 20, 16) && foto >= 0) {
                MovilCliente.clic();
                int f = foto;
                String s = pie.s.toString().trim();
                MovilRed.alServidor(MovilRed.C_MG_PUBLICAR, b -> {
                    b.writeVarInt(f);
                    b.writeUtf(s);
                });
                MinegramCliente.pedirFeed(0, false);
                MinegramCliente.pedirFeed(1, false);
                m.atras();
                return true;
            }
            return false;
        }
    }

    // ================================================================== perfil
    static final class Perfil extends Vista {
        private final UUID id;
        private static final int TW = 43, TH = 43;
        private long confirmar;

        Perfil(UUID id) {
            this.id = id;
        }

        private boolean mio() {
            return id.equals(yo());
        }

        @Override
        void abierta() {
            if (MinegramCliente.perfil != null && !MinegramCliente.perfil.id.equals(id)) MinegramCliente.perfil = null;
            MovilRed.alServidor(MovilRed.C_MG_PERFIL, b -> b.writeUUID(id));
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            MinegramCliente.Perfil p = MinegramCliente.perfil != null && MinegramCliente.perfil.id.equals(id) ? MinegramCliente.perfil : null;
            int fin = finLista();
            recortar(g, Y0, fin);
            int y = Y0 + 6 - (int) scroll;
            // aro de colores + cara
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int c = mezcla(MORADO, NARANJA, (float) ((Math.sin(a) + 1) / 2));
                Ui.circulo(g, 24 + (int) Math.round(Math.cos(a) * 1), y + 18 + (int) Math.round(Math.sin(a) * 1), 18, c);
            }
            Ui.circulo(g, 24, y + 18, 17, Ui.FONDO);
            Ui.cara(g, id, 10, y + 4, 28, Ui.FONDO);
            if (p != null) {
                int[] nums = {p.posts, p.seguidores, p.siguiendo};
                String[] et = {t("mg_n_posts"), t("mg_n_seguidores"), t("mg_n_siguiendo")};
                for (int i = 0; i < 3; i++) {
                    int cx = 62 + i * 28;
                    Ui.centrado(g, Component.literal(Ui.miles(nums[i])).withStyle(ChatFormatting.BOLD).getString(), cx, y + 8, Ui.TEXTO);
                    g.pose().pushPose();
                    g.pose().translate(cx - Ui.ancho(et[i]) / 4F, y + 19, 0);
                    g.pose().scale(0.5F, 0.5F, 1);
                    g.drawString(Ui.font(), et[i], 0, 0, Ui.TEXTO2, false);
                    g.pose().popPose();
                }
                Ui.texto(g, Component.literal(Ui.cortar(p.nombre, W - 16)).withStyle(ChatFormatting.BOLD), 8, y + 41, Ui.TEXTO);
                int by = y + 53;
                if (!mio()) {
                    boolean s = Ui.dentro(mx, my, 8, by, W - 16, 14) && my >= Y0;
                    boolean conf = Util.getMillis() - confirmar < 3000;
                    int col = p.yoSigo ? (conf ? Ui.ROJO : s ? 0xFF33404C : 0xFF26313B) : (s ? 0xFF2B86E0 : AZUL_MG);
                    Ui.pastilla(g, 8, by, W - 16, 14, t(p.yoSigo ? (conf ? "mg_dejar_seguir" : "mg_siguiendo_btn") : "mg_seguir"), col, 0xFFFFFFFF);
                } else Ui.pastilla(g, 8, by, W - 16, 14, t("mg_tu_perfil"), 0xFF26313B, Ui.TEXTO2);
                int gy = by + 20;
                Ui.rect(g, 0, gy - 2, W, 1, Ui.LINEA);
                for (int i = 0; i < p.rejilla.size(); i++) {
                    int x = (i % 3) * (TW + 1), yy = gy + (i / 3) * (TH + 1);
                    if (yy + TH < Y0 || yy > fin) continue;
                    int pid = p.rejilla.get(i)[0];
                    FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_MINEGRAM, pid);
                    if (t != null) FotoCache.cubrir(g, t, x, yy, TW, TH);
                    else Ui.rect(g, x, yy, TW, TH, 0xFF1C232A);
                    if (Ui.dentro(mx, my, x, yy, TW, TH) && my >= Y0 && my < fin) {
                        Ui.rect(g, x, yy, TW, TH, 0x50000000);
                        Ui.glifo(g, Ui.G_CORAZON, x + 8, yy + 17, 0xFFFFFFFF);
                        Ui.texto(g, Integer.toString(p.rejilla.get(i)[1]), x + 19, yy + 18, 0xFFFFFFFF);
                    }
                }
                int filas = (p.rejilla.size() + 2) / 3;
                if (p.rejilla.isEmpty()) Ui.centrado(g, t("mg_sin_posts"), W / 2, gy + 16, 0xFF59636D);
                scrollMax = gy + filas * (TH + 1) + 4 + (int) scroll - fin;
            } else Ui.centrado(g, t("cargando"), W / 2, y + 60, Ui.TEXTO2);
            barraScroll(g, Y0, fin);
            soltarRecorte(g);
            cabeceraMg(this, g, p == null ? "" : p.nombre, mx, my);
            nav(g, mio() ? T_YO : -1, mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (clicNav(this, x, y, mio() ? T_YO : -1)) return true;
            MinegramCliente.Perfil p = MinegramCliente.perfil != null && MinegramCliente.perfil.id.equals(id) ? MinegramCliente.perfil : null;
            if (p == null || y < Y0) return false;
            int yb = Y0 + 6 - (int) scroll + 53;
            if (!mio() && Ui.dentro(x, y, 8, yb, W - 16, 14)) {
                MovilCliente.clic();
                boolean seguir;
                if (p.yoSigo) {
                    if (Util.getMillis() - confirmar >= 3000) {
                        confirmar = Util.getMillis();
                        return true;
                    }
                    seguir = false;
                } else seguir = true;
                confirmar = 0;
                p.yoSigo = seguir;
                p.seguidores += seguir ? 1 : -1;
                MovilRed.alServidor(MovilRed.C_MG_SEGUIR, b -> {
                    b.writeUUID(id);
                    b.writeBoolean(seguir);
                });
                return true;
            }
            int gy = yb + 20;
            for (int i = 0; i < p.rejilla.size(); i++) {
                int gx = (i % 3) * (TW + 1), yy = gy + (i / 3) * (TH + 1);
                if (Ui.dentro(x, y, gx, yy, TW, TH)) {
                    MovilCliente.clic();
                    m.abrir(new PostV(p.rejilla.get(i)[0]));
                    return true;
                }
            }
            return false;
        }
    }

    // ================================================================== personas
    static final class Personas extends Vista {
        private static final int F = 24;

        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_SYNC, null);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int fin = finLista();
            recortar(g, Y0, fin);
            int y = Y0 - (int) scroll;
            List<MovilCliente.Contacto> l = MovilCliente.CONTACTOS;
            for (MovilCliente.Contacto k : l) {
                if (y + F > Y0 && y < fin) {
                    if (Ui.dentro(mx, my, 0, y, W, F) && my >= Y0 && my < fin) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                    Ui.circulo(g, 14, y + 12, 9, ROSA);
                    Ui.circulo(g, 14, y + 12, 8, Ui.FONDO);
                    Ui.cara(g, k.id(), 7, y + 5, 14, Ui.FONDO);
                    Ui.texto(g, Component.literal(Ui.cortar(k.nombre(), W - 50)).withStyle(ChatFormatting.BOLD), 28, y + 4, Ui.TEXTO);
                    Ui.texto(g, t(k.conectado() ? "en_linea" : "desconectado"), 28, y + 13, k.conectado() ? Ui.ACENTO : Ui.TEXTO2);
                    Ui.glifo(g, Ui.G_DERECHA, W - 12, y + 8, Ui.TEXTO2);
                }
                y += F;
            }
            scrollMax = l.size() * F - (fin - Y0);
            barraScroll(g, Y0, fin);
            soltarRecorte(g);
            if (l.isEmpty()) Ui.centrado(g, t(MovilCliente.sincronizado ? "sin_contactos" : "cargando"), W / 2, H / 2, Ui.TEXTO2);
            cabeceraMg(this, g, t("mg_personas"), mx, my);
            nav(g, T_PERSONAS, mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (clicNav(this, x, y, T_PERSONAS)) return true;
            if (y < Y0 || y >= finLista()) return false;
            int i = (int) ((y - Y0 + scroll) / F);
            if (i < 0 || i >= MovilCliente.CONTACTOS.size()) return false;
            MovilCliente.clic();
            m.abrir(new Perfil(MovilCliente.CONTACTOS.get(i).id()));
            return true;
        }
    }

    // ================================================================== notificaciones
    static final class Notis extends Vista {
        private static final int F = 26;

        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_MG_NOTIS, null);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int fin = finLista();
            recortar(g, Y0, fin);
            int y = Y0 - (int) scroll;
            List<MinegramCliente.Noti> l = MinegramCliente.NOTIS;
            for (MinegramCliente.Noti n : l) {
                if (y + F > Y0 && y < fin) {
                    if (n.nueva()) Ui.rect(g, 0, y, W, F, 0x14E1306C);
                    if (Ui.dentro(mx, my, 0, y, W, F) && my >= Y0 && my < fin) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                    Ui.cara(g, n.de(), 6, y + 5, 16, n.nueva() ? 0xFF1F1A20 : Ui.FONDO);
                    int tw = W - 28 - (n.existe() ? 24 : 4);
                    String accion = switch (n.tipo()) {
                        case 0 -> t("mg_n_like");
                        case 1 -> t("mg_n_comentario", n.texto());
                        default -> t("mg_n_seguir");
                    };
                    Component c = Component.literal(n.nombre()).withStyle(ChatFormatting.BOLD).append(Component.literal(" " + accion).withStyle(ChatFormatting.RESET));
                    List<FormattedCharSequence> ls = Ui.font().split(c, tw);
                    g.drawString(Ui.font(), ls.get(0), 26, y + 4, Ui.TEXTO, false);
                    String h = hace(n.t());
                    if (ls.size() > 1) {
                        g.pose().pushPose();
                        g.pose().translate(26, y + 14, 0);
                        g.pose().scale(0.5F, 0.5F, 1);
                        g.drawString(Ui.font(), ls.get(1), 0, 0, Ui.TEXTO2, false);
                        g.drawString(Ui.font(), h, 0, 9, 0xFF66717C, false);
                        g.pose().popPose();
                    } else Ui.texto(g, h, 26, y + 14, 0xFF66717C);
                    int icono = n.tipo() == 0 ? Ui.G_CORAZON : n.tipo() == 1 ? Ui.G_COMENTARIO : Ui.G_SEGUIR;
                    if (n.existe()) {
                        FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_MINEGRAM, n.post());
                        if (t != null) FotoCache.cubrir(g, t, W - 24, y + 3, 20, 20);
                        else Ui.rect(g, W - 24, y + 3, 20, 20, 0xFF1C232A);
                    }
                    Ui.circulo(g, 21, y + 20, 4, n.tipo() == 0 ? ROSA : n.tipo() == 1 ? AZUL_MG : 0xFF8A3AB9);
                    g.pose().pushPose();
                    g.pose().translate(19, y + 18, 0);
                    g.pose().scale(0.5F, 0.5F, 1);
                    Ui.glifo(g, icono, 0, 0, 0xFFFFFFFF);
                    g.pose().popPose();
                }
                y += F;
            }
            scrollMax = l.size() * F - (fin - Y0);
            barraScroll(g, Y0, fin);
            soltarRecorte(g);
            if (l.isEmpty()) Ui.centrado(g, t(MinegramCliente.notisCargadas ? "mg_sin_notis" : "cargando"), W / 2, H / 2 - 4, Ui.TEXTO2);
            cabeceraMg(this, g, t("mg_notificaciones"), mx, my);
            nav(g, T_NOTIS, mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (clicNav(this, x, y, T_NOTIS)) return true;
            if (y < Y0 || y >= finLista()) return false;
            int i = (int) ((y - Y0 + scroll) / F);
            if (i < 0 || i >= MinegramCliente.NOTIS.size()) return false;
            MinegramCliente.Noti n = MinegramCliente.NOTIS.get(i);
            MovilCliente.clic();
            if (n.existe() && n.tipo() != MovilCliente.MG_SEGUIR) m.abrir(new PostV(n.post()));
            else m.abrir(new Perfil(n.de()));
            return true;
        }
    }
}
