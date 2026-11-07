package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** App de mensajes: lista de chats, conversación (individual o de grupo, con fotos), nuevo chat y elegir chat para mandar una foto. */
final class VistasChat {
    private VistasChat() {}

    static final int Y0 = Vista.BARRA + Vista.CAB + 1, FILA = 28;

    /** Campo de texto mínimo: escribir al final, borrar, pegar. */
    static final class Campo {
        final StringBuilder s = new StringBuilder();
        final int max;

        Campo(int max) {
            this.max = max;
        }

        boolean caracter(char c) {
            if (Character.isSurrogate(c) || c == '§' || c < 32 || s.length() >= max) return Character.isSurrogate(c);
            s.append(c);
            return true;
        }

        boolean tecla(int key) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (s.length() == 0) return true;
                if (Screen.hasControlDown()) {
                    int i = s.length() - 1;
                    while (i > 0 && s.charAt(i - 1) == ' ') i--;
                    while (i > 0 && s.charAt(i - 1) != ' ') i--;
                    s.setLength(Math.max(0, i));
                } else s.setLength(s.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_V && Screen.hasControlDown()) {
                String p = Minecraft.getInstance().keyboardHandler.getClipboard();
                for (char c : p.toCharArray()) if (c != '\n' && c != '\r') caracter(c);
                return true;
            }
            return false;
        }

        void dibujar(GuiGraphics g, int x, int y, int w, String marcador, boolean foco) {
            String txt = s.toString();
            if (txt.isEmpty()) {
                Ui.texto(g, Ui.cortar(marcador, w), x, y, 0xFF66717C);
                if (foco && (Util.getMillis() / 500) % 2 == 0) Ui.rect(g, x, y - 1, 1, 9, Ui.ACENTO);
                return;
            }
            String vis = txt;
            while (Ui.ancho(vis) > w - 2 && vis.length() > 0) vis = vis.substring(1);
            Ui.texto(g, vis, x, y, Ui.TEXTO);
            if (foco && (Util.getMillis() / 500) % 2 == 0) Ui.rect(g, x + Ui.ancho(vis), y - 1, 1, 9, Ui.ACENTO);
        }
    }

    static void avatar(GuiGraphics g, MovilCliente.ChatInfo c, int x, int y, int tam, int fondo) {
        if (c.grupo) Ui.avatarGrupo(g, c.nombre, x, y, tam);
        else Ui.cara(g, otro(c.miembros), x, y, tam, fondo);
    }

    static UUID otro(List<UUID> miembros) {
        var p = Minecraft.getInstance().player;
        for (UUID u : miembros) if (p == null || !u.equals(p.getUUID())) return u;
        return miembros.isEmpty() ? null : miembros.get(0);
    }

    static String vistaPrevia(MovilCliente.Msg m, boolean grupo) {
        if (m == null) return "";
        var p = Minecraft.getInstance().player;
        String quien = p != null && m.de().equals(p.getUUID()) ? Vista.t("tu") + ": " : grupo ? m.nombreDe() + ": " : "";
        return quien + (m.adjunto() >= 0 ? Vista.t(m.voz() < 0 && m.vozMs() > 0 ? "video_adjunto" : "foto_adjunta") : m.voz() >= 0 ? Vista.t("nota_voz", MovilCliente.duracion(m.vozMs() / 1000)) : m.texto());
    }

    /** Lista de chats para tocar uno (la usan la app y "enviar foto a..."). */
    static void listaChats(Vista v, GuiGraphics g, int mx, int my) {
        v.recortar(g, Y0, Vista.H - Vista.ABAJO);
        List<MovilCliente.ChatInfo> l = MovilCliente.CHATS;
        int y = Y0 - (int) v.scroll;
        for (MovilCliente.ChatInfo c : l) {
            if (y + FILA > Y0 && y < Vista.H) {
                if (Ui.dentro(mx, my, 0, y, Vista.W, FILA) && my >= Y0) Ui.rect(g, 0, y, Vista.W, FILA, 0x10FFFFFF);
                avatar(g, c, 6, y + 4, 20, Ui.FONDO);
                String hora = c.ultimo == null ? "" : Vista.cuando(c.ultimo.t());
                Ui.texto(g, Ui.cortar(c.nombre, Vista.W - 40 - Ui.anchoMini(hora)), 31, y + 5, Ui.TEXTO);
                Ui.mini(g, hora, Vista.W - 6 - Ui.anchoMini(hora), y + 7, c.noLeidos > 0 ? Ui.ACENTO : Ui.TEXTO2);
                int px = 31;
                if (c.ultimo != null && (c.ultimo.adjunto() >= 0 || c.ultimo.voz() >= 0)) {
                    Ui.glifo(g, c.ultimo.voz() >= 0 ? Ui.G_MICRO : Ui.G_FOTO, 31, y + 16, c.ultimo.voz() >= 0 ? Ui.ACENTO : Ui.TEXTO2);
                    px = 42;
                }
                int anchoPrev = Vista.W - px - 6 - (c.noLeidos > 0 ? 14 : 0);
                Ui.texto(g, Ui.cortar(vistaPrevia(c.ultimo, c.grupo), anchoPrev), px, y + 16, Ui.TEXTO2);
                if (c.noLeidos > 0) Ui.insignia(g, c.noLeidos, Vista.W - 12, y + 20, Ui.ACENTO);
                Ui.rect(g, 31, y + FILA - 1, Vista.W - 31, 1, Ui.LINEA);
            }
            y += FILA;
        }
        v.scrollMax = l.size() * FILA - (Vista.H - Vista.ABAJO - Y0);
        v.barraScroll(g, Y0, Vista.H - Vista.ABAJO);
        v.soltarRecorte(g);
        if (l.isEmpty()) Ui.centrado(g, Vista.t(MovilCliente.sincronizado ? "sin_chats" : "cargando"), Vista.W / 2, Vista.H / 2 - 4, Ui.TEXTO2);
    }

    static MovilCliente.ChatInfo chatEn(Vista v, double y) {
        if (y < Y0) return null;
        int i = (int) ((y - Y0 + v.scroll) / FILA);
        return i >= 0 && i < MovilCliente.CHATS.size() ? MovilCliente.CHATS.get(i) : null;
    }

    // ================================================================== lista
    static final class Chats extends Vista {
        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_SYNC, null);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            listaChats(this, g, mx, my);
            cabecera(g, t("app_chats"), mx, my);
            boolean s = Ui.dentro(mx, my, W - 20, BARRA, 20, CAB);
            Ui.boton(g, W - 11, BARRA + 10, 7, s ? 0xFF2FBF73 : Ui.ACENTO, Ui.G_MAS, 0xFF0F1419);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (Ui.dentro(x, y, W - 20, BARRA, 20, CAB)) {
                MovilCliente.clic();
                m.abrir(new NuevoChat());
                return true;
            }
            MovilCliente.ChatInfo c = chatEn(this, y);
            if (c != null) {
                MovilCliente.clic();
                m.abrir(new Chat(c.id));
                return true;
            }
            return false;
        }
    }

    // ================================================================== conversación
    static final class Chat extends Vista {
        final int id;
        private final Campo campo = new Campo(256);
        /** Desplazamiento desde abajo (0 = lo último). */
        private double desdeAbajo, maxAbajo;
        private final Map<MovilCliente.Msg, Burbuja> cache = new IdentityHashMap<>();
        private long confirmarSalir;
        private final List<Object[]> fotosVisibles = new ArrayList<>(), vocesVisibles = new ArrayList<>();
        /** El micro está pulsado (grabando una nota de voz). */
        private boolean microPulsado;

        /** fw/fh: tamaño de la foto adjunta (apaisada o vertical; hasta que llega, el de una vertical). */
        private record Burbuja(List<FormattedCharSequence> lineas, int w, int h, boolean mia, boolean nombre, int fw, int fh) {}

        Chat(int id) {
            this.id = id;
        }

        @Override
        void abierta() {
            if (MovilCliente.chat == null || MovilCliente.chat.id != id) MovilCliente.chat = null;
            MovilRed.alServidor(MovilRed.C_CHAT_ABRIR, b -> b.writeVarInt(id));
        }

        @Override
        void cerrada() {
            if (microPulsado) NotasVozCliente.terminar(false);
            microPulsado = false;
        }

        private boolean microVisible() {
            return campo.s.length() == 0 && MovilCliente.notasVoz();
        }

        @Override
        boolean pulsar(double x, double y) {
            int abajo = H - ABAJO - ENTRADA;
            if (microVisible() && Ui.dentro(x, y, W - 20, abajo, 20, ENTRADA) && NotasVozCliente.empezar(id)) {
                microPulsado = true;
                MovilCliente.sonido(com.miscelanea.movil.MovilRegistro.CLIC.get(), 0.6F, 1.6F);
                return true;
            }
            return false;
        }

        @Override
        void soltar(double x, double y) {
            if (!microPulsado) return;
            microPulsado = false;
            // deslizar hacia arriba (fuera de la barra) = cancelar
            NotasVozCliente.terminar(y >= H - ABAJO - ENTRADA - 26);
            desdeAbajo = 0;
        }

        @Override
        boolean escribiendo() {
            return true;
        }

        private static final int ENTRADA = 18, MAXW = 92, VOZ_W = 96, BARRAS = 40;

        private Burbuja burbuja(MovilCliente.Msg msg, boolean grupo) {
            Burbuja hecha = cache.get(msg);
            if (hecha != null) return hecha;
            FotoCache.Tex ft = msg.adjunto() >= 0 ? FotoCache.get(MovilRed.FOTO_ADJUNTA, msg.adjunto()) : null;
            Burbuja nb = hacer(msg, grupo, ft);
            // con foto, se guarda cuando ya se sabe su forma (si no, se vuelve a medir al llegar)
            if (msg.adjunto() < 0 || ft != null) cache.put(msg, nb);
            return nb;
        }

        private Burbuja hacer(MovilCliente.Msg msg, boolean grupo, FotoCache.Tex ft) {
            {
                MovilCliente.Msg k = msg;
                var p = Minecraft.getInstance().player;
                boolean mia = p != null && k.de().equals(p.getUUID());
                boolean nombre = grupo && !mia;
                List<FormattedCharSequence> l = k.texto().isEmpty() ? List.of() : Ui.envolver(k.texto(), MAXW);
                int w = 0;
                for (FormattedCharSequence s : l) w = Math.max(w, Ui.font().width(s));
                if (nombre) w = Math.max(w, Math.min(MAXW, Ui.ancho(k.nombreDe())));
                boolean apaisada = ft != null && ft.w > ft.h;
                int fw = apaisada ? MAXW : 54, fh = apaisada ? 52 : 96;
                if (k.adjunto() >= 0) w = Math.max(w, fw);
                if (k.voz() >= 0) w = Math.max(w, VOZ_W);
                w = Math.max(w, Ui.anchoMini("00:00") + 4) + 8;
                int h = 4 + l.size() * 9 + 7 + (nombre ? 10 : 0) + (k.adjunto() >= 0 ? fh + 2 : 0) + (k.voz() >= 0 ? 18 : 0);
                return new Burbuja(l, w, h, mia, nombre, w - 8, fh);
            }
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            Ui.rect(g, 0, 0, W, H, 0xFF0B1014);
            // patrón sutil de fondo
            for (int y = Y0; y < H; y += 12) for (int x = (y / 12) % 2 * 6; x < W; x += 12) Ui.rect(g, x, y, 1, 1, 0xFF141B21);
            MovilCliente.ChatAbierto c = MovilCliente.chat != null && MovilCliente.chat.id == id ? MovilCliente.chat : null;
            int abajo = H - ABAJO - ENTRADA;
            fotosVisibles.clear();
            vocesVisibles.clear();
            if (c == null) Ui.centrado(g, t("cargando"), W / 2, H / 2 - 4, Ui.TEXTO2);
            else {
                recortar(g, Y0, abajo);
                int total = 4;
                for (MovilCliente.Msg msg : c.msgs) total += burbuja(msg, c.grupo).h + 3;
                maxAbajo = Math.max(0, total - (abajo - Y0));
                desdeAbajo = Mth.clamp(desdeAbajo, 0, maxAbajo);
                int y = abajo - 3 + (int) desdeAbajo;
                for (int i = c.msgs.size() - 1; i >= 0; i--) {
                    MovilCliente.Msg msg = c.msgs.get(i);
                    Burbuja b = burbuja(msg, c.grupo);
                    y -= b.h + 3;
                    if (y > abajo || y + b.h < Y0) continue;
                    dibujarBurbuja(g, msg, b, y, mx, my);
                }
                if (c.msgs.isEmpty()) {
                    Ui.redondo(g, 14, Y0 + 12, W - 28, 22, 6, 0xFF1C2A33);
                    Ui.centrado(g, t("chat_vacio"), W / 2, Y0 + 19, 0xFFB0BCC6);
                }
                soltarRecorte(g);
            }
            // barra de escribir
            Ui.rect(g, 0, abajo, W, ENTRADA + ABAJO, Ui.CABECERA);
            if (microPulsado && NotasVozCliente.grabando()) {
                long ms = NotasVozCliente.msGrabados();
                boolean cancelar = my < abajo - 26 && my > -900;
                if ((Util.getMillis() / 400) % 2 == 0) Ui.circulo(g, 8, abajo + 9, 3, Ui.ROJO);
                String tt = MovilCliente.duracion(ms / 1000) + " / " + MovilCliente.duracion(MovilCliente.vozMaxSeg);
                Ui.texto(g, tt, 15, abajo + 5, Ui.TEXTO);
                g.pose().pushPose();
                g.pose().translate(18 + Ui.ancho(tt), abajo + 5, 0);
                g.pose().scale(0.5F, 0.5F, 1);
                g.drawString(Ui.font(), t(cancelar ? "nota_suelta_cancelar" : "nota_suelta_enviar"), 0, 0, cancelar ? Ui.ROJO : Ui.TEXTO2, false);
                g.drawString(Ui.font(), t("nota_desliza"), 0, 9, 0xFF66717C, false);
                g.pose().popPose();
                int r = 9 + (int) (NotasVozCliente.nivel() * 6);
                Ui.circulo(g, W - 12, abajo + 4, r, 0x40EF4B4B);
                Ui.boton(g, W - 12, abajo + 4, 9, cancelar ? 0xFF59636D : Ui.ROJO, Ui.G_MICRO, 0xFFFFFFFF);
            } else {
                boolean sf = Ui.dentro(mx, my, 0, abajo, 16, ENTRADA);
                Ui.glifo(g, Ui.G_CAMARA, 5, abajo + 5, sf ? 0xFFFFFFFF : Ui.TEXTO2);
                Ui.redondo(g, 17, abajo + 2, W - 37, 14, 7, 0xFF2A333D);
                campo.dibujar(g, 23, abajo + 5, W - 47, t("escribe"), true);
                boolean hay = campo.s.length() > 0;
                if (!hay && MovilCliente.notasVoz()) {
                    boolean sm = Ui.dentro(mx, my, W - 20, abajo, 20, ENTRADA);
                    Ui.boton(g, W - 10, abajo + 9, 7, sm ? 0xFF2FBF73 : Ui.ACENTO, Ui.G_MICRO, 0xFF0F1419);
                } else Ui.boton(g, W - 10, abajo + 9, 7, hay ? Ui.ACENTO : 0xFF2A333D, Ui.G_ENVIAR, hay ? 0xFF0F1419 : Ui.TEXTO2);
            }
            // cabecera
            Ui.rect(g, 0, 0, W, BARRA + CAB, Ui.CABECERA);
            Ui.glifo(g, Ui.G_ATRAS, 5, BARRA + 6, Ui.TEXTO);
            MovilCliente.ChatInfo ci = MovilCliente.chatInfo(id);
            String nombre = c != null ? c.nombre : ci != null ? ci.nombre : "";
            boolean grupo = c != null ? c.grupo : ci != null && ci.grupo;
            if (ci != null) avatar(g, ci, 17, BARRA + 3, 14, Ui.CABECERA);
            else if (c != null && !grupo) Ui.cara(g, otro(new ArrayList<>(c.miembros.keySet())), 17, BARRA + 3, 14, Ui.CABECERA);
            UUID otroId = c != null && !grupo ? otro(new ArrayList<>(c.miembros.keySet())) : null;
            int ind = VistasMapa.indicador(g, otroId, W - 31, BARRA + 1);
            Ui.texto(g, Ui.cortar(nombre, W - 66 - ind - (grupo ? 14 : 0)), 35, BARRA + 2, Ui.TEXTO);
            String sub = "";
            if (c != null) {
                if (grupo) sub = t("miembros", c.miembros.size());
                else {
                    MovilCliente.Contacto k = MovilCliente.contacto(otro(new ArrayList<>(c.miembros.keySet())));
                    sub = k == null ? "" : t(k.conectado() ? "en_linea" : "desconectado");
                }
            }
            g.pose().pushPose();
            g.pose().translate(35, BARRA + 12, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.drawString(Ui.font(), sub, 0, 0, 0xFF7FD7A5, false);
            g.pose().popPose();
            if (c != null && MovilCliente.ubicacion) {
                boolean compartiendo = false;
                for (UUID u : c.miembros.keySet()) if (MovilCliente.ubiMia(u) != null) compartiendo = true;
                boolean sp = Ui.dentro(mx, my, W - 31, BARRA, 14, CAB);
                Ui.glifo(g, Ui.G_PIN, W - 27, BARRA + 6, compartiendo ? Ui.AZUL : sp ? 0xFFFFFFFF : Ui.TEXTO2);
            }
            if (grupo && c != null) {
                // llamada de grupo
                Ui.glifo(g, Ui.G_TELEFONO, W - 41, BARRA + 6, Ui.dentro(mx, my, W - 45, BARRA, 14, CAB) ? 0xFFFFFFFF : Ui.ACENTO);
            }
            if (grupo) {
                boolean conf = Util.getMillis() - confirmarSalir < 3000;
                Ui.glifo(g, Ui.G_SALIR, W - 13, BARRA + 6, conf ? Ui.ROJO : Ui.TEXTO2);
                if (conf) Ui.texto(g, t("salir_confirmar"), W - 16 - Ui.ancho(t("salir_confirmar")), BARRA + 6, Ui.ROJO);
            } else if (c != null) {
                Ui.glifo(g, Ui.G_TELEFONO, W - 13, BARRA + 6, Ui.dentro(mx, my, W - 17, BARRA, 17, CAB) ? 0xFFFFFFFF : Ui.TEXTO2);
            }
            Ui.rect(g, 0, BARRA + CAB, W, 1, Ui.LINEA);
        }

        private void dibujarBurbuja(GuiGraphics g, MovilCliente.Msg msg, Burbuja b, int y, int mx, int my) {
            int x = b.mia ? W - 5 - b.w : 5;
            int col = b.mia ? Ui.MIA : Ui.OTRO;
            Ui.redondo(g, x, y, b.w, b.h, 4, col);
            // pico
            if (b.mia) {
                Ui.rect(g, x + b.w - 2, y, 4, 2, col);
                Ui.rect(g, x + b.w + 2, y, 1, 1, col);
            } else {
                Ui.rect(g, x - 2, y, 4, 2, col);
                Ui.rect(g, x - 3, y, 1, 1, col);
            }
            int ty = y + 3;
            if (b.nombre) {
                int[] cols = {0xFF7FB8FF, 0xFFFFA07A, 0xFF8FE3A8, 0xFFE0A6FF, 0xFFFFD27F, 0xFF7FE0E0};
                Ui.texto(g, Ui.cortar(msg.nombreDe(), MAXW), x + 4, ty, cols[Math.floorMod(msg.de().hashCode(), cols.length)]);
                ty += 10;
            }
            if (msg.voz() >= 0) {
                int cx = x + 11, cy = ty + 8;
                boolean suena = NotasVozCliente.sonando(msg.voz()), carga = NotasVozCliente.cargando(msg.voz());
                boolean falta = NotasVozCliente.falta(msg.voz());
                Ui.circulo(g, cx, cy, 7, b.mia ? 0xFF2C8A68 : 0xFF33404C);
                if (carga) {
                    int k = (int) (Util.getMillis() / 120 % 8);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        Ui.rect(g, cx + (int) Math.round(Math.cos(a) * 4), cy + (int) Math.round(Math.sin(a) * 4), 1, 1, i == k ? 0xFFFFFFFF : 0x60FFFFFF);
                    }
                } else Ui.glifo(g, falta ? Ui.G_BORRAR : suena ? Ui.G_PAUSA : Ui.G_PLAY, cx - 4, cy - 4, falta ? Ui.ROJO : 0xFFFFFFFF);
                float prog = NotasVozCliente.progreso(msg.voz());
                byte[] onda = msg.onda();
                int bx = x + 21;
                for (int i = 0; i < BARRAS; i++) {
                    int v = onda.length == 0 ? 40 : onda[Math.min(onda.length - 1, i * onda.length / BARRAS)] & 0xFF;
                    int h = 1 + v * 11 / 255;
                    boolean oida = suena && i < prog * BARRAS;
                    int cb = oida ? (b.mia ? 0xFFB9F5D2 : Ui.AZUL) : 0x90FFFFFF;
                    Ui.rect(g, bx + i * 2, cy - h / 2, 1, h, cb);
                }
                String d = MovilCliente.duracion((suena ? (long) (prog * msg.vozMs()) : msg.vozMs()) / 1000);
                Ui.mini(g, d, bx, ty + 17, 0x99FFFFFF);
                vocesVisibles.add(new Object[]{msg.voz(), x, y, b.w, b.h});
                ty += 18;
            }
            if (msg.adjunto() >= 0) {
                FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_ADJUNTA, msg.adjunto());
                int fx = x + 4, fw = b.fw(), fh = b.fh();
                if (t != null) FotoCache.cubrir(g, t, fx, ty, fw, fh);
                else {
                    Ui.rect(g, fx, ty, fw, fh, 0xFF151B20);
                    Ui.glifo(g, Ui.G_FOTO, fx + fw / 2 - 4, ty + fh / 2 - 4, Ui.TEXTO2);
                }
                if (msg.voz() < 0 && msg.vozMs() > 0) VistasFotos.marcaVideo(g, fx, ty, fw, fh, msg.vozMs());
                fotosVisibles.add(new Object[]{msg.adjunto(), fx, ty, fw, fh, msg.voz() < 0 ? msg.vozMs() : 0});
                ty += fh + 2;
            }
            for (FormattedCharSequence s : b.lineas) {
                g.drawString(Ui.font(), s, x + 4, ty, Ui.TEXTO, false);
                ty += 9;
            }
            String h = cuando(msg.t());
            Ui.mini(g, h, x + b.w - 4 - Ui.anchoMini(h), y + b.h - 7, 0x99FFFFFF);
        }

        private long ultEnvio;

        private void enviar() {
            String s = campo.s.toString().trim();
            // el servidor no admite dos mensajes en menos de 100 ms: así no se pierde ninguno
            if (s.isEmpty() || Util.getMillis() - ultEnvio < 150) return;
            ultEnvio = Util.getMillis();
            MovilRed.alServidor(MovilRed.C_MENSAJE, b -> {
                b.writeVarInt(id);
                b.writeUtf(s);
                b.writeVarInt(0);
            });
            campo.s.setLength(0);
            desdeAbajo = 0;
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
        void arrastrar(double x, double y, double dx, double dy) {
            desdeAbajo = Mth.clamp(desdeAbajo + dy, 0, maxAbajo);
        }

        @Override
        void rueda(double d) {
            desdeAbajo = Mth.clamp(desdeAbajo + d * 14, 0, maxAbajo);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int abajo = H - ABAJO - ENTRADA;
            MovilCliente.ChatAbierto c = MovilCliente.chat != null && MovilCliente.chat.id == id ? MovilCliente.chat : null;
            if (c != null && MovilCliente.ubicacion && Ui.dentro(x, y, W - 31, BARRA, 14, CAB)) {
                MovilCliente.clic();
                List<UUID> otros = new ArrayList<>();
                var yo = Minecraft.getInstance().player;
                for (UUID u : c.miembros.keySet()) if (yo == null || !u.equals(yo.getUUID())) otros.add(u);
                m.abrir(new VistasMapa.Compartir(otros, c.nombre));
                return true;
            }
            if (c != null && !c.grupo) {
                UUID o = otro(new ArrayList<>(c.miembros.keySet()));
                if (o != null && MovilCliente.ubiDe(o) != null && Ui.dentro(x, y, 35, BARRA, W - 66, CAB) && x > W - 31 - 60) {
                    MovilCliente.clic();
                    m.abrir(new VistasMapa.Mapa(o));
                    return true;
                }
            }
            if (c != null && c.grupo && Ui.dentro(x, y, W - 45, BARRA, 14, CAB)) {
                MovilCliente.clic();
                if (VistasTelefono.faltaVoz() == null) MovilCliente.llamarGrupo(id, false);
                else MovilCliente.aviso(net.minecraft.network.chat.Component.literal(VistasTelefono.faltaVoz()));
                return true;
            }
            if (Ui.dentro(x, y, W - 17, BARRA, 17, CAB) && c != null) {
                if (c.grupo) {
                    if (Util.getMillis() - confirmarSalir < 3000) {
                        MovilRed.alServidor(MovilRed.C_CHAT_SALIR, b -> b.writeVarInt(id));
                        m.atras();
                    } else confirmarSalir = Util.getMillis();
                } else {
                    UUID o = otro(new ArrayList<>(c.miembros.keySet()));
                    if (o != null) MovilCliente.llamar(o, false);
                }
                MovilCliente.clic();
                return true;
            }
            if (Ui.dentro(x, y, 0, abajo, 16, ENTRADA)) {
                MovilCliente.clic();
                m.abrir(new VistasFotos.Galeria(foto -> MovilRed.alServidor(MovilRed.C_MENSAJE, b -> {
                    b.writeVarInt(id);
                    b.writeUtf("");
                    b.writeVarInt(foto);
                })));
                return true;
            }
            if (Ui.dentro(x, y, W - 20, abajo, 20, ENTRADA)) {
                enviar();
                return true;
            }
            for (Object[] v : vocesVisibles)
                if (Ui.dentro(x, y, (int) v[1], (int) v[2], (int) v[3], (int) v[4]) && y >= Y0 && y < abajo) {
                    NotasVozCliente.alternar((int) v[0]);
                    return true;
                }
            for (Object[] f : fotosVisibles)
                if (Ui.dentro(x, y, (int) f[1], (int) f[2], (int) f[3], (int) f[4]) && y >= Y0 && y < abajo) {
                    MovilCliente.clic();
                    m.abrir(new VistasFotos.Foto(MovilRed.FOTO_ADJUNTA, (int) f[0], (int) f[5]));
                    return true;
                }
            return false;
        }
    }

    // ================================================================== nuevo chat / grupo
    static final class NuevoChat extends Vista {
        private final Set<UUID> elegidos = new LinkedHashSet<>();
        private final Campo nombre = new Campo(24);
        private static final int F = 22;

        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_SYNC, null);
        }

        @Override
        boolean escribiendo() {
            return elegidos.size() >= 2;
        }

        private int y0() {
            return Y0 + (elegidos.size() >= 2 ? 22 : 0);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int y0 = y0();
            if (elegidos.size() >= 2) {
                Ui.rect(g, 0, Y0, W, 22, Ui.TARJETA);
                Ui.glifo(g, Ui.G_GRUPO, 6, Y0 + 7, Ui.ACENTO);
                Ui.redondo(g, 18, Y0 + 4, W - 24, 14, 7, 0xFF2A333D);
                nombre.dibujar(g, 24, Y0 + 7, W - 36, t("nombre_grupo"), true);
            }
            int fin = H - ABAJO - (elegidos.isEmpty() ? 0 : 24);
            recortar(g, y0, fin);
            int y = y0 - (int) scroll;
            for (MovilCliente.Contacto k : MovilCliente.CONTACTOS) {
                boolean sel = elegidos.contains(k.id());
                if (Ui.dentro(mx, my, 0, y, W, F) && my >= y0 && my < fin) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                Ui.cara(g, k.id(), 7, y + 3, 16, Ui.FONDO);
                if (k.conectado()) Ui.circulo(g, 21, y + 17, 2, Ui.ACENTO);
                Ui.texto(g, Ui.cortar(k.nombre(), W - 60), 29, y + 3, Ui.TEXTO);
                Ui.mini(g, k.numero(), 29, y + 13, Ui.TEXTO2);
                Ui.circulo(g, W - 12, y + 11, 5, sel ? Ui.ACENTO : 0xFF3A434D);
                if (sel) Ui.glifo(g, Ui.G_OK, W - 16, y + 7, 0xFF0F1419);
                else Ui.circulo(g, W - 12, y + 11, 4, Ui.FONDO);
                y += F;
            }
            scrollMax = MovilCliente.CONTACTOS.size() * F - (fin - y0);
            barraScroll(g, y0, fin);
            soltarRecorte(g);
            if (MovilCliente.CONTACTOS.isEmpty()) Ui.centrado(g, t("sin_contactos"), W / 2, H / 2, Ui.TEXTO2);
            if (!elegidos.isEmpty()) {
                boolean s = Ui.dentro(mx, my, 10, fin + 4, W - 20, 16);
                Ui.redondo(g, 10, fin + 4, W - 20, 16, 8, s ? 0xFF2FBF73 : Ui.ACENTO);
                Ui.centrado(g, elegidos.size() >= 2 ? t("crear_grupo", elegidos.size() + 1) : t("abrir_chat"), W / 2, fin + 8, 0xFF0F1419);
            }
            cabecera(g, t("nuevo_chat"), mx, my);
        }

        @Override
        boolean caracter(char c) {
            return elegidos.size() >= 2 && nombre.caracter(c);
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            return elegidos.size() >= 2 && nombre.tecla(key);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int y0 = y0(), fin = H - ABAJO - (elegidos.isEmpty() ? 0 : 24);
            if (!elegidos.isEmpty() && Ui.dentro(x, y, 10, fin + 4, W - 20, 16)) {
                MovilCliente.clic();
                List<UUID> l = new ArrayList<>(elegidos);
                String n = nombre.s.toString();
                MovilRed.alServidor(MovilRed.C_CHAT_NUEVO, b -> {
                    b.writeUtf(l.size() >= 2 ? n : "");
                    b.writeVarInt(l.size());
                    for (UUID u : l) b.writeUUID(u);
                });
                return true;
            }
            if (y < y0 || y >= fin) return false;
            int i = (int) ((y - y0 + scroll) / F);
            if (i < 0 || i >= MovilCliente.CONTACTOS.size()) return false;
            UUID u = MovilCliente.CONTACTOS.get(i).id();
            if (!elegidos.remove(u)) elegidos.add(u);
            MovilCliente.clic();
            return true;
        }
    }

    // ================================================================== mandar una foto a un chat
    static final class ElegirChat extends Vista {
        private final int foto;

        ElegirChat(int foto) {
            this.foto = foto;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            listaChats(this, g, mx, my);
            cabecera(g, t("enviar_a"), mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            MovilCliente.ChatInfo c = chatEn(this, y);
            if (c == null) return false;
            MovilCliente.clic();
            int id = c.id;
            MovilRed.alServidor(MovilRed.C_MENSAJE, b -> {
                b.writeVarInt(id);
                b.writeUtf("");
                b.writeVarInt(foto);
            });
            m.cambiar(new Chat(id));
            return true;
        }
    }
}
