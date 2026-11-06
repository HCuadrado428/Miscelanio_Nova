package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilDatos;
import com.miscelanea.movil.MovilRed;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** Teléfono (recientes y teclado), agenda de contactos, ficha de contacto y la pantalla de llamada / videollamada. */
final class VistasTelefono {
    private VistasTelefono() {}

    static final int Y0 = VistasChat.Y0;

    /** Aviso de que falta Simple Voice Chat (en el cliente o en el servidor), o null si está todo. */
    static String faltaVoz() {
        if (!MovilCliente.vozLocal()) return Vista.t("necesitas_svc");
        if (MovilCliente.sincronizado && !MovilCliente.vozServidor) return Vista.t("necesitas_svc_servidor");
        return null;
    }

    static void avisoVoz(GuiGraphics g, String s, int y) {
        Ui.redondo(g, 8, y, Vista.W - 16, 60, 8, Ui.TARJETA);
        Ui.glifo(g, Ui.G_TELEFONO, Vista.W / 2 - 4, y + 7, Ui.AMARILLO);
        int yy = y + 20;
        for (FormattedCharSequence l : Ui.envolver(s, Vista.W - 28)) {
            g.drawCenteredString(Ui.font(), l, Vista.W / 2, yy, Ui.TEXTO);
            yy += 10;
        }
    }

    // ================================================================== teléfono
    static final class Telefono extends Vista {
        private boolean teclado;
        private final StringBuilder num = new StringBuilder();
        private static final int F = 24;

        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_SYNC, null);
            MovilCliente.registroVisto = System.currentTimeMillis();
        }

        @Override
        void cerrada() {
            MovilCliente.registroVisto = System.currentTimeMillis();
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int y0 = Y0 + 16;
            // pestañas
            Ui.rect(g, 0, Y0, W, 16, Ui.CABECERA);
            String[] p = {t("recientes"), t("teclado")};
            for (int i = 0; i < 2; i++) {
                boolean sel = teclado == (i == 1);
                Ui.centrado(g, p[i], W / 4 + i * W / 2, Y0 + 4, sel ? Ui.ACENTO : Ui.TEXTO2);
                if (sel) Ui.rect(g, i * W / 2 + 12, Y0 + 14, W / 2 - 24, 2, Ui.ACENTO);
            }
            String falta = faltaVoz();
            if (teclado) dibujarTeclado(g, mx, my, y0, falta);
            else {
                recortar(g, y0, H - ABAJO);
                int y = y0 - (int) scroll;
                for (MovilCliente.Registro r : MovilCliente.REGISTRO) {
                    if (y + F > y0 && y < H) {
                        if (Ui.dentro(mx, my, 0, y, W, F) && my >= y0) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                        Ui.cara(g, r.otro(), 6, y + 4, 16, Ui.FONDO);
                        boolean perdida = r.tipo() == MovilDatos.R_PERDIDA;
                        Ui.texto(g, Ui.cortar(r.nombre(), W - 70), 28, y + 3, perdida ? Ui.ROJO : Ui.TEXTO);
                        int gl = switch (r.tipo()) {
                            case MovilDatos.R_SALIENTE, MovilDatos.R_SIN_RESPUESTA -> Ui.G_SALIENTE;
                            case MovilDatos.R_PERDIDA -> Ui.G_PERDIDA;
                            default -> Ui.G_ENTRANTE;
                        };
                        int col = perdida ? Ui.ROJO : r.tipo() == MovilDatos.R_SIN_RESPUESTA ? Ui.TEXTO2 : Ui.ACENTO;
                        Ui.glifo(g, gl, 27, y + 13, col);
                        if (r.video()) Ui.glifo(g, Ui.G_VIDEO, 37, y + 13, Ui.TEXTO2);
                        String d = cuando(r.t()) + (r.dur() > 0 ? " " + MovilCliente.duracion(r.dur()) : "");
                        Ui.mini(g, d, r.video() ? 48 : 38, y + 15, Ui.TEXTO2);
                        Ui.glifo(g, r.video() ? Ui.G_VIDEO : Ui.G_TELEFONO, W - 15, y + 8, Ui.dentro(mx, my, W - 22, y, 22, F) ? 0xFFFFFFFF : Ui.ACENTO);
                    }
                    y += F;
                }
                scrollMax = MovilCliente.REGISTRO.size() * F - (H - ABAJO - y0);
                barraScroll(g, y0, H - ABAJO);
                soltarRecorte(g);
                if (falta != null) avisoVoz(g, falta, H - ABAJO - 68);
                else if (MovilCliente.REGISTRO.isEmpty()) Ui.centrado(g, t("sin_llamadas"), W / 2, H / 2, Ui.TEXTO2);
            }
            cabecera(g, t("app_llamar"), mx, my);
        }

        private static final String[] TECLAS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<"};

        private static int tx(int i) {
            return 26 + (i % 3) * 40;
        }

        private static int ty(int y0, int i) {
            return y0 + 44 + (i / 3) * 30;
        }

        private void dibujarTeclado(GuiGraphics g, int mx, int my, int y0, String falta) {
            String n = num.length() == 0 ? "" : num.toString();
            Ui.grande(g, n, W / 2, y0 + 12, 2, Ui.TEXTO);
            String quien = "";
            for (MovilCliente.Contacto k : MovilCliente.CONTACTOS) if (k.numero().contentEquals(num)) quien = k.nombre();
            if (!quien.isEmpty()) Ui.centrado(g, quien, W / 2, y0 + 31, Ui.ACENTO);
            else if (num.length() == 0) Ui.centrado(g, t("mi_numero_es", MovilCliente.miNumero), W / 2, y0 + 31, Ui.TEXTO2);
            for (int i = 0; i < TECLAS.length; i++) {
                if (TECLAS[i].isEmpty()) continue;
                int cx = tx(i), cy = ty(y0, i);
                boolean s = Ui.enCirculo(mx, my, cx, cy, 12);
                Ui.circulo(g, cx, cy, 12, s ? 0xFF323C46 : 0xFF222A32);
                if (TECLAS[i].equals("<")) Ui.glifo(g, Ui.G_BORRAR_TECLA, cx - 4, cy - 4, Ui.TEXTO);
                else Ui.grande(g, TECLAS[i], cx + 1, cy - 7, 2, Ui.TEXTO);
            }
            int cy = ty(y0, 12) - 2;
            if (falta != null) {
                Ui.centrado(g, Ui.cortar(falta, W - 8), W / 2, cy - 4, Ui.AMARILLO);
                return;
            }
            boolean s1 = Ui.enCirculo(mx, my, W / 2, cy, 13), s2 = Ui.enCirculo(mx, my, W / 2 + 36, cy, 10);
            Ui.boton(g, W / 2, cy, 13, s1 ? 0xFF2FBF73 : Ui.ACENTO, Ui.G_TELEFONO, 0xFFFFFFFF);
            if (MovilCliente.videoServidor) Ui.boton(g, W / 2 + 36, cy, 10, s2 ? 0xFF3A86D6 : Ui.AZUL, Ui.G_VIDEO, 0xFFFFFFFF);
        }

        private void marcar(boolean video) {
            if (num.length() == 0 || faltaVoz() != null) return;
            String n = num.toString();
            MovilRed.alServidor(MovilRed.C_LLAMAR_NUMERO, b -> {
                b.writeUtf(n);
                b.writeBoolean(video);
            });
            MovilCliente.miVideo = video;
            MovilCliente.frontal = true;
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (!teclado) return false;
            if (key == GLFW.GLFW_KEY_BACKSPACE && num.length() > 0) {
                num.setLength(num.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                marcar(false);
                return true;
            }
            return false;
        }

        @Override
        boolean caracter(char c) {
            if (teclado && c >= '0' && c <= '9' && num.length() < 5) {
                num.append(c);
                MovilCliente.clic();
                return true;
            }
            return false;
        }

        @Override
        boolean escribiendo() {
            return teclado;
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (y >= Y0 && y < Y0 + 16) {
                teclado = x >= W / 2.0;
                scroll = 0;
                MovilCliente.clic();
                return true;
            }
            int y0 = Y0 + 16;
            if (teclado) {
                for (int i = 0; i < TECLAS.length; i++) {
                    if (TECLAS[i].isEmpty() || !Ui.enCirculo(x, y, tx(i), ty(y0, i), 12)) continue;
                    MovilCliente.clic();
                    if (TECLAS[i].equals("<")) {
                        if (num.length() > 0) num.setLength(num.length() - 1);
                    } else if (num.length() < 5) num.append(TECLAS[i]);
                    return true;
                }
                int cy = ty(y0, 12) - 2;
                if (Ui.enCirculo(x, y, W / 2, cy, 13)) {
                    marcar(false);
                    return true;
                }
                if (MovilCliente.videoServidor && Ui.enCirculo(x, y, W / 2 + 36, cy, 10)) {
                    marcar(true);
                    return true;
                }
                return false;
            }
            if (y < y0) return false;
            int i = (int) ((y - y0 + scroll) / F);
            if (i < 0 || i >= MovilCliente.REGISTRO.size()) return false;
            MovilCliente.Registro r = MovilCliente.REGISTRO.get(i);
            MovilCliente.clic();
            if (x >= W - 22) MovilCliente.llamar(r.otro(), r.video());
            else m.abrir(new Contacto(r.otro()));
            return true;
        }
    }

    // ================================================================== agenda
    static final class Agenda extends Vista {
        private static final int F = 24;

        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_SYNC, null);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int y0 = Y0 + 14;
            Ui.rect(g, 0, Y0, W, 14, Ui.TARJETA);
            Ui.texto(g, t("mi_numero"), 8, Y0 + 3, Ui.TEXTO2);
            Ui.texto(g, MovilCliente.miNumero, W - 8 - Ui.ancho(MovilCliente.miNumero), Y0 + 3, Ui.ACENTO);
            recortar(g, y0, H - ABAJO);
            int y = y0 - (int) scroll;
            for (MovilCliente.Contacto k : MovilCliente.CONTACTOS) {
                if (y + F > y0 && y < H) {
                    if (Ui.dentro(mx, my, 0, y, W, F) && my >= y0) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                    Ui.cara(g, k.id(), 6, y + 4, 16, Ui.FONDO);
                    if (k.conectado()) {
                        Ui.circulo(g, 20, y + 18, 3, Ui.FONDO);
                        Ui.circulo(g, 20, y + 18, 2, Ui.ACENTO);
                    }
                    Ui.texto(g, Ui.cortar(k.nombre(), W - 40), 28, y + 4, Ui.TEXTO);
                    Ui.mini(g, k.numero(), 28, y + 15, Ui.TEXTO2);
                    Ui.glifo(g, Ui.G_DERECHA, W - 12, y + 8, Ui.TEXTO2);
                    Ui.rect(g, 28, y + F - 1, W - 28, 1, Ui.LINEA);
                }
                y += F;
            }
            scrollMax = MovilCliente.CONTACTOS.size() * F - (H - ABAJO - y0);
            barraScroll(g, y0, H - ABAJO);
            soltarRecorte(g);
            if (MovilCliente.CONTACTOS.isEmpty()) Ui.centrado(g, t(MovilCliente.sincronizado ? "sin_contactos" : "cargando"), W / 2, H / 2, Ui.TEXTO2);
            cabecera(g, t("app_agenda"), mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int y0 = Y0 + 14;
            if (y < y0) return false;
            int i = (int) ((y - y0 + scroll) / F);
            if (i < 0 || i >= MovilCliente.CONTACTOS.size()) return false;
            MovilCliente.clic();
            m.abrir(new Contacto(MovilCliente.CONTACTOS.get(i).id()));
            return true;
        }
    }

    // ================================================================== ficha de contacto
    static final class Contacto extends Vista {
        private final UUID id;

        Contacto(UUID id) {
            this.id = id;
        }

        private static int bx(int i) {
            return W / 2 - 36 + i * 36;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            MovilCliente.Contacto k = MovilCliente.contacto(id);
            Ui.rect(g, 0, Y0, W, 74, Ui.CABECERA);
            Ui.cara(g, id, W / 2 - 16, Y0 + 6, 32, Ui.CABECERA);
            if (k != null) {
                Ui.centrado(g, k.nombre(), W / 2, Y0 + 44, Ui.TEXTO);
                Ui.centrado(g, k.numero() + " · " + t(k.conectado() ? "en_linea" : "desconectado"), W / 2, Y0 + 56, k.conectado() ? Ui.ACENTO : Ui.TEXTO2);
            }
            int cy = Y0 + 98;
            int[] gl = {Ui.G_MENSAJE, Ui.G_TELEFONO, Ui.G_VIDEO};
            String[] tx = {t("mensaje"), t("llamar"), t("video")};
            for (int i = 0; i < 3; i++) {
                if (i == 2 && !MovilCliente.videoServidor) continue;
                boolean s = Ui.enCirculo(mx, my, bx(i), cy, 12);
                Ui.boton(g, bx(i), cy, 12, s ? 0xFF33404C : 0xFF222A32, gl[i], i == 0 ? Ui.ACENTO : i == 1 ? Ui.ACENTO : Ui.AZUL);
                Ui.centrado(g, tx[i], bx(i), cy + 16, Ui.TEXTO2);
            }
            String falta = faltaVoz();
            if (falta != null) avisoVoz(g, falta, cy + 34);
            cabecera(g, t("contacto"), mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int cy = Y0 + 98;
            for (int i = 0; i < 3; i++) {
                if (!Ui.enCirculo(x, y, bx(i), cy, 13)) continue;
                if (i == 2 && !MovilCliente.videoServidor) return false;
                MovilCliente.clic();
                if (i == 0) MovilRed.alServidor(MovilRed.C_CHAT_NUEVO, b -> {
                    b.writeUtf("");
                    b.writeVarInt(1);
                    b.writeUUID(id);
                });
                else if (faltaVoz() == null) MovilCliente.llamar(id, i == 2);
                return true;
            }
            return false;
        }
    }

    // ================================================================== llamada / videollamada
    static final class Llamada extends Vista {
        private long ultFrame;

        @Override
        int fondoBarra() {
            return 0;
        }

        private boolean video() {
            return MovilCliente.llEstado == com.miscelanea.movil.MovilRed.LL_ACTIVA && MovilCliente.miVideo && MovilCliente.llVideoPermitido;
        }

        @Override
        boolean quiereFrontal() {
            return video() && MovilCliente.frontal;
        }

        @Override
        void tick() {
            if (MovilCliente.llEstado == MovilRed.LL_FIN && Util.getMillis() - MovilCliente.llFin > 1600 && m.arriba() == this) m.atras();
        }

        @Override
        void cerrada() {
            if (MovilCliente.miVideo && MovilCliente.llEstado == MovilRed.LL_ACTIVA)
                MovilRed.alServidor(MovilRed.C_VIDEO, b -> b.writeByteArray(new byte[0]));
        }

        @Override
        void abierta() {
            ultFrame = 0;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            int est = MovilCliente.llEstado;
            boolean activa = est == MovilRed.LL_ACTIVA;
            FotoCache.Tex remoto = activa && MovilCliente.otroVideo ? FotoCache.video() : null;
            // fondo: vídeo del otro o degradado con su cara
            if (remoto != null) FotoCache.cubrir(g, remoto, 0, 0, W, H);
            else {
                g.fillGradient(0, 0, W, H, 0xFF1D3B4F, 0xFF0B1218);
                Ui.cara(g, MovilCliente.llOtro, W / 2 - 20, 52, 40, 0xFF173041);
            }
            // mi vídeo: enviar y miniatura
            if (video()) {
                MovilCaptura.pedir();
                long ms = Util.getMillis();
                if (MovilCaptura.lista() && ms - ultFrame >= 1000 / MovilCliente.vFps) {
                    ultFrame = ms;
                    byte[] f = MovilCaptura.video(MovilCliente.vW, MovilCliente.vH, MovilCliente.vMax);
                    if (f != null) MovilRed.alServidor(MovilRed.C_VIDEO, b -> b.writeByteArray(f));
                }
                int pw = 34, ph = 60, px = W - pw - 6, py = BARRA + 4;
                Ui.redondo(g, px - 1, py - 1, pw + 2, ph + 2, 3, 0xFFFFFFFF);
                if (MovilCaptura.lista()) MovilCaptura.dibujarVisor(g, px, py, pw, ph);
            }
            // nombre y estado
            int ty = remoto != null ? BARRA + 6 : 100;
            if (remoto != null) Ui.rect(g, 0, 0, W, BARRA + 28, 0x60000000);
            int tx = remoto != null ? 8 : W / 2;
            String nombre = Ui.cortar(MovilCliente.llNombre, video() && remoto != null ? W - 52 : W - 12);
            String estado = switch (est) {
                case MovilRed.LL_SALIENTE -> t("llamando") + puntos();
                case MovilRed.LL_ENTRANTE -> t(MovilCliente.miVideo ? "videollamada_entrante" : "llamada_entrante");
                case MovilRed.LL_ACTIVA -> MovilCliente.duracion((Util.getMillis() - MovilCliente.llInicio) / 1000);
                default -> t("fin" + MovilCliente.llMotivo);
            };
            if (remoto != null) {
                Ui.texto(g, nombre, tx, ty, 0xFFFFFFFF);
                Ui.texto(g, estado, tx, ty + 10, 0xFFB9F5D2);
            } else {
                g.drawCenteredString(Ui.font(), nombre, tx, ty, 0xFFFFFFFF);
                Ui.centrado(g, estado, tx, ty + 12, est == MovilRed.LL_FIN ? Ui.ROJO : 0xFFB9F5D2);
                if (activa && MovilCliente.miVideo && !MovilCliente.otroVideo) Ui.centrado(g, t("sin_video_otro"), tx, ty + 24, 0xFF8FA3B3);
            }
            // botones
            int cy = H - ABAJO - 26;
            if (est == MovilRed.LL_ENTRANTE) {
                long a = (Util.getMillis() / 120) % 8;
                Ui.boton(g, W / 2 - 34, cy, 14, Ui.ROJO, Ui.G_COLGAR, 0xFFFFFFFF);
                Ui.boton(g, W / 2 + 34, cy - (a < 2 ? (int) a : 0), 14, Ui.ACENTO, MovilCliente.miVideo ? Ui.G_VIDEO : Ui.G_TELEFONO, 0xFFFFFFFF);
                Ui.centrado(g, t("rechazar"), W / 2 - 34, cy + 17, 0xFFCCD5DD);
                Ui.centrado(g, t("contestar"), W / 2 + 34, cy + 17, 0xFFCCD5DD);
            } else if (est != MovilRed.LL_FIN) {
                Ui.boton(g, W / 2, cy, 14, Ui.dentro(mx, my, W / 2 - 14, cy - 14, 28, 28) ? 0xFFD43C3C : Ui.ROJO, Ui.G_COLGAR, 0xFFFFFFFF);
                if (activa && MovilCliente.llVideoPermitido) {
                    boolean v = MovilCliente.miVideo;
                    Ui.boton(g, W / 2 - 40, cy, 11, v ? 0xFFFFFFFF : 0x80303A44, v ? Ui.G_VIDEO : Ui.G_VIDEO_NO, v ? 0xFF101418 : 0xFFFFFFFF);
                    if (v) Ui.boton(g, W / 2 + 40, cy, 11, MovilCliente.frontal ? 0x80303A44 : 0xFFFFFFFF, Ui.G_GIRAR,
                            MovilCliente.frontal ? 0xFFFFFFFF : 0xFF101418);
                }
            }
        }

        private static String puntos() {
            return ".".repeat((int) ((Util.getMillis() / 400) % 4));
        }

        @Override
        boolean clic(double x, double y) {
            int est = MovilCliente.llEstado, cy = H - ABAJO - 26;
            if (est == MovilRed.LL_ENTRANTE) {
                if (Ui.enCirculo(x, y, W / 2 - 34, cy, 15)) {
                    MovilRed.alServidor(MovilRed.C_CONTESTAR, b -> b.writeBoolean(false));
                    return true;
                }
                if (Ui.enCirculo(x, y, W / 2 + 34, cy, 15)) {
                    MovilCliente.clic();
                    MovilRed.alServidor(MovilRed.C_CONTESTAR, b -> b.writeBoolean(true));
                    return true;
                }
                return false;
            }
            if (est == MovilRed.LL_FIN) return false;
            if (Ui.enCirculo(x, y, W / 2, cy, 15)) {
                MovilRed.alServidor(MovilRed.C_COLGAR, null);
                return true;
            }
            if (est == MovilRed.LL_ACTIVA && MovilCliente.llVideoPermitido) {
                if (Ui.enCirculo(x, y, W / 2 - 40, cy, 12)) {
                    MovilCliente.miVideo = !MovilCliente.miVideo;
                    MovilCliente.clic();
                    if (!MovilCliente.miVideo) MovilRed.alServidor(MovilRed.C_VIDEO, b -> b.writeByteArray(new byte[0]));
                    return true;
                }
                if (MovilCliente.miVideo && Ui.enCirculo(x, y, W / 2 + 40, cy, 12)) {
                    MovilCliente.frontal = !MovilCliente.frontal;
                    MovilCliente.clic();
                    return true;
                }
            }
            return false;
        }
    }
}
