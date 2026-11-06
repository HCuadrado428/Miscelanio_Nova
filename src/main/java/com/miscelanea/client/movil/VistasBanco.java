package com.miscelanea.client.movil;

import com.miscelanea.movil.BancoDatos;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Banco y Bizum: saldo de esmeraldas, ingresar/retirar (de/al inventario), Bizum a otro jugador con concepto, mandar un
 * objeto del inventario y el historial. Todo lo que mueve dinero pide confirmación (segundo toque: "¿Seguro?").
 */
final class VistasBanco {
    private VistasBanco() {}

    static final int Y0 = VistasChat.Y0;
    static final int VERDE = 0xFF17C964, VERDE_OSC = 0xFF0C7A3C, TARJETA_A = 0xFF0F6B45, TARJETA_B = 0xFF0A3D2E;

    static void pedir() {
        MovilRed.alServidor(MovilRed.C_BANCO, null);
    }

    static void esmeralda(GuiGraphics g, int x, int y) {
        Ui.glifo(g, Ui.G_ESMERALDA, x, y, 0xFF2EE87A);
    }

    // ================================================================== inicio del banco
    static final class Banco extends Vista {
        private static final int[] GL = {Ui.G_MAS, Ui.G_MENOS, Ui.G_TRANSFER, Ui.G_COFRE};
        private static final String[] TX = {"banco_ingresar", "banco_retirar", "banco_bizum", "banco_objeto"};

        @Override
        void abierta() {
            pedir();
            MovilRed.alServidor(MovilRed.C_SYNC, null);
        }

        private static int bx(int i) {
            return 18 + i * 32;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            // tarjeta con el saldo
            int ty = Y0 + 6;
            for (int i = 0; i < 52; i++) Ui.rect(g, 8, ty + i, W - 16, 1, VistasMinegram.mezcla(TARJETA_A, TARJETA_B, i / 51F));
            esquinas(g, 8, ty, W - 16, 52);
            Ui.texto(g, t("banco_saldo"), 15, ty + 6, 0xFFB5F0CF);
            esmeralda(g, 15, ty + 21);
            String s = BancoCliente.cargado ? Ui.miles(BancoCliente.saldo) : "…";
            Ui.grande(g, s, 26 + Ui.ancho(s), ty + 19, 2, 0xFFFFFFFF);
            Ui.texto(g, t("banco_en_inventario", BancoCliente.enInventario), 15, ty + 39, 0xFF9DD9B8);
            // chip de la tarjeta
            Ui.redondo(g, W - 30, ty + 6, 12, 9, 2, 0xFFE8C66A);
            Ui.rect(g, W - 30, ty + 10, 12, 1, 0xFFB8964A);
            Ui.rect(g, W - 24, ty + 6, 1, 9, 0xFFB8964A);
            // botones
            int cy = ty + 70;
            for (int i = 0; i < 4; i++) {
                if (i == 3 && !BancoCliente.objetos) continue;
                boolean so = Ui.enCirculo(mx, my, bx(i), cy, 11);
                Ui.boton(g, bx(i), cy, 11, so ? 0xFF2A3A33 : 0xFF1C2A24, GL[i], VERDE);
                g.pose().pushPose();
                String e = t(TX[i]);
                g.pose().translate(bx(i) - Ui.ancho(e) / 4F, cy + 14, 0);
                g.pose().scale(0.5F, 0.5F, 1);
                g.drawString(Ui.font(), e, 0, 0, Ui.TEXTO2, false);
                g.pose().popPose();
            }
            // historial
            int hy = cy + 24;
            Ui.texto(g, t("banco_movimientos"), 8, hy, Ui.TEXTO);
            Ui.rect(g, 0, hy + 11, W, 1, Ui.LINEA);
            int y0 = hy + 12, fin = H - ABAJO;
            recortar(g, y0, fin);
            int y = y0 - (int) scroll;
            List<BancoCliente.Mov> l = BancoCliente.HISTORIAL;
            for (BancoCliente.Mov mv : l) {
                if (y + 22 > y0 && y < fin) fila(g, mv, y);
                y += 22;
            }
            scrollMax = l.size() * 22 - (fin - y0);
            barraScroll(g, y0, fin);
            soltarRecorte(g);
            if (l.isEmpty() && BancoCliente.cargado) Ui.centrado(g, t("banco_sin_movimientos"), W / 2, y0 + 12, 0xFF59636D);
            cabecera(g, t("app_banco"), mx, my);
        }

        private void fila(GuiGraphics g, BancoCliente.Mov mv, int y) {
            boolean entra = mv.cantidad() > 0 || mv.tipo() == BancoDatos.M_OBJETO_RECIBIDO;
            if (mv.otro() != null) Ui.cara(g, mv.otro(), 6, y + 4, 14, Ui.FONDO);
            else {
                Ui.circulo(g, 13, y + 11, 7, 0xFF1C2A24);
                Ui.glifo(g, mv.tipo() == BancoDatos.M_ADMIN ? Ui.G_OK : mv.tipo() == BancoDatos.M_INGRESO ? Ui.G_MAS : Ui.G_MENOS, 9, y + 7, VERDE);
            }
            String tit = switch (mv.tipo()) {
                case BancoDatos.M_INGRESO -> t("banco_m_ingreso");
                case BancoDatos.M_RETIRADA -> t("banco_m_retirada");
                case BancoDatos.M_ENVIADO -> t("banco_m_enviado", mv.nombre());
                case BancoDatos.M_RECIBIDO -> t("banco_m_recibido", mv.nombre());
                case BancoDatos.M_ADMIN -> t("banco_m_admin", mv.nombre());
                case BancoDatos.M_OBJETO_ENVIADO -> t("banco_m_obj_enviado", mv.nombre());
                default -> t("banco_m_obj_recibido", mv.nombre());
            };
            String cant = mv.cantidad() == 0 ? "" : (mv.cantidad() > 0 ? "+" : "") + Ui.miles(mv.cantidad());
            int cw = Ui.ancho(cant);
            Ui.texto(g, Ui.cortar(tit, W - 34 - cw - 4), 24, y + 3, Ui.TEXTO);
            if (!cant.isEmpty()) Ui.texto(g, cant, W - 6 - cw, y + 3, entra ? VERDE : Ui.TEXTO);
            String sub = cuando(mv.t()) + (mv.texto().isEmpty() ? "" : " · " + mv.texto());
            g.pose().pushPose();
            g.pose().translate(24, y + 13, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.drawString(Ui.font(), Ui.cortar(sub, (W - 30) * 2), 0, 0, Ui.TEXTO2, false);
            g.pose().popPose();
            Ui.rect(g, 24, y + 21, W - 24, 1, Ui.LINEA);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int cy = Y0 + 6 + 70;
            for (int i = 0; i < 4; i++) {
                if (!Ui.enCirculo(x, y, bx(i), cy, 12)) continue;
                if (i == 3 && !BancoCliente.objetos) return false;
                MovilCliente.clic();
                switch (i) {
                    case 0 -> m.abrir(new Cantidad(Cantidad.INGRESAR, null, null));
                    case 1 -> m.abrir(new Cantidad(Cantidad.RETIRAR, null, null));
                    case 2 -> m.abrir(new ElegirContacto(t("banco_bizum_a"), false, k -> m.cambiar(new Cantidad(Cantidad.BIZUM, k.id(), k.nombre()))));
                    default -> m.abrir(new ElegirContacto(t("banco_objeto_a"), true, k -> m.cambiar(new Objeto(k.id(), k.nombre()))));
                }
                return true;
            }
            return false;
        }
    }

    /** Esquinas redondeadas pintando el fondo por fuera (la tarjeta es un degradado de filas). */
    static void esquinas(GuiGraphics g, int x, int y, int w, int h) {
        int r = 6;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int in = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (in <= 0) continue;
            Ui.rect(g, x, y + i, in, 1, Ui.FONDO);
            Ui.rect(g, x + w - in, y + i, in, 1, Ui.FONDO);
            Ui.rect(g, x, y + h - i - 1, in, 1, Ui.FONDO);
            Ui.rect(g, x + w - in, y + h - i - 1, in, 1, Ui.FONDO);
        }
    }

    // ================================================================== elegir a quién
    static final class ElegirContacto extends Vista {
        private final String titulo;
        private final boolean soloConectados;
        private final Consumer<MovilCliente.Contacto> elegir;
        private static final int F = 22;

        ElegirContacto(String titulo, boolean soloConectados, Consumer<MovilCliente.Contacto> elegir) {
            this.titulo = titulo;
            this.soloConectados = soloConectados;
            this.elegir = elegir;
        }

        private List<MovilCliente.Contacto> lista() {
            return soloConectados ? MovilCliente.CONTACTOS.stream().filter(MovilCliente.Contacto::conectado).toList() : MovilCliente.CONTACTOS;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            List<MovilCliente.Contacto> l = lista();
            recortar(g, Y0, H - ABAJO);
            int y = Y0 - (int) scroll;
            for (MovilCliente.Contacto k : l) {
                if (Ui.dentro(mx, my, 0, y, W, F) && my >= Y0) Ui.rect(g, 0, y, W, F, 0x10FFFFFF);
                Ui.cara(g, k.id(), 7, y + 3, 16, Ui.FONDO);
                if (k.conectado()) Ui.circulo(g, 21, y + 17, 2, Ui.ACENTO);
                Ui.texto(g, Ui.cortar(k.nombre(), W - 50), 29, y + 3, Ui.TEXTO);
                Ui.mini(g, k.numero(), 29, y + 13, Ui.TEXTO2);
                Ui.glifo(g, Ui.G_DERECHA, W - 12, y + 7, Ui.TEXTO2);
                y += F;
            }
            scrollMax = l.size() * F - (H - ABAJO - Y0);
            barraScroll(g, Y0, H - ABAJO);
            soltarRecorte(g);
            if (l.isEmpty()) Ui.centrado(g, t(soloConectados ? "banco_nadie_conectado" : "sin_contactos"), W / 2, H / 2, Ui.TEXTO2);
            cabecera(g, titulo, mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (y < Y0) return false;
            List<MovilCliente.Contacto> l = lista();
            int i = (int) ((y - Y0 + scroll) / F);
            if (i < 0 || i >= l.size()) return false;
            MovilCliente.clic();
            elegir.accept(l.get(i));
            return true;
        }
    }

    // ================================================================== cantidad (ingresar, retirar, Bizum)
    static final class Cantidad extends Vista {
        static final int INGRESAR = 0, RETIRAR = 1, BIZUM = 2;
        private final int modo;
        private final UUID a;
        private final String nombre;
        private final StringBuilder num = new StringBuilder();
        private final VistasChat.Campo concepto = new VistasChat.Campo(BancoCliente.maxConcepto);
        private long confirmar;
        private boolean focoConcepto;
        private static final String[] TECLAS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "<"};

        Cantidad(int modo, UUID a, String nombre) {
            this.modo = modo;
            this.a = a;
            this.nombre = nombre;
        }

        @Override
        void abierta() {
            pedir();
        }

        @Override
        boolean escribiendo() {
            return modo == BIZUM;
        }

        private long valor() {
            try {
                return num.length() == 0 ? 0 : Long.parseLong(num.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private long maximo() {
            return switch (modo) {
                case INGRESAR -> BancoCliente.enInventario;
                case RETIRAR -> Math.min(BancoCliente.saldo, 64 * 36);
                default -> Math.min(BancoCliente.saldo, BancoCliente.maxTransferencia);
            };
        }

        private static int kx(int i) {
            return 26 + (i % 3) * 40;
        }

        private static int ky(int i) {
            return Y0 + 70 + (i / 3) * 22;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            long v = valor(), max = maximo();
            boolean ok = v > 0 && v <= max;
            int y = Y0 + 4;
            if (modo == BIZUM) {
                Ui.cara(g, a, 8, y, 12, Ui.FONDO);
                Ui.texto(g, Ui.cortar(t("banco_para", nombre), W - 30), 24, y + 2, Ui.TEXTO);
            } else Ui.centrado(g, t(modo == INGRESAR ? "banco_ingresar_desc" : "banco_retirar_desc"), W / 2, y + 2, Ui.TEXTO2);
            String s = num.length() == 0 ? "0" : Ui.miles(v);
            int sw = Ui.ancho(s) * 2 + 12;
            esmeralda(g, W / 2 - sw / 2, y + 23);
            Ui.grande(g, s, W / 2 + 6, y + 20, 2, v > max ? Ui.ROJO : num.length() == 0 ? 0xFF59636D : 0xFFFFFFFF);
            String lim = t("banco_max", Ui.miles(max));
            g.pose().pushPose();
            g.pose().translate(W / 2F - Ui.ancho(lim) / 4F, y + 38, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.drawString(Ui.font(), lim, 0, 0, v > max ? Ui.ROJO : Ui.TEXTO2, false);
            g.pose().popPose();
            if (modo == BIZUM) {
                Ui.redondo(g, 10, y + 46, W - 20, 14, 7, focoConcepto ? 0xFF34404C : 0xFF2A333D);
                concepto.dibujar(g, 16, y + 49, W - 32, t("banco_concepto"), focoConcepto);
            }
            for (int i = 0; i < TECLAS.length; i++) {
                int cx = kx(i), cy = ky(i);
                boolean so = Ui.enCirculo(mx, my, cx, cy, 9);
                Ui.circulo(g, cx, cy, 9, so ? 0xFF2E3943 : 0xFF222A32);
                if (TECLAS[i].equals("<")) Ui.glifo(g, Ui.G_BORRAR_TECLA, cx - 4, cy - 4, Ui.TEXTO);
                else if (TECLAS[i].equals("*")) {
                    g.pose().pushPose();
                    g.pose().translate(cx - Ui.ancho(t("banco_todo")) / 4F, cy - 2, 0);
                    g.pose().scale(0.5F, 0.5F, 1);
                    g.drawString(Ui.font(), t("banco_todo"), 0, 0, VERDE, false);
                    g.pose().popPose();
                } else Ui.centrado(g, TECLAS[i], cx + 1, cy - 4, Ui.TEXTO);
            }
            int by = H - ABAJO - 20;
            boolean conf = Util.getMillis() - confirmar < 3000;
            boolean sb = Ui.dentro(mx, my, 10, by, W - 20, 16);
            String bt = !ok ? t(modo == BIZUM ? "banco_enviar" : modo == INGRESAR ? "banco_ingresar" : "banco_retirar")
                    : conf ? t("banco_seguro", Ui.miles(v)) : t(modo == BIZUM ? "banco_enviar_n" : modo == INGRESAR ? "banco_ingresar_n" : "banco_retirar_n", Ui.miles(v));
            Ui.pastilla(g, 10, by, W - 20, 16, bt, !ok ? 0xFF26313B : conf ? 0xFFE0A02A : sb ? 0xFF14B058 : VERDE, ok ? 0xFF06140C : 0xFF59636D);
            cabecera(g, t(modo == BIZUM ? "banco_bizum" : modo == INGRESAR ? "banco_ingresar" : "banco_retirar"), mx, my);
        }

        private void teclear(String k) {
            if (k.equals("<")) {
                if (num.length() > 0) num.setLength(num.length() - 1);
            } else if (k.equals("*")) {
                num.setLength(0);
                long mx = maximo();
                if (mx > 0) num.append(mx);
            } else if (num.length() < 9 && !(num.length() == 0 && k.equals("0"))) num.append(k);
            confirmar = 0;
        }

        private void confirmarPulsado() {
            long v = valor();
            if (v <= 0 || v > maximo()) return;
            if (Util.getMillis() - confirmar >= 3000) {
                confirmar = Util.getMillis();
                MovilCliente.clic();
                return;
            }
            confirmar = 0;
            switch (modo) {
                case INGRESAR -> MovilRed.alServidor(MovilRed.C_BANCO_INGRESAR, b -> b.writeVarInt((int) v));
                case RETIRAR -> MovilRed.alServidor(MovilRed.C_BANCO_RETIRAR, b -> b.writeVarInt((int) v));
                default -> {
                    String c = concepto.s.toString().trim();
                    MovilRed.alServidor(MovilRed.C_BIZUM, b -> {
                        b.writeUUID(a);
                        b.writeVarLong(v);
                        b.writeUtf(c);
                    });
                }
            }
            MovilCliente.sonido(MovilRegistro.MENSAJE.get(), 0.6F, 1.3F);
            m.atras();
        }

        @Override
        boolean caracter(char c) {
            if (c >= '0' && c <= '9' && !focoConcepto) {
                teclear(String.valueOf(c));
                return true;
            }
            if (modo != BIZUM) return false;
            focoConcepto = true;
            return concepto.caracter(c);
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmarPulsado();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (modo == BIZUM && focoConcepto) return concepto.tecla(key);
                teclear("<");
                return true;
            }
            return modo == BIZUM && concepto.tecla(key);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            if (modo == BIZUM && Ui.dentro(x, y, 10, Y0 + 50, W - 20, 14)) {
                focoConcepto = true;
                return true;
            }
            for (int i = 0; i < TECLAS.length; i++)
                if (Ui.enCirculo(x, y, kx(i), ky(i), 10)) {
                    MovilCliente.clic();
                    focoConcepto = false;
                    teclear(TECLAS[i]);
                    return true;
                }
            if (Ui.dentro(x, y, 10, H - ABAJO - 20, W - 20, 16)) {
                confirmarPulsado();
                return true;
            }
            return false;
        }
    }

    // ================================================================== mandar un objeto
    static final class Objeto extends Vista {
        private final UUID a;
        private final String nombre;
        private int slot = -1;
        private long confirmar;
        private static final int CELDA = 14;

        Objeto(UUID a, String nombre) {
            this.a = a;
            this.nombre = nombre;
        }

        private static int sx(int i) {
            return 3 + (i % 9) * CELDA;
        }

        /** Fila 0 = barra rápida (slots 0-8) abajo, como en el inventario. */
        private static int sy(int i) {
            int fila = i < 9 ? 3 : i / 9 - 1;
            return Y0 + 34 + fila * CELDA + (i < 9 ? 4 : 0);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            var p = Minecraft.getInstance().player;
            Ui.cara(g, a, 8, Y0 + 4, 12, Ui.FONDO);
            Ui.texto(g, Ui.cortar(t("banco_para", nombre), W - 30), 24, Y0 + 6, Ui.TEXTO);
            Ui.texto(g, t("banco_elige_objeto"), 8, Y0 + 21, Ui.TEXTO2);
            if (p != null) {
                Inventory inv = p.getInventory();
                for (int i = 0; i < 36; i++) {
                    int x = sx(i), y = sy(i);
                    ItemStack s = inv.items.get(i);
                    boolean sel = i == slot, so = Ui.dentro(mx, my, x, y, CELDA - 1, CELDA - 1);
                    Ui.rect(g, x, y, CELDA - 1, CELDA - 1, sel ? 0xFF1E6A50 : so ? 0xFF2E3943 : 0xFF1C242C);
                    if (!s.isEmpty()) {
                        g.pose().pushPose();
                        g.pose().translate(x + 0.5F, y + 0.5F, 0);
                        g.pose().scale(0.75F, 0.75F, 1);
                        g.renderItem(s, 0, 0);
                        g.renderItemDecorations(Ui.font(), s, 0, 0);
                        g.pose().popPose();
                        if (MovilRegistro.esMovil(s)) Ui.rect(g, x, y, CELDA - 1, CELDA - 1, 0x90101418);
                    }
                }
                int iy = sy(0) + CELDA + 6;
                ItemStack s = slot >= 0 ? inv.items.get(slot) : ItemStack.EMPTY;
                if (!s.isEmpty()) {
                    Ui.texto(g, Ui.cortar(s.getCount() + "x " + s.getHoverName().getString(), W - 16), 8, iy, Ui.TEXTO);
                } else Ui.texto(g, t("banco_ningun_objeto"), 8, iy, 0xFF59636D);
                boolean ok = !s.isEmpty() && !MovilRegistro.esMovil(s);
                int by = H - ABAJO - 20;
                boolean conf = Util.getMillis() - confirmar < 3000;
                boolean sb = Ui.dentro(mx, my, 10, by, W - 20, 16);
                Ui.pastilla(g, 10, by, W - 20, 16, t(conf && ok ? "seguro" : "banco_enviar"), !ok ? 0xFF26313B : conf ? 0xFFE0A02A : sb ? 0xFF14B058 : VERDE,
                        ok ? 0xFF06140C : 0xFF59636D);
            }
            cabecera(g, t("banco_objeto"), mx, my);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            var p = Minecraft.getInstance().player;
            if (p == null) return false;
            for (int i = 0; i < 36; i++)
                if (Ui.dentro(x, y, sx(i), sy(i), CELDA - 1, CELDA - 1)) {
                    MovilCliente.clic();
                    ItemStack s = p.getInventory().items.get(i);
                    slot = s.isEmpty() || MovilRegistro.esMovil(s) ? -1 : i;
                    confirmar = 0;
                    return true;
                }
            if (Ui.dentro(x, y, 10, H - ABAJO - 20, W - 20, 16) && slot >= 0) {
                ItemStack s = p.getInventory().items.get(slot);
                if (s.isEmpty() || MovilRegistro.esMovil(s)) return true;
                if (Util.getMillis() - confirmar >= 3000) {
                    confirmar = Util.getMillis();
                    MovilCliente.clic();
                    return true;
                }
                int sl = slot, n = s.getCount();
                MovilRed.alServidor(MovilRed.C_ENVIAR_OBJETO, b -> {
                    b.writeUUID(a);
                    b.writeVarInt(sl);
                    b.writeVarInt(n);
                });
                MovilCliente.sonido(MovilRegistro.MENSAJE.get(), 0.6F, 1.3F);
                m.atras();
                return true;
            }
            return false;
        }
    }
}
