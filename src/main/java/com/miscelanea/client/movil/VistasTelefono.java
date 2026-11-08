package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilDatos;
import com.miscelanea.movil.MovilRed;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
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
        private boolean teclado, oculto;
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
                        caraLlamada(g, r.otro(), 6, y + 4, 16, Ui.FONDO);
                        boolean perdida = r.tipo() == MovilDatos.R_PERDIDA;
                        Ui.texto(g, Ui.cortar(MovilCliente.nombreVisible(r.otro(), r.nombre()), W - 70), 28, y + 3, perdida ? Ui.ROJO : Ui.TEXTO);
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
                        if (!r.otro().equals(com.miscelanea.movil.Llamadas.OCULTO))
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
            return y0 + 46 + (i / 3) * 27;
        }

        private void dibujarTeclado(GuiGraphics g, int mx, int my, int y0, String falta) {
            String n = num.length() == 0 ? "" : num.toString();
            Ui.grande(g, n, W / 2, y0 + 6, 2, Ui.TEXTO);
            String quien = "";
            for (MovilCliente.Contacto k : MovilCliente.CONTACTOS) if (k.numero().contentEquals(num)) quien = k.nombre();
            if (!quien.isEmpty()) Ui.centrado(g, quien, W / 2, y0 + 23, Ui.ACENTO);
            else if (num.length() == 0) Ui.centrado(g, t("mi_numero_es", MovilCliente.miNumero), W / 2, y0 + 23, Ui.TEXTO2);
            // la tecla de abajo a la izquierda: #31# (número oculto en esta llamada)
            {
                int cx = tx(9), cy = ty(y0, 9);
                boolean on = oculto || MovilCliente.ocultarSiempre, s = Ui.enCirculo(mx, my, cx, cy, 12);
                Ui.circulo(g, cx, cy, 12, on ? Ui.AZUL : s ? 0xFF323C46 : 0xFF222A32);
                Ui.mini(g, "#31#", cx - 7, cy - 2, on ? 0xFFFFFFFF : Ui.TEXTO2);
                if (on) Ui.centrado(g, t("numero_oculto"), W / 2, y0 + 33, Ui.AZUL);
            }
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
            boolean oc = oculto || MovilCliente.ocultarSiempre;
            MovilRed.alServidor(MovilRed.C_LLAMAR_NUMERO, b -> {
                b.writeUtf(n);
                b.writeBoolean(video);
                b.writeBoolean(oc);
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
                if (Ui.enCirculo(x, y, tx(9), ty(y0, 9), 12)) {
                    MovilCliente.clic();
                    if (MovilCliente.ocultarSiempre) MovilCliente.aviso(net.minecraft.network.chat.Component.translatable("movil.miscelanea.oculto_siempre"));
                    else oculto = !oculto;
                    return true;
                }
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
            if (r.otro().equals(com.miscelanea.movil.Llamadas.OCULTO)) return false;
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
        private net.minecraft.client.resources.sounds.SoundInstance muestra;

        @Override
        void cerrada() {
            if (muestra != null) net.minecraft.client.Minecraft.getInstance().getSoundManager().stop(muestra);
        }

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
            Ui.rect(g, 0, Y0, W, 62, Ui.CABECERA);
            Ui.cara(g, id, W / 2 - 16, Y0 + 4, 32, Ui.CABECERA);
            if (k != null) {
                Ui.centrado(g, k.nombre(), W / 2, Y0 + 39, Ui.TEXTO);
                Ui.centrado(g, k.numero() + " · " + t(k.conectado() ? "en_linea" : "desconectado"), W / 2, Y0 + 50, k.conectado() ? Ui.ACENTO : Ui.TEXTO2);
            }
            int cy = Y0 + 86;
            int[] gl = {Ui.G_MENSAJE, Ui.G_TELEFONO, Ui.G_VIDEO};
            String[] tx = {t("mensaje"), t("llamar"), t("video")};
            for (int i = 0; i < 3; i++) {
                if (i == 2 && !MovilCliente.videoServidor) continue;
                boolean s = Ui.enCirculo(mx, my, bx(i), cy, 12);
                Ui.boton(g, bx(i), cy, 12, s ? 0xFF33404C : 0xFF222A32, gl[i], i == 0 ? Ui.ACENTO : i == 1 ? Ui.ACENTO : Ui.AZUL);
                Ui.centrado(g, tx[i], bx(i), cy + 16, Ui.TEXTO2);
            }
            // segunda fila: ubicación, Bizum y perfil de Minegram
            int cy2 = cy + 36;
            int[] gl2 = {Ui.G_PIN, Ui.G_TRANSFER, Ui.G_FOTO};
            String[] tx2 = {t("ubicacion"), t("banco_bizum"), "Minegram"};
            boolean[] on2 = {MovilCliente.ubicacion, MovilCliente.banco, MovilCliente.minegram};
            for (int i = 0; i < 3; i++) {
                if (!on2[i]) continue;
                boolean s = Ui.enCirculo(mx, my, bx(i), cy2, 12);
                boolean activo = i == 0 && MovilCliente.ubiMia(id) != null;
                Ui.boton(g, bx(i), cy2, 12, s ? 0xFF33404C : 0xFF222A32, gl2[i], i == 0 ? (activo ? Ui.AZUL : Ui.TEXTO) : i == 1 ? VistasBanco.VERDE : VistasMinegram.ROSA);
                Ui.centrado(g, tx2[i], bx(i), cy2 + 16, activo ? Ui.AZUL : Ui.TEXTO2);
            }
            MovilCliente.UbiAmigo ua = MovilCliente.ubiDe(id);
            if (ua != null) {
                boolean s = Ui.dentro(mx, my, 10, cy2 + 30, W - 20, 14);
                Ui.pastilla(g, 10, cy2 + 30, W - 20, 14, t("ubi_ver_mapa"), s ? 0xFF2B6FB0 : 0xFF1F4F80, 0xFFFFFFFF);
            }
            // tono de llamada para este contacto
            if (k != null) {
                int ty = cy2 + 46;
                boolean s = Ui.dentro(mx, my, 8, ty, W - 16, 18);
                Ui.redondo(g, 8, ty, W - 16, 18, 4, s ? 0xFF2A343E : Ui.TARJETA);
                Ui.glifo(g, Ui.G_CAMPANA, 12, ty + 5, Ui.TEXTO2);
                Ui.texto(g, t("tono_contacto"), 24, ty + 5, Ui.TEXTO);
                String nt = k.tono() < 0 ? t("tono_general") : t("tono" + k.tono());
                Ui.texto(g, nt, W - 12 - Ui.ancho(nt), ty + 5, Ui.AZUL);
            }
            String falta = faltaVoz();
            if (falta != null) {
                // aviso compacto abajo (en la ficha no cabe la tarjeta grande)
                List<FormattedCharSequence> ls = Ui.font().split(net.minecraft.network.chat.Component.literal(falta), (W - 16) * 2);
                int yy = H - ABAJO - 2 - ls.size() * 5;
                g.pose().pushPose();
                g.pose().translate(8, yy, 0);
                g.pose().scale(0.5F, 0.5F, 1);
                for (int i = 0; i < ls.size(); i++) g.drawString(Ui.font(), ls.get(i), 0, i * 10, Ui.AMARILLO, false);
                g.pose().popPose();
            }
            cabecera(g, t("contacto"), mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int cy = Y0 + 86;
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
            int cy2 = cy + 36;
            boolean[] on2 = {MovilCliente.ubicacion, MovilCliente.banco, MovilCliente.minegram};
            for (int i = 0; i < 3; i++) {
                if (!on2[i] || !Ui.enCirculo(x, y, bx(i), cy2, 13)) continue;
                MovilCliente.clic();
                MovilCliente.Contacto k = MovilCliente.contacto(id);
                String nombre = k == null ? "?" : k.nombre();
                if (i == 0) m.abrir(new VistasMapa.Compartir(java.util.List.of(id), nombre));
                else if (i == 1) m.abrir(new VistasBanco.Cantidad(VistasBanco.Cantidad.BIZUM, id, nombre));
                else m.abrir(new VistasMinegram.Perfil(id));
                return true;
            }
            MovilCliente.Contacto k = MovilCliente.contacto(id);
            if (k != null && Ui.dentro(x, y, 8, cy2 + 46, W - 16, 18)) {
                int nuevo = k.tono() + 1 >= com.miscelanea.movil.MovilRegistro.TONOS ? -1 : k.tono() + 1;
                MovilCliente.tonoContacto(id, nuevo);
                // se ve ya, sin esperar al servidor
                int i = MovilCliente.CONTACTOS.indexOf(k);
                if (i >= 0) MovilCliente.CONTACTOS.set(i, new MovilCliente.Contacto(k.id(), k.nombre(), k.numero(), k.conectado(), nuevo));
                var sm = net.minecraft.client.Minecraft.getInstance().getSoundManager();
                if (muestra != null) sm.stop(muestra);
                muestra = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.miscelanea.movil.MovilRegistro.TONO[nuevo < 0 ? MovilCliente.tono : nuevo].get(), 1.0F, 0.8F);
                sm.play(muestra);
                return true;
            }
            if (MovilCliente.ubiDe(id) != null && Ui.dentro(x, y, 10, cy2 + 30, W - 20, 14)) {
                MovilCliente.clic();
                m.abrir(new VistasMapa.Mapa(id));
                return true;
            }
            return false;
        }
    }

    // ================================================================== llamada / videollamada
    /** Cara de alguien de la llamada; los de número oculto, un círculo con una interrogación. */
    static void caraLlamada(GuiGraphics g, UUID id, int x, int y, int tam, int fondo) {
        if (id == null || id.equals(com.miscelanea.movil.Llamadas.OCULTO)) {
            Ui.redondo(g, x, y, tam, tam, tam / 2, 0xFF4A5560);
            Ui.grande(g, "?", x + tam / 2 + 1, y + tam / 2 - tam / 6 - 2, Math.max(1, tam / 12), 0xFFFFFFFF);
        } else Ui.cara(g, id, x, y, tam, fondo);
    }

    static final class Llamada extends Vista {
        private long ultFrame;
        private static final int TW = (W - 12) / 2;

        @Override
        int fondoBarra() {
            return 0;
        }

        private static boolean video() {
            return MovilCliente.llEstado == MovilRed.LL_ACTIVA && MovilCliente.miVideo && MovilCliente.llVideoPermitido;
        }

        /** Se ve como llamada de grupo (cuadrícula) y no como llamada de uno a uno. */
        private static boolean enGrupo() {
            return MovilCliente.llGrupo || MovilCliente.PARTES.size() > 1;
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

        private void enviarVideo() {
            if (!video()) return;
            MovilCaptura.pedir();
            long ms = Util.getMillis();
            if (MovilCaptura.lista() && ms - ultFrame >= 1000 / MovilCliente.vFps) {
                ultFrame = ms;
                byte[] f = MovilCaptura.video(MovilCliente.vW, MovilCliente.vH, MovilCliente.vMax);
                if (f != null) MovilRed.alServidor(MovilRed.C_VIDEO, b -> b.writeByteArray(f));
            }
        }

        private static String estadoTexto(int est) {
            return switch (est) {
                case MovilRed.LL_SALIENTE -> t("llamando") + puntos();
                case MovilRed.LL_ENTRANTE -> t(MovilCliente.llGrupo ? "llamada_grupo_entrante" : MovilCliente.llVideo ? "videollamada_entrante" : "llamada_entrante");
                case MovilRed.LL_ACTIVA -> MovilCliente.duracion((Util.getMillis() - MovilCliente.llInicio) / 1000);
                default -> t("fin" + MovilCliente.llMotivo);
            };
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            int est = MovilCliente.llEstado;
            boolean activa = est == MovilRed.LL_ACTIVA;
            enviarVideo();
            if (enGrupo() && est != MovilRed.LL_ENTRANTE) dibujarGrupo(g, est);
            else dibujarUno(g, est, activa);
            dibujarSegunda(g, mx, my);
            dibujarBotones(g, mx, my, est, activa);
        }

        /** Llamada de uno a uno: su vídeo a pantalla completa o su cara grande. */
        private void dibujarUno(GuiGraphics g, int est, boolean activa) {
            MovilCliente.Parte p = MovilCliente.PARTES.isEmpty() ? null : MovilCliente.PARTES.get(0);
            UUID otro = p == null ? MovilCliente.llOtro : p.id();
            FotoCache.Tex remoto = activa && otro != null ? FotoCache.video(otro) : null;
            if (remoto != null) FotoCache.cubrir(g, remoto, 0, 0, W, H);
            else {
                g.fillGradient(0, 0, W, H, 0xFF1D3B4F, 0xFF0B1218);
                if (est == MovilRed.LL_ENTRANTE && MovilCliente.llGrupo) Ui.avatarGrupo(g, MovilCliente.llNombre, W / 2 - 20, 52, 40);
                else {
                    if (activa && com.miscelanea.client.pociones.EcoCliente.hablando(otro)) Ui.redondo(g, W / 2 - 23, 49, 46, 46, 6, 0xFF2FBF73);
                    caraLlamada(g, otro, W / 2 - 20, 52, 40, 0xFF173041);
                }
            }
            if (video()) {
                int pw = 34, ph = 60, px = W - pw - 6, py = BARRA + 4;
                if (MovilCliente.dosEstado >= 0) py += 26;
                Ui.redondo(g, px - 1, py - 1, pw + 2, ph + 2, 3, 0xFFFFFFFF);
                if (MovilCaptura.lista()) MovilCaptura.dibujarVisor(g, px, py, pw, ph);
            }
            int ty = remoto != null ? BARRA + 6 : 100;
            if (MovilCliente.dosEstado >= 0 && remoto != null) ty += 26;
            if (remoto != null) Ui.rect(g, 0, ty - 6, W, 28, 0x60000000);
            int tx = remoto != null ? 8 : W / 2;
            String nombre = Ui.cortar(MovilCliente.llNombre, video() && remoto != null ? W - 52 : W - 12);
            String estado = estadoTexto(est);
            if (remoto != null) {
                Ui.texto(g, nombre, tx, ty, 0xFFFFFFFF);
                Ui.texto(g, estado, tx, ty + 10, 0xFFB9F5D2);
            } else {
                g.drawCenteredString(Ui.font(), nombre, tx, ty, 0xFFFFFFFF);
                Ui.centrado(g, estado, tx, ty + 12, est == MovilRed.LL_FIN ? Ui.ROJO : 0xFFB9F5D2);
                int extra = ty + 24;
                if (p != null && p.est() == com.miscelanea.movil.Llamadas.P_ESPERA) {
                    Ui.centrado(g, t("te_tiene_en_espera"), tx, extra, Ui.AMARILLO);
                    extra += 10;
                }
                if (activa && MovilCliente.miVideo && otro != null && FotoCache.video(otro) == null) Ui.centrado(g, t("sin_video_otro"), tx, extra, 0xFF8FA3B3);
                if (MovilCliente.llOcultoYo && est != MovilRed.LL_FIN) Ui.centrado(g, t("numero_oculto"), tx, ty - 10, 0xFF8FA3B3);
            }
        }

        /** Llamada de grupo: una casilla por persona (con su vídeo o su cara; borde verde si habla). */
        private void dibujarGrupo(GuiGraphics g, int est) {
            g.fillGradient(0, 0, W, H, 0xFF1D3B4F, 0xFF0B1218);
            int y0 = BARRA + 4 + (MovilCliente.dosEstado >= 0 ? 26 : 0);
            Ui.texto(g, Ui.cortar(MovilCliente.llNombre, W - 12), 6, y0, 0xFFFFFFFF);
            Ui.mini(g, estadoTexto(est) + "  ·  " + t("personas", MovilCliente.PARTES.size() + 1), 6, y0 + 11, 0xFFB9F5D2);
            int ty0 = y0 + 20;
            int filas = (MovilCliente.PARTES.size() + 2) / 2;
            int libre = (H - ABAJO - 22 - 34 - 18) - ty0;
            int th = Math.max(30, Math.min(62, (libre - 4 * (filas - 1)) / filas));
            int n = 0;
            for (MovilCliente.Parte p : MovilCliente.PARTES) {
                int x = 4 + (n % 2) * (TW + 4), y = ty0 + (n / 2) * (th + 4);
                casilla(g, x, y, th, p.id(), MovilCliente.nombreVisible(p.id(), p.nombre()), p.est(), est == MovilRed.LL_ACTIVA);
                n++;
            }
            // yo
            final int TH = th;
            int x = 4 + (n % 2) * (TW + 4), y = ty0 + (n / 2) * (TH + 4);
            Ui.redondo(g, x, y, TW, TH, 4, 0xFF15232E);
            int ct = Math.min(24, TH - 18);
            if (video() && MovilCaptura.lista()) MovilCaptura.dibujarVisor(g, x + 2, y + 2, TW - 4, TH - 4);
            else {
                var me = net.minecraft.client.Minecraft.getInstance().player;
                if (me != null) Ui.cara(g, me.getUUID(), x + TW / 2 - ct / 2, y + (TH - 12 - ct) / 2, ct, 0xFF15232E);
            }
            boolean hablo = !MovilCliente.llMute && com.miscelanea.client.pociones.EcoCliente.yoHablando();
            if (hablo) borde(g, x, y, TW, TH, 0xFF2FBF73);
            Ui.rect(g, x, y + TH - 12, TW, 12, 0x90000000);
            Ui.mini(g, t("tu") + (MovilCliente.llMute ? " · " + t("mute_corto") : ""), x + 3, y + TH - 9, MovilCliente.llMute ? Ui.ROJO : 0xFFFFFFFF);
        }

        private static void casilla(GuiGraphics g, int x, int y, int TH, UUID id, String nombre, int pe, boolean activa) {
            Ui.redondo(g, x, y, TW, TH, 4, 0xFF15232E);
            FotoCache.Tex v = activa && pe == com.miscelanea.movil.Llamadas.P_DENTRO ? FotoCache.video(id) : null;
            int ct = Math.min(24, TH - 18);
            if (v != null) FotoCache.cubrir(g, v, x, y, TW, TH);
            else caraLlamada(g, id, x + TW / 2 - ct / 2, y + (TH - 12 - ct) / 2, ct, 0xFF15232E);
            if (pe == com.miscelanea.movil.Llamadas.P_DENTRO && com.miscelanea.client.pociones.EcoCliente.hablando(id)) borde(g, x, y, TW, TH, 0xFF2FBF73);
            if (pe != com.miscelanea.movil.Llamadas.P_DENTRO) Ui.rect(g, x, y, TW, TH, 0x70000000);
            Ui.rect(g, x, y + TH - 12, TW, 12, 0x90000000);
            Ui.mini(g, Ui.cortarMini(nombre, TW - 6), x + 3, y + TH - 9, 0xFFFFFFFF);
            if (pe == com.miscelanea.movil.Llamadas.P_SONANDO) Ui.mini(g, t("llamando") + puntos(), x + 3, y + 3, 0xFFB9F5D2);
            else if (pe == com.miscelanea.movil.Llamadas.P_ESPERA) Ui.mini(g, t("en_espera"), x + 3, y + 3, Ui.AMARILLO);
        }

        private static void borde(GuiGraphics g, int x, int y, int w, int h, int c) {
            Ui.rect(g, x, y, w, 2, c);
            Ui.rect(g, x, y + h - 2, w, 2, c);
            Ui.rect(g, x, y, 2, h, c);
            Ui.rect(g, x + w - 2, y, 2, h, c);
        }

        /** Arriba: la llamada que me entra mientras hablo, o la que tengo en espera. */
        private void dibujarSegunda(GuiGraphics g, int mx, int my) {
            int d = MovilCliente.dosEstado;
            if (d < 0 || MovilCliente.llEstado == MovilRed.LL_FIN) return;
            int y = BARRA + 1;
            Ui.redondo(g, 4, y, W - 8, 24, 5, 0xF2252D36);
            caraLlamada(g, MovilCliente.dosId, 7, y + 4, 16, 0xF2252D36);
            Ui.mini(g, Ui.cortarMini(MovilCliente.dosNombre, W - 76), 27, y + 4, 0xFFFFFFFF);
            if (d == MovilRed.LL_ENTRANTE) {
                Ui.mini(g, Ui.cortarMini(t("llamada_en_espera"), W - 76), 27, y + 13, 0xFFB9F5D2);
                Ui.boton(g, W - 38, y + 12, 8, Ui.dentro(mx, my, W - 46, y + 4, 16, 16) ? 0xFFD43C3C : Ui.ROJO, Ui.G_COLGAR, 0xFFFFFFFF);
                Ui.boton(g, W - 17, y + 12, 8, Ui.dentro(mx, my, W - 25, y + 4, 16, 16) ? 0xFF2FBF73 : Ui.ACENTO, Ui.G_TELEFONO, 0xFFFFFFFF);
            } else {
                Ui.mini(g, Ui.cortarMini(t("en_espera_tocar"), W - 56), 27, y + 13, Ui.AMARILLO);
                Ui.boton(g, W - 17, y + 12, 8, Ui.dentro(mx, my, W - 25, y + 4, 16, 16) ? 0xFF3A86D6 : Ui.AZUL, Ui.G_CAMBIAR, 0xFFFFFFFF);
            }
        }

        private static final int[] BX = {W / 2 - 45, W / 2 - 15, W / 2 + 15, W / 2 + 45};

        private void dibujarBotones(GuiGraphics g, int mx, int my, int est, boolean activa) {
            int cy = H - ABAJO - 22;
            if (est == MovilRed.LL_ENTRANTE) {
                long a = (Util.getMillis() / 120) % 8;
                Ui.boton(g, W / 2 - 34, cy, 14, Ui.ROJO, Ui.G_COLGAR, 0xFFFFFFFF);
                Ui.boton(g, W / 2 + 34, cy - (a < 2 ? (int) a : 0), 14, Ui.ACENTO, MovilCliente.llVideo ? Ui.G_VIDEO : Ui.G_TELEFONO, 0xFFFFFFFF);
                Ui.centrado(g, t("rechazar"), W / 2 - 34, cy + 17, 0xFFCCD5DD);
                Ui.centrado(g, t("contestar"), W / 2 + 34, cy + 17, 0xFFCCD5DD);
                return;
            }
            if (est == MovilRed.LL_FIN) return;
            Ui.boton(g, W / 2, cy, 13, Ui.dentro(mx, my, W / 2 - 13, cy - 13, 26, 26) ? 0xFFD43C3C : Ui.ROJO, Ui.G_COLGAR, 0xFFFFFFFF);
            // fila de controles: silenciar, manos libres, cámara y girar
            int cy2 = cy - 34;
            Ui.rect(g, 0, cy2 - 16, W, H - (cy2 - 16), 0x50000000);
            boolean[] on = {MovilCliente.llMute, false, MovilCliente.miVideo, !MovilCliente.frontal};
            int[] gl = {MovilCliente.llMute ? Ui.G_MICRO_NO : Ui.G_MICRO, Ui.G_ALTAVOZ, MovilCliente.miVideo ? Ui.G_VIDEO : Ui.G_VIDEO_NO, Ui.G_GIRAR};
            String[] tx = {t("silenciar"), t("manos_libres_corto"), t("camara_corto"), t("girar")};
            for (int i = 0; i < 4; i++) {
                if (!boton(i, activa)) continue;
                boolean s = Ui.enCirculo(mx, my, BX[i], cy2, 11);
                Ui.boton(g, BX[i], cy2, 11, on[i] ? 0xFFFFFFFF : s ? 0xC0404A54 : 0x80303A44, gl[i], on[i] ? 0xFF101418 : 0xFFFFFFFF);
                Ui.mini(g, tx[i], BX[i] - Ui.anchoMini(tx[i]) / 2, cy2 + 13, 0xFFCCD5DD);
            }
        }

        /** Qué botones de la fila se ven. */
        private static boolean boton(int i, boolean activa) {
            return switch (i) {
                case 0, 1 -> true;
                case 2 -> activa && MovilCliente.llVideoPermitido;
                default -> activa && MovilCliente.llVideoPermitido && MovilCliente.miVideo;
            };
        }

        private static String puntos() {
            return ".".repeat((int) ((Util.getMillis() / 400) % 4));
        }

        @Override
        boolean clic(double x, double y) {
            int est = MovilCliente.llEstado, cy = H - ABAJO - 22;
            // segunda llamada
            if (MovilCliente.dosEstado >= 0 && est != MovilRed.LL_FIN) {
                int yy = BARRA + 1;
                if (MovilCliente.dosEstado == MovilRed.LL_ENTRANTE) {
                    if (Ui.dentro(x, y, W - 46, yy + 4, 16, 16)) {
                        MovilRed.alServidor(MovilRed.C_CONTESTAR, b -> b.writeBoolean(false));
                        return true;
                    }
                    if (Ui.dentro(x, y, W - 25, yy + 4, 16, 16)) {
                        MovilCliente.clic();
                        MovilRed.alServidor(MovilRed.C_CONTESTAR, b -> b.writeBoolean(true));
                        return true;
                    }
                } else if (Ui.dentro(x, y, 4, yy, W - 8, 24)) {
                    MovilCliente.clic();
                    MovilRed.alServidor(MovilRed.C_CAMBIAR_LLAMADA, null);
                    return true;
                }
            }
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
            if (Ui.enCirculo(x, y, W / 2, cy, 14)) {
                MovilRed.alServidor(MovilRed.C_COLGAR, null);
                return true;
            }
            int cy2 = cy - 34;
            boolean activa = est == MovilRed.LL_ACTIVA;
            for (int i = 0; i < 4; i++) {
                if (!boton(i, activa) || !Ui.enCirculo(x, y, BX[i], cy2, 12)) continue;
                MovilCliente.clic();
                switch (i) {
                    case 0 -> MovilCliente.silenciar(!MovilCliente.llMute);
                    case 1 -> MovilCliente.ponerManosLibres();
                    case 2 -> {
                        MovilCliente.miVideo = !MovilCliente.miVideo;
                        if (!MovilCliente.miVideo) MovilRed.alServidor(MovilRed.C_VIDEO, b -> b.writeByteArray(new byte[0]));
                    }
                    default -> MovilCliente.frontal = !MovilCliente.frontal;
                }
                return true;
            }
            return false;
        }
    }
}
