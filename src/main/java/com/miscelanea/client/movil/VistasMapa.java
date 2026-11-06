package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mapa y GPS: el mapa explorado alrededor del jugador (arrastrar = mover, rueda o +/- = zoom), la flecha del jugador con
 * su rumbo, los puntos guardados, los amigos que te comparten su ubicación y "Navegar" (flecha en el HUD con el móvil
 * cerrado). También la pantalla de compartir tu ubicación en tiempo real.
 */
final class VistasMapa {
    private VistasMapa() {}

    static final int Y0 = VistasChat.Y0;
    static final float[] ZOOMS = {0.5F, 1, 2, 4};

    static double dist(LocalPlayer p, double x, double z) {
        return Math.sqrt((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ()));
    }

    // ================================================================== el mapa
    static final class Mapa extends Vista {
        private int zoom = 1;
        private boolean seguir = true;
        private double cx, cz;
        /** Seleccionado: un punto o un amigo (para la tarjeta de abajo). */
        private Waypoints.Punto sel;
        private UUID selAmigo;
        private long confirmar;
        private final UUID centrarEn;

        Mapa() {
            this(null);
        }

        /** Abre el mapa centrado en un amigo que comparte su ubicación. */
        Mapa(UUID amigo) {
            centrarEn = amigo;
            selAmigo = amigo;
            if (amigo != null) seguir = false;
        }

        @Override
        int fondoBarra() {
            return 0xC01A2129;
        }

        @Override
        void abierta() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null && (seguir || cx == 0 && cz == 0)) {
                cx = p.getX();
                cz = p.getZ();
            }
            if (centrarEn != null) {
                MovilCliente.UbiAmigo u = MovilCliente.ubiDe(centrarEn);
                if (u != null && u.dim().equals(Waypoints.dimActual())) {
                    cx = u.x();
                    cz = u.z();
                }
            }
        }

        private float esc() {
            return ZOOMS[zoom];
        }

        private int sx(double x) {
            return (int) Math.round(W / 2.0 + (x - cx) * esc());
        }

        private int sy(double z) {
            return (int) Math.round(MAPA_Y + MAPA_H / 2.0 + (z - cz) * esc());
        }

        private static final int MAPA_Y = BARRA, MAPA_H = H - ABAJO - BARRA;

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            LocalPlayer p = Minecraft.getInstance().player;
            Ui.rect(g, 0, 0, W, H, 0xFF141A1F);
            if (p == null) return;
            if (seguir) {
                cx = Mth.lerp(pt, p.xo, p.getX());
                cz = Mth.lerp(pt, p.zo, p.getZ());
            }
            // cuadrícula de fondo (lo no explorado)
            for (int y = 0; y < H; y += 8) for (int x = (y / 8) % 2 * 4; x < W; x += 8) Ui.rect(g, x, y, 1, 1, 0xFF1E262D);
            recortar(g, MAPA_Y, MAPA_Y + MAPA_H);
            MapaCache.dibujar(g, cx, cz, esc(), 0, MAPA_Y, W, MAPA_H);
            String dim = Waypoints.dimActual();
            // puntos guardados
            for (Waypoints.Punto w : Waypoints.PUNTOS) {
                if (!w.dim.equals(dim)) continue;
                int x = sx(w.x + 0.5), y = sy(w.z + 0.5);
                if (x < -10 || x > W + 10 || y < MAPA_Y - 10 || y > MAPA_Y + MAPA_H + 10) continue;
                boolean s = w == sel;
                Ui.glifo(g, Ui.G_PIN, x - 4, y - 7, 0xA0000000);
                Ui.glifo(g, Ui.G_PIN, x - 4, y - 8, w.color);
                if (s || zoom >= 2) {
                    String n = Ui.cortar(w.nombre, 60);
                    Ui.redondo(g, x - Ui.ancho(n) / 2 - 2, y + 2, Ui.ancho(n) + 4, 10, 3, 0xB0101418);
                    Ui.centrado(g, n, x, y + 3, s ? 0xFFFFFFFF : 0xFFD0D8E0);
                }
            }
            // amigos que me comparten su ubicación
            for (MovilCliente.UbiAmigo u : MovilCliente.UBI_LE) {
                if (!u.dim().equals(dim)) continue;
                int x = sx(u.x()), y = sy(u.z());
                boolean s = u.id().equals(selAmigo);
                Ui.circulo(g, x, y, 7, s ? 0xFFFFFFFF : Ui.AZUL);
                Ui.cara(g, u.id(), x - 5, y - 5, 10, s ? 0xFFFFFFFF : Ui.AZUL);
                if (s || zoom >= 2) {
                    String n = Ui.cortar(u.nombre(), 60);
                    Ui.redondo(g, x - Ui.ancho(n) / 2 - 2, y + 9, Ui.ancho(n) + 4, 10, 3, 0xB0101418);
                    Ui.centrado(g, n, x, y + 10, 0xFFFFFFFF);
                }
            }
            // yo
            int px = sx(Mth.lerp(pt, p.xo, p.getX())), py = sy(Mth.lerp(pt, p.zo, p.getZ()));
            float yaw = Mth.lerp(pt, p.yRotO, p.getYRot());
            Ui.circulo(g, px, py, 9, 0x304EA4F6);
            Ui.flecha(g, px, py, 6, Math.toRadians(yaw + 180), 0xFFFFFFFF, 0xFF1A5FB0);
            soltarRecorte(g);
            // barra de arriba: coordenadas
            Ui.rect(g, 0, BARRA, W, 14, 0xB0101418);
            Ui.glifo(g, Ui.G_ATRAS, 4, BARRA + 3, Ui.dentro(mx, my, 0, BARRA, 16, 14) ? 0xFFFFFFFF : Ui.TEXTO);
            String c = String.format("X %d  Y %d  Z %d", p.getBlockX(), p.getBlockY(), p.getBlockZ());
            Ui.texto(g, c, 16, BARRA + 3, Ui.TEXTO);
            String zs = zoom == 0 ? "1:2" : (int) esc() + ":1";
            Ui.mini(g, zs, W - 5 - Ui.anchoMini(zs), BARRA + 5, Ui.TEXTO2);
            // botones de la derecha
            int[] gl = {Ui.G_MAS, Ui.G_MENOS, Ui.G_CENTRAR, Ui.G_LISTA, Ui.G_PIN};
            for (int i = 0; i < gl.length; i++) {
                int bx = W - 12, by = BARRA + 26 + i * 20;
                boolean s = Ui.enCirculo(mx, my, bx, by, 8);
                Ui.circulo(g, bx, by, 8, s ? 0xF0303A44 : 0xE0202830);
                Ui.glifo(g, gl[i], bx - 4, by - 4, i == 2 && seguir ? Ui.AZUL : i == 4 ? Ui.ROJO : Ui.TEXTO);
                if (i == 4) Ui.rect(g, bx + 2, by + 3, 3, 1, Ui.TEXTO);
            }
            // tarjeta del seleccionado o de la navegación
            tarjeta(g, p, mx, my);
            if (!MapaCache.explorado(p.getBlockX(), p.getBlockZ())) Ui.centrado(g, t("mapa_explorando"), W / 2, MAPA_Y + 18, Ui.TEXTO2);
        }

        private static final int TY = H - ABAJO - 34;

        private void tarjeta(GuiGraphics g, LocalPlayer p, int mx, int my) {
            String nombre = null, sub = null;
            int color = Ui.AZUL;
            boolean navegandoEste = false, borrable = false;
            UUID cara = null;
            if (sel != null) {
                nombre = sel.nombre;
                color = sel.color;
                boolean misma = sel.dim.equals(Waypoints.dimActual());
                sub = misma ? Waypoints.distancia(dist(p, sel.x + 0.5, sel.z + 0.5)) + " · " + sel.x + ", " + sel.y + ", " + sel.z : t("otra_dimension");
                navegandoEste = Waypoints.destino == sel;
                borrable = true;
            } else if (selAmigo != null) {
                MovilCliente.UbiAmigo u = MovilCliente.ubiDe(selAmigo);
                if (u == null) {
                    selAmigo = null;
                    return;
                }
                nombre = u.nombre();
                cara = u.id();
                boolean misma = u.dim().equals(Waypoints.dimActual());
                sub = (misma ? Waypoints.distancia(dist(p, u.x(), u.z())) : t("otra_dimension")) + " · " + t("ubi_quedan", MovilCliente.duracion(u.restan()));
                navegandoEste = selAmigo.equals(Waypoints.destinoAmigo);
            } else if (Waypoints.navegando()) {
                double[] o = Waypoints.objetivo();
                nombre = t("navegando_a", Waypoints.nombreObjetivo());
                sub = o == null ? "" : o[2] != 0 ? t("otra_dimension") : Waypoints.distancia(dist(p, o[0], o[1]));
                color = Waypoints.colorObjetivo();
                navegandoEste = true;
            }
            if (nombre == null) return;
            Ui.redondo(g, 4, TY, W - 8, 30, 6, 0xF0161C22);
            if (cara != null) Ui.cara(g, cara, 9, TY + 7, 16, 0xFF161C22);
            else {
                Ui.circulo(g, 17, TY + 15, 8, 0xFF222A32);
                Ui.glifo(g, navegandoEste && sel == null ? Ui.G_NAVEGAR : Ui.G_PIN, 13, TY + 11, color);
            }
            int bw = 36;
            Ui.texto(g, Ui.cortar(nombre, W - 40 - bw - (borrable ? 14 : 0)), 30, TY + 6, Ui.TEXTO);
            g.pose().pushPose();
            g.pose().translate(30, TY + 18, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.drawString(Ui.font(), Ui.cortar(sub, (W - 40 - bw) * 2), 0, 0, Ui.TEXTO2, false);
            g.pose().popPose();
            int bx = W - 8 - bw - (borrable ? 14 : 0);
            boolean sb = Ui.dentro(mx, my, bx, TY + 7, bw, 16);
            Ui.pastilla(g, bx, TY + 7, bw, 16, t(navegandoEste ? "parar" : "navegar"), navegandoEste ? (sb ? 0xFFD94040 : Ui.ROJO) : (sb ? 0xFF3A8FE0 : Ui.AZUL), 0xFFFFFFFF);
            if (borrable) {
                boolean conf = Util.getMillis() - confirmar < 3000;
                Ui.glifo(g, Ui.G_BORRAR, W - 19, TY + 11, conf ? Ui.ROJO : Ui.TEXTO2);
            }
        }

        @Override
        void arrastrar(double x, double y, double dx, double dy) {
            seguir = false;
            cx -= dx / esc();
            cz -= dy / esc();
        }

        @Override
        void rueda(double d) {
            zoom = Mth.clamp(zoom + (d > 0 ? 1 : -1), 0, ZOOMS.length - 1);
        }

        @Override
        boolean clic(double x, double y) {
            if (Ui.dentro(x, y, 0, BARRA, 16, 14)) {
                MovilCliente.clic();
                m.atras();
                return true;
            }
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return false;
            for (int i = 0; i < 5; i++) {
                if (!Ui.enCirculo(x, y, W - 12, BARRA + 26 + i * 20, 9)) continue;
                MovilCliente.clic();
                switch (i) {
                    case 0 -> zoom = Math.min(ZOOMS.length - 1, zoom + 1);
                    case 1 -> zoom = Math.max(0, zoom - 1);
                    case 2 -> {
                        seguir = true;
                        sel = null;
                        selAmigo = null;
                    }
                    case 3 -> m.abrir(new Puntos(this));
                    default -> m.abrir(new NuevoPunto());
                }
                return true;
            }
            // tarjeta
            boolean hayTarjeta = sel != null || selAmigo != null || Waypoints.navegando();
            if (hayTarjeta && Ui.dentro(x, y, 4, TY, W - 8, 30)) {
                int bw = 36, bx = W - 8 - bw - (sel != null ? 14 : 0);
                if (Ui.dentro(x, y, bx, TY + 7, bw, 16)) {
                    MovilCliente.clic();
                    if (sel != null) {
                        if (Waypoints.destino == sel) Waypoints.parar();
                        else Waypoints.navegar(sel);
                    } else if (selAmigo != null) {
                        if (selAmigo.equals(Waypoints.destinoAmigo)) Waypoints.parar();
                        else Waypoints.navegar(selAmigo);
                    } else Waypoints.parar();
                    return true;
                }
                if (sel != null && x >= W - 22) {
                    MovilCliente.clic();
                    if (Util.getMillis() - confirmar < 3000) {
                        if (Waypoints.destino == sel) Waypoints.parar();
                        Waypoints.PUNTOS.remove(sel);
                        Waypoints.guardar();
                        sel = null;
                    } else confirmar = Util.getMillis();
                    return true;
                }
                return true;
            }
            // tocar un marcador
            String dim = Waypoints.dimActual();
            for (MovilCliente.UbiAmigo u : MovilCliente.UBI_LE)
                if (u.dim().equals(dim) && Ui.enCirculo(x, y, sx(u.x()), sy(u.z()), 8)) {
                    MovilCliente.clic();
                    selAmigo = u.id();
                    sel = null;
                    return true;
                }
            for (Waypoints.Punto w : Waypoints.PUNTOS)
                if (w.dim.equals(dim) && Ui.dentro(x, y, sx(w.x + 0.5) - 5, sy(w.z + 0.5) - 9, 10, 11)) {
                    MovilCliente.clic();
                    sel = w;
                    selAmigo = null;
                    confirmar = 0;
                    return true;
                }
            sel = null;
            selAmigo = null;
            return false;
        }

        void centrar(Waypoints.Punto w) {
            sel = w;
            selAmigo = null;
            if (w.dim.equals(Waypoints.dimActual())) {
                seguir = false;
                cx = w.x + 0.5;
                cz = w.z + 0.5;
            }
        }

        void centrar(UUID amigo) {
            sel = null;
            selAmigo = amigo;
            MovilCliente.UbiAmigo u = MovilCliente.ubiDe(amigo);
            if (u != null && u.dim().equals(Waypoints.dimActual())) {
                seguir = false;
                cx = u.x();
                cz = u.z();
            }
        }
    }

    // ================================================================== lista de puntos y amigos
    static final class Puntos extends Vista {
        private final Mapa mapa;
        private static final int F = 24;

        Puntos(Mapa mapa) {
            this.mapa = mapa;
        }

        private int filas() {
            return MovilCliente.UBI_LE.size() + Waypoints.PUNTOS.size();
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            LocalPlayer p = Minecraft.getInstance().player;
            recortar(g, Y0, H - ABAJO);
            int y = Y0 - (int) scroll;
            String dim = Waypoints.dimActual();
            for (MovilCliente.UbiAmigo u : MovilCliente.UBI_LE) {
                if (Ui.dentro(mx, my, 0, y, W, F) && my >= Y0) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                Ui.cara(g, u.id(), 7, y + 4, 16, Ui.FONDO);
                Ui.texto(g, Ui.cortar(u.nombre(), W - 50), 29, y + 4, Ui.TEXTO);
                String sub = (u.dim().equals(dim) && p != null ? Waypoints.distancia(dist(p, u.x(), u.z())) : t("otra_dimension")) + " · "
                        + t("ubi_quedan", MovilCliente.duracion(u.restan()));
                Ui.texto(g, Ui.cortar(sub, W - 50), 29, y + 13, Ui.AZUL);
                Ui.glifo(g, Ui.G_NAVEGAR, W - 14, y + 8, u.id().equals(Waypoints.destinoAmigo) ? Ui.ACENTO : Ui.TEXTO2);
                Ui.rect(g, 29, y + F - 1, W - 29, 1, Ui.LINEA);
                y += F;
            }
            for (Waypoints.Punto w : Waypoints.PUNTOS) {
                if (Ui.dentro(mx, my, 0, y, W, F) && my >= Y0) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                Ui.circulo(g, 15, y + 12, 8, 0xFF1C242C);
                Ui.glifo(g, Ui.G_PIN, 11, y + 8, w.color);
                Ui.texto(g, Ui.cortar(w.nombre, W - 50), 29, y + 4, Ui.TEXTO);
                String sub = (w.dim.equals(dim) && p != null ? Waypoints.distancia(dist(p, w.x + 0.5, w.z + 0.5)) : t("otra_dimension")) + " · " + w.x + " " + w.y + " " + w.z;
                Ui.texto(g, Ui.cortar(sub, W - 50), 29, y + 13, Ui.TEXTO2);
                Ui.glifo(g, Ui.G_NAVEGAR, W - 14, y + 8, Waypoints.destino == w ? Ui.ACENTO : Ui.TEXTO2);
                Ui.rect(g, 29, y + F - 1, W - 29, 1, Ui.LINEA);
                y += F;
            }
            scrollMax = filas() * F - (H - ABAJO - Y0);
            barraScroll(g, Y0, H - ABAJO);
            soltarRecorte(g);
            if (filas() == 0) {
                int yy = H / 2 - 14;
                for (FormattedCharSequence f : Ui.envolver(t("mapa_sin_puntos"), W - 24)) {
                    g.drawCenteredString(Ui.font(), f, W / 2, yy, Ui.TEXTO2);
                    yy += 10;
                }
            }
            cabecera(g, t("mapa_puntos"), mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (y < Y0) return false;
            int i = (int) ((y - Y0 + scroll) / F);
            int na = MovilCliente.UBI_LE.size();
            if (i < 0 || i >= filas()) return false;
            MovilCliente.clic();
            boolean nav = x >= W - 22;
            if (i < na) {
                UUID u = MovilCliente.UBI_LE.get(i).id();
                if (nav) {
                    if (u.equals(Waypoints.destinoAmigo)) Waypoints.parar();
                    else Waypoints.navegar(u);
                    return true;
                }
                mapa.centrar(u);
            } else {
                Waypoints.Punto w = Waypoints.PUNTOS.get(i - na);
                if (nav) {
                    if (Waypoints.destino == w) Waypoints.parar();
                    else Waypoints.navegar(w);
                    return true;
                }
                mapa.centrar(w);
            }
            m.atras();
            return true;
        }
    }

    // ================================================================== nuevo punto
    static final class NuevoPunto extends Vista {
        private final VistasChat.Campo nombre = new VistasChat.Campo(24);
        private int color;
        private int x, y, z;

        @Override
        void abierta() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                x = p.getBlockX();
                y = p.getBlockY();
                z = p.getBlockZ();
            }
            color = Waypoints.PUNTOS.size() % Waypoints.COLORES.length;
        }

        @Override
        boolean escribiendo() {
            return true;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int yy = Y0 + 8;
            Ui.texto(g, t("mapa_nombre"), 10, yy, Ui.TEXTO2);
            Ui.redondo(g, 8, yy + 11, W - 16, 15, 7, 0xFF2A333D);
            nombre.dibujar(g, 15, yy + 15, W - 30, t("mapa_nombre_marcador"), true);
            yy += 34;
            Ui.texto(g, t("mapa_color"), 10, yy, Ui.TEXTO2);
            for (int i = 0; i < Waypoints.COLORES.length; i++) {
                int cx = 18 + i * 19, cy = yy + 20;
                if (i == color) Ui.circulo(g, cx, cy, 8, 0xFFFFFFFF);
                Ui.circulo(g, cx, cy, 6, Waypoints.COLORES[i]);
            }
            yy += 36;
            Ui.texto(g, t("mapa_posicion"), 10, yy, Ui.TEXTO2);
            Ui.redondo(g, 8, yy + 11, W - 16, 22, 6, Ui.TARJETA);
            Ui.glifo(g, Ui.G_PIN, 14, yy + 18, Waypoints.COLORES[color]);
            Ui.texto(g, x + ", " + y + ", " + z, 27, yy + 15, Ui.TEXTO);
            g.pose().pushPose();
            g.pose().translate(27, yy + 25, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.drawString(Ui.font(), t("mapa_aqui"), 0, 0, Ui.TEXTO2, false);
            g.pose().popPose();
            int by = H - ABAJO - 20;
            boolean ok = nombre.s.toString().trim().length() > 0;
            boolean sb = Ui.dentro(mx, my, 10, by, W - 20, 16);
            Ui.pastilla(g, 10, by, W - 20, 16, t("mapa_guardar"), ok ? (sb ? 0xFF2FBF73 : Ui.ACENTO) : 0xFF26313B, ok ? 0xFF0F1419 : 0xFF59636D);
            cabecera(g, t("mapa_nuevo"), mx, my);
        }

        private void guardar() {
            String n = nombre.s.toString().trim();
            if (n.isEmpty()) return;
            Waypoints.Punto w = new Waypoints.Punto();
            w.nombre = n;
            w.color = Waypoints.COLORES[color];
            w.x = x;
            w.y = y;
            w.z = z;
            w.dim = Waypoints.dimActual();
            Waypoints.PUNTOS.add(w);
            Waypoints.guardar();
            MovilCliente.clic();
            m.atras();
        }

        @Override
        boolean caracter(char c) {
            return nombre.caracter(c);
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                guardar();
                return true;
            }
            return nombre.tecla(key);
        }

        @Override
        boolean clic(double xx, double yy) {
            if (clicAtras(xx, yy)) return true;
            int cyy = Y0 + 8 + 34 + 20;
            for (int i = 0; i < Waypoints.COLORES.length; i++)
                if (Ui.enCirculo(xx, yy, 18 + i * 19, cyy, 8)) {
                    color = i;
                    MovilCliente.clic();
                    return true;
                }
            if (Ui.dentro(xx, yy, 10, H - ABAJO - 20, W - 20, 16)) {
                guardar();
                return true;
            }
            return false;
        }
    }

    // ================================================================== compartir mi ubicación
    static final class Compartir extends Vista {
        private final List<UUID> con;
        private final String nombre;
        private static final int[] MINUTOS = {15, 30, 60};

        Compartir(List<UUID> con, String nombre) {
            this.con = new ArrayList<>(con);
            this.nombre = nombre;
        }

        private boolean compartiendo() {
            for (UUID u : con) if (MovilCliente.ubiMia(u) != null) return true;
            return false;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int y = Y0 + 8;
            // dibujo: un pin grande con ondas
            int cx = W / 2, cy = y + 22;
            long t = Util.getMillis() % 1600;
            for (int k = 0; k < 2; k++) {
                float f = ((t + k * 800) % 1600) / 1600F;
                int r = 8 + (int) (f * 14), a = (int) ((1 - f) * 120);
                Ui.circulo(g, cx, cy + 4, r, (a << 24) | 0x4EA4F6);
            }
            g.pose().pushPose();
            g.pose().translate(cx - 9, cy - 10, 0);
            g.pose().scale(2, 2, 1);
            Ui.glifo(g, Ui.G_PIN, 0, 0, Ui.AZUL);
            g.pose().popPose();
            y += 50;
            for (FormattedCharSequence f : Ui.envolver(t("ubi_explica", nombre), W - 20)) {
                g.drawCenteredString(Ui.font(), f, W / 2, y, Ui.TEXTO2);
                y += 10;
            }
            y += 6;
            if (!MovilCliente.ubicacion) {
                Ui.centrado(g, t("ubi_desactivada"), W / 2, y, Ui.ROJO);
                cabecera(g, t("ubi_titulo"), mx, my);
                return;
            }
            for (int i = 0; i < MINUTOS.length; i++) {
                int by = y + i * 20;
                boolean s = Ui.dentro(mx, my, 14, by, W - 28, 16);
                Ui.pastilla(g, 14, by, W - 28, 16, t("ubi_minutos", MINUTOS[i]), s ? 0xFF33404C : 0xFF222A31, Ui.TEXTO);
            }
            y += 64;
            if (compartiendo()) {
                int quedan = 0;
                for (UUID u : con) {
                    MovilCliente.UbiMia um = MovilCliente.ubiMia(u);
                    if (um != null) quedan = Math.max(quedan, um.restan());
                }
                Ui.centrado(g, t("ubi_compartiendo", MovilCliente.duracion(quedan)), W / 2, y, Ui.ACENTO);
                boolean s = Ui.dentro(mx, my, 14, y + 12, W - 28, 16);
                Ui.pastilla(g, 14, y + 12, W - 28, 16, t("ubi_parar"), s ? 0xFFD94040 : Ui.ROJO, 0xFFFFFFFF);
            }
            cabecera(g, t("ubi_titulo"), mx, my);
        }

        private int y0Opciones() {
            int y = Y0 + 8 + 50;
            y += Ui.envolver(t("ubi_explica", nombre), W - 20).size() * 10 + 6;
            return y;
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (!MovilCliente.ubicacion) return false;
            int y0 = y0Opciones();
            for (int i = 0; i < MINUTOS.length; i++)
                if (Ui.dentro(x, y, 14, y0 + i * 20, W - 28, 16)) {
                    int min = MINUTOS[i];
                    MovilRed.alServidor(MovilRed.C_UBI_COMPARTIR, b -> {
                        b.writeVarInt(min);
                        b.writeVarInt(con.size());
                        for (UUID u : con) b.writeUUID(u);
                    });
                    MovilCliente.clic();
                    MovilCliente.aviso(Component.translatable("movil.miscelanea.ubi_compartida", min));
                    m.atras();
                    return true;
                }
            if (compartiendo() && Ui.dentro(x, y, 14, y0 + 64 + 12, W - 28, 16)) {
                for (UUID u : con)
                    MovilRed.alServidor(MovilRed.C_UBI_PARAR, b -> {
                        b.writeBoolean(false);
                        b.writeUUID(u);
                    });
                MovilCliente.clic();
                return true;
            }
            return false;
        }
    }

    /** Cabecera de un chat individual: distancia y flecha hacia el amigo si te comparte su ubicación. */
    static int indicador(GuiGraphics g, UUID otro, int xDer, int y) {
        MovilCliente.UbiAmigo u = otro == null ? null : MovilCliente.ubiDe(otro);
        LocalPlayer p = Minecraft.getInstance().player;
        if (u == null || p == null) return 0;
        boolean misma = u.dim().equals(Waypoints.dimActual());
        String s = misma ? Waypoints.distancia(dist(p, u.x(), u.z())) : t("otra_dim_corto");
        int w = Ui.ancho(s) + (misma ? 13 : 4);
        int x = xDer - w;
        Ui.redondo(g, x, y, w, 11, 5, 0xFF16324A);
        if (misma) Ui.flecha(g, x + 6, y + 5, 4, Waypoints.angulo(p, u.x(), u.z()), Ui.AZUL, 0);
        Ui.texto(g, s, x + (misma ? 11 : 2), y + 2, 0xFFBFE0FF);
        return w;
    }

    static String t(String k, Object... a) {
        return Component.translatable("movil.miscelanea." + k, a).getString();
    }

    static Component negrita(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }
}
