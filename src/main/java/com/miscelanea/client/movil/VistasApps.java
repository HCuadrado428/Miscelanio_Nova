package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Pantalla de inicio y apps sencillas: reloj con alarma, linterna y ajustes. */
final class VistasApps {
    private VistasApps() {}

    static final int FONDOS = 4;

    static ResourceLocation fondoTex(int i) {
        return new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/movil/fondo_" + Math.floorMod(i, FONDOS) + ".png");
    }

    static void interruptor(GuiGraphics g, int x, int y, boolean on) {
        Ui.redondo(g, x, y, 20, 11, 5, on ? Ui.ACENTO : 0xFF3A434D);
        Ui.circulo(g, on ? x + 14 : x + 5, y + 5, 4, 0xFFFFFFFF);
    }

    // ================================================================== inicio
    static final class Inicio extends Vista {
        /** apps de la cuadrícula y del dock. */
        private static final int[] REJILLA = {Ui.APP_MINEGRAM, Ui.APP_MAPA, Ui.APP_BANCO, Ui.APP_AGENDA, Ui.APP_RELOJ, Ui.APP_LINTERNA, Ui.APP_AJUSTES},
                DOCK = {Ui.APP_LLAMAR, Ui.APP_CHATS, Ui.APP_CAMARA, Ui.APP_FOTOS};
        private static final String[] NOMBRES = {"app_chats", "app_llamar", "app_agenda", "app_camara", "app_fotos", "app_reloj", "app_linterna", "app_ajustes",
                "app_minegram", "app_mapa", "app_banco"};

        @Override
        int fondoBarra() {
            return 0;
        }

        private static int rx(int i) {
            return 22 + (i % 3) * 44;
        }

        private static int ry(int i) {
            return 56 + (i / 3) * 42;
        }

        private static int dx(int i) {
            return 21 + i * 30;
        }

        private static final int DY = H - 40;

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            g.blit(fondoTex(MovilCliente.fondo), 0, 0, 0, 0, W, H, W, H);
            // reloj y fecha
            Ui.grande(g, MovilCliente.hora(), W / 2, 22, 2, 0xFFFFFFFF);
            var l = Minecraft.getInstance().level;
            String fecha = t("dia", MovilCliente.dia()) + " · " + t(l == null ? "luna0" : "luna" + l.getMoonPhase());
            g.drawCenteredString(Ui.font(), fecha, W / 2, 42, 0xFFE8E8F0);
            for (int i = 0; i < REJILLA.length; i++) app(g, REJILLA[i], rx(i), ry(i), mx, my, true);
            Ui.redondo(g, 6, DY - 5, W - 12, 34, 10, 0x50FFFFFF);
            for (int i = 0; i < DOCK.length; i++) app(g, DOCK[i], dx(i) + 4, DY, mx, my, false);
        }

        private void app(GuiGraphics g, int app, int cx, int y, int mx, int my, boolean etiqueta) {
            Ui.icono(g, app, cx - 12, y);
            if (Ui.dentro(mx, my, cx - 12, y, 24, 24)) Ui.redondo(g, cx - 12, y, 24, 24, 6, 0x40000000);
            if (etiqueta) g.drawCenteredString(Ui.font(), t(NOMBRES[app]), cx, y + 27, 0xFFFFFFFF);
            int n = app == Ui.APP_CHATS ? MovilCliente.noLeidos() : app == Ui.APP_LLAMAR ? MovilCliente.perdidas()
                    : app == Ui.APP_MINEGRAM ? MinegramCliente.noVistas : 0;
            if (app == Ui.APP_MAPA && Waypoints.navegando()) Ui.circulo(g, cx + 10, y + 2, 3, Ui.AZUL);
            if (n > 0) Ui.insignia(g, n, cx + 11, y + 1, Ui.ROJO);
        }

        @Override
        boolean clic(double x, double y) {
            for (int i = 0; i < REJILLA.length; i++)
                if (Ui.dentro(x, y, rx(i) - 13, ry(i) - 1, 26, 36)) return lanzar(REJILLA[i]);
            for (int i = 0; i < DOCK.length; i++)
                if (Ui.dentro(x, y, dx(i) + 4 - 13, DY - 1, 26, 26)) return lanzar(DOCK[i]);
            return false;
        }

        private boolean lanzar(int app) {
            MovilCliente.clic();
            m.abrir(switch (app) {
                case Ui.APP_CHATS -> new VistasChat.Chats();
                case Ui.APP_LLAMAR -> new VistasTelefono.Telefono();
                case Ui.APP_AGENDA -> new VistasTelefono.Agenda();
                case Ui.APP_CAMARA -> new VistasFotos.Camara();
                case Ui.APP_FOTOS -> new VistasFotos.Galeria(null);
                case Ui.APP_RELOJ -> new Reloj();
                case Ui.APP_LINTERNA -> new Linterna();
                case Ui.APP_MINEGRAM -> MovilCliente.minegram ? new VistasMinegram.Feed() : new Desactivada("app_minegram");
                case Ui.APP_MAPA -> new VistasMapa.Mapa();
                case Ui.APP_BANCO -> MovilCliente.banco ? new VistasBanco.Banco() : new Desactivada("app_banco");
                default -> new Ajustes();
            });
            return true;
        }
    }

    /** Una app que el servidor tiene desactivada. */
    static final class Desactivada extends Vista {
        private final String app;

        Desactivada(String app) {
            this.app = app;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            Ui.centrado(g, t("app_desactivada"), W / 2, H / 2 - 4, Ui.TEXTO2);
            cabecera(g, t(app), mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            return clicAtras(x, y);
        }
    }

    // ================================================================== reloj y alarma
    static final class Reloj extends Vista {
        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            Ui.rect(g, 0, 0, W, H, 0xFF07090C);
            cabecera(g, t("app_reloj"), mx, my);
            Ui.grande(g, MovilCliente.hora(), W / 2, 46, 3, 0xFFFFFFFF);
            var l = Minecraft.getInstance().level;
            Ui.centrado(g, t("dia", MovilCliente.dia()), W / 2, 74, Ui.TEXTO2);
            Ui.centrado(g, t(l == null ? "luna0" : "luna" + l.getMoonPhase()), W / 2, 85, Ui.TEXTO2);
            // tarjeta de la alarma
            int y = 104;
            Ui.redondo(g, 8, y, W - 16, 92, 8, Ui.TARJETA);
            Ui.glifo(g, Ui.G_ALARMA, 15, y + 8, Ui.AMARILLO);
            Ui.texto(g, t("alarma"), 28, y + 9, Ui.TEXTO);
            boolean on = MovilCliente.alarma >= 0;
            interruptor(g, W - 36, y + 7, on);
            int a = on ? MovilCliente.alarma : 7 * 60;
            Ui.grande(g, String.format("%02d:%02d", a / 60, a % 60), W / 2, y + 30, 2, on ? 0xFFFFFFFF : 0xFF59636D);
            String[] bt = {"-1h", "+1h", "-10", "+10"};
            for (int i = 0; i < 4; i++) {
                int bx = 14 + i * 27, by = y + 56;
                boolean s = Ui.dentro(mx, my, bx, by, 24, 14);
                Ui.redondo(g, bx, by, 24, 14, 5, s ? 0xFF33404C : 0xFF27313B);
                Ui.centrado(g, bt[i], bx + 12, by + 3, Ui.TEXTO);
            }
            Ui.centrado(g, t("alarma_ayuda"), W / 2, y + 77, 0xFF59636D);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int y0 = 104;
            if (Ui.dentro(x, y, W - 40, y0 + 3, 28, 18)) {
                MovilCliente.alarma = MovilCliente.alarma >= 0 ? -1 : 7 * 60;
                MovilCliente.clic();
                MovilCliente.ajustes();
                return true;
            }
            int[] d = {-60, 60, -10, 10};
            for (int i = 0; i < 4; i++)
                if (Ui.dentro(x, y, 14 + i * 27, y0 + 56, 24, 14)) {
                    int a = MovilCliente.alarma >= 0 ? MovilCliente.alarma : 7 * 60;
                    MovilCliente.alarma = Math.floorMod(a + d[i], 1440);
                    MovilCliente.clic();
                    MovilCliente.ajustes();
                    return true;
                }
            return false;
        }
    }

    // ================================================================== linterna
    static final class Linterna extends Vista {
        @Override
        boolean barraOscura() {
            return MovilCliente.linterna;
        }

        @Override
        int fondoBarra() {
            return 0;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            boolean on = MovilCliente.linterna;
            Ui.rect(g, 0, 0, W, H, on ? 0xFFF7F3E3 : 0xFF07090C);
            int cx = W / 2, cy = H / 2 - 8;
            if (on) for (int r = 46; r > 28; r -= 3) Ui.circulo(g, cx, cy, r, 0x18F5C84C);
            Ui.circulo(g, cx, cy, 28, on ? 0xFFFFFFFF : 0xFF1F262E);
            Ui.circulo(g, cx, cy, 25, on ? 0xFFFCE9A8 : 0xFF2A333C);
            g.pose().pushPose();
            g.pose().translate(cx - 9, cy - 9, 0);
            g.pose().scale(2, 2, 1);
            Ui.glifo(g, Ui.G_LINTERNA, 0, 0, on ? 0xFF4A3B10 : 0xFFB8C2CC);
            g.pose().popPose();
            Ui.centrado(g, t(on ? "linterna_on" : "linterna_off"), cx, cy + 40, on ? 0xFF4A4232 : Ui.TEXTO2);
            Ui.centrado(g, t("linterna_ayuda"), cx, cy + 52, on ? 0xFF8A8068 : 0xFF59636D);
            if (MovilCliente.linternaReal) Ui.centrado(g, t("linterna_real"), cx, cy + 62, on ? 0xFF8A8068 : 0xFF59636D);
            boolean s = Ui.dentro(mx, my, 4, BARRA + 2, 16, 16);
            Ui.glifo(g, Ui.G_ATRAS, 6, BARRA + 6, s ? 0xFFFFFFFF : on ? 0xFF4A4232 : Ui.TEXTO);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (Ui.enCirculo(x, y, W / 2, H / 2 - 8, 28)) {
                MovilCliente.linterna(!MovilCliente.linterna);
                MovilCliente.sonido(MovilRegistro.CLIC.get(), 0.7F, MovilCliente.linterna ? 1.3F : 0.9F);
                return true;
            }
            return false;
        }
    }

    // ================================================================== ajustes
    static final class Ajustes extends Vista {
        private static final int Y0 = BARRA + CAB + 1;
        private net.minecraft.client.resources.sounds.SoundInstance muestra;

        @Override
        void cerrada() {
            if (muestra != null) Minecraft.getInstance().getSoundManager().stop(muestra);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            recortar(g, Y0, H - ABAJO);
            int y = Y0 + 4 - (int) scroll;
            // tono
            fila(g, y, t("tono"), mx, my);
            String nt = t("tono" + MovilCliente.tono);
            Ui.texto(g, nt, W - 14 - Ui.ancho(nt), y + 6, Ui.AZUL);
            Ui.glifo(g, Ui.G_DERECHA, W - 12, y + 6, Ui.TEXTO2);
            y += 22;
            fila(g, y, t("silencio"), mx, my);
            interruptor(g, W - 30, y + 5, MovilCliente.silencio);
            y += 22;
            Ui.texto(g, t("fondo"), 10, y + 6, Ui.TEXTO);
            y += 18;
            for (int i = 0; i < FONDOS; i++) {
                int fx = 9 + i * 29;
                if (i == MovilCliente.fondo) Ui.redondo(g, fx - 2, y - 2, 28, 49, 4, Ui.ACENTO);
                g.blit(fondoTex(i), fx, y, 24, 45, 0, 0, W, H, W, H);
            }
            y += 52;
            Ui.rect(g, 8, y, W - 16, 1, Ui.LINEA);
            y += 5;
            Ui.texto(g, t("mi_numero"), 10, y + 2, Ui.TEXTO);
            Ui.texto(g, MovilCliente.miNumero, W - 10 - Ui.ancho(MovilCliente.miNumero), y + 2, Ui.TEXTO2);
            y += 16;
            String voz = MovilCliente.vozLocal() && MovilCliente.vozServidor ? t("voz_si") : t("voz_no");
            Ui.texto(g, t("voz"), 10, y + 2, Ui.TEXTO);
            Ui.texto(g, voz, W - 10 - Ui.ancho(voz), y + 2, Ui.TEXTO2);
            y += 18;
            Ui.texto(g, t("privacidad"), 10, y, Ui.TEXTO);
            y += 11;
            for (FormattedCharSequence s : Ui.envolver(t("privacidad_texto"), W - 20)) {
                g.drawString(Ui.font(), s, 10, y, Ui.TEXTO2, false);
                y += 10;
            }
            scrollMax = y + (int) scroll + 6 - (H - ABAJO);
            soltarRecorte(g);
            cabecera(g, t("app_ajustes"), mx, my);
        }

        private void fila(GuiGraphics g, int y, String txt, int mx, int my) {
            if (Ui.dentro(mx, my, 0, y, W, 20)) Ui.rect(g, 0, y, W, 20, 0x10FFFFFF);
            Ui.texto(g, txt, 10, y + 6, Ui.TEXTO);
        }

        @Override
        boolean clic(double x, double yy) {
            if (clicAtras(x, yy)) return true;
            if (yy < Y0) return false;
            double y = yy + scroll - (Y0 + 4);
            if (y >= 0 && y < 20) {
                MovilCliente.tono = (MovilCliente.tono + 1) % MovilRegistro.TONOS;
                var sm = Minecraft.getInstance().getSoundManager();
                if (muestra != null) sm.stop(muestra);
                muestra = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(MovilRegistro.TONO[MovilCliente.tono].get(), 1.0F, 0.8F);
                sm.play(muestra);
                MovilCliente.ajustes();
                return true;
            }
            if (y >= 22 && y < 42) {
                MovilCliente.silencio = !MovilCliente.silencio;
                MovilCliente.clic();
                if (MovilCliente.silencio) MovilCliente.sonido(MovilRegistro.VIBRAR.get(), 0.8F, 1.0F);
                MovilCliente.ajustes();
                return true;
            }
            if (y >= 62 && y < 107) {
                for (int i = 0; i < FONDOS; i++)
                    if (x >= 9 + i * 29 && x < 33 + i * 29) {
                        MovilCliente.fondo = i;
                        MovilCliente.clic();
                        MovilCliente.ajustes();
                        return true;
                    }
            }
            return false;
        }
    }
}
