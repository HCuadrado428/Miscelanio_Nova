package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/** Cámara (con modo selfie), galería y visor de fotos. */
final class VistasFotos {
    private VistasFotos() {}

    // ================================================================== cámara
    /**
     * Cámara: en vertical (9:16) o con el móvil girado (16:9, el botón del móvil tumbado o la tecla R), con selfie,
     * cuadrícula de tercios y la última foto para ir a la galería. Espacio o Intro también hacen la foto.
     */
    static final class Camara extends Vista {
        private boolean selfie, horiz;
        private long tFlash, ultFoto;
        /** Para recordar cómo la dejaste la próxima vez que abras la cámara. */
        private static boolean horizAntes;

        Camara() {
            horiz = horizAntes;
        }

        @Override
        void abierta() {
            if (!MovilCliente.fotosCargadas) MovilRed.alServidor(MovilRed.C_FOTOS, null);
        }

        @Override
        boolean horizontal() {
            return horiz;
        }

        @Override
        boolean quiereFrontal() {
            return selfie;
        }

        @Override
        int fondoBarra() {
            return 0;
        }

        /** Tamaño de la pantalla tal y como se ve ahora. */
        private int w() {
            return horiz ? HW : W;
        }

        private int h() {
            return horiz ? HH : H;
        }

        // posiciones de los botones: en vertical abajo, en horizontal en la columna de la derecha
        private int[] disparador() {
            return horiz ? new int[]{HW - 24, HH / 2} : new int[]{W / 2, H - 30};
        }

        private int[] ultima() {
            return horiz ? new int[]{HW - 24, HH - 24} : new int[]{23, H - 30};
        }

        private int[] cambiar() {
            return horiz ? new int[]{HW - 24, 22} : new int[]{W - 23, H - 30};
        }

        private int[] girar() {
            return horiz ? new int[]{18, HH - 18} : new int[]{W - 16, BARRA + 8};
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            int w = w(), h = h();
            MovilCaptura.pedir(horiz);
            Ui.rect(g, 0, 0, w, h, 0xFF000000);
            int vw = horiz ? w - 48 : w;      // en horizontal el visor deja sitio a la columna de botones
            if (MovilCaptura.lista()) MovilCaptura.dibujarVisor(g, 0, 0, vw, h);
            else Ui.centrado(g, t("iniciando_camara"), vw / 2, h / 2 - 4, Ui.TEXTO2);
            // cuadrícula de tercios
            int gy0 = horiz ? 0 : BARRA + 16, gy1 = horiz ? h : h - 52;
            for (int i = 1; i < 3; i++) {
                Ui.rect(g, vw * i / 3, gy0, 1, gy1 - gy0, 0x22FFFFFF);
                Ui.rect(g, 0, gy0 + (gy1 - gy0) * i / 3, vw, 1, 0x22FFFFFF);
            }
            long f = Util.getMillis() - tFlash;
            if (f < 180) Ui.rect(g, 0, 0, w, h, ((int) (255 * (1 - f / 180F)) << 24) | 0xFFFFFF);
            String n = MovilCliente.FOTOS.size() + "/" + MovilCliente.maxFotos;
            if (horiz) {
                Ui.rect(g, w - 48, 0, 48, h, 0xB0000000);
                Ui.rect(g, 0, 0, vw, 16, 0x70000000);
                Ui.glifo(g, Ui.G_ATRAS, 5, 4, Ui.dentro(mx, my, 0, 0, 18, 16) ? 0xFFFFFFFF : 0xFFD0D0D0);
                Ui.centrado(g, t(selfie ? "selfie" : "foto"), vw / 2, 4, Ui.AMARILLO);
                Ui.mini(g, n, vw - 6 - Ui.anchoMini(n), 6, 0xFFFFFFFF);
            } else {
                Ui.rect(g, 0, 0, w, BARRA + 16, 0x90000000);
                Ui.centrado(g, t(selfie ? "selfie" : "foto"), w / 2, BARRA + 4, Ui.AMARILLO);
                Ui.mini(g, n, 22, BARRA + 6, 0xFFFFFFFF);
                Ui.glifo(g, Ui.G_ATRAS, 5, BARRA + 4, Ui.dentro(mx, my, 0, BARRA, 18, 16) ? 0xFFFFFFFF : 0xFFD0D0D0);
                Ui.rect(g, 0, h - 52, w, 52, 0xA0000000);
            }
            // disparador (gris mientras se guarda la foto anterior)
            int[] d = disparador();
            boolean listo = Util.getMillis() - ultFoto > ESPERA;
            boolean s = Ui.enCirculo(mx, my, d[0], d[1], 13);
            Ui.circulo(g, d[0], d[1], 13, 0xFFFFFFFF);
            Ui.circulo(g, d[0], d[1], 11, 0xFF000000);
            Ui.circulo(g, d[0], d[1], s && listo ? 9 : 10, listo ? 0xFFFFFFFF : 0xFF8A9199);
            // última foto
            int[] u = ultima();
            Ui.redondo(g, u[0] - 11, u[1] - 11, 22, 22, 5, 0xFF2A2F35);
            if (!MovilCliente.FOTOS.isEmpty()) {
                FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_PROPIA, MovilCliente.FOTOS.get(0).id());
                if (t != null) FotoCache.cubrir(g, t, u[0] - 10, u[1] - 10, 20, 20);
            }
            // selfie
            int[] c = cambiar();
            boolean sg = Ui.enCirculo(mx, my, c[0], c[1], 10);
            Ui.boton(g, c[0], c[1], 10, sg ? 0xFF4A535C : 0xFF30373E, Ui.G_GIRAR, 0xFFFFFFFF);
            // girar el móvil: un movilito tumbado o de pie
            int[] gr = girar();
            boolean so = Ui.enCirculo(mx, my, gr[0], gr[1], 8);
            Ui.circulo(g, gr[0], gr[1], 8, so ? 0xC04A535C : 0x9030373E);
            int pw = horiz ? 5 : 9, ph = horiz ? 9 : 5;     // muestra la otra forma (a la que pasarías)
            Ui.rect(g, gr[0] - pw / 2, gr[1] - ph / 2, pw, ph, 0xFFFFFFFF);
            Ui.rect(g, gr[0] - pw / 2 + 1, gr[1] - ph / 2 + 1, pw - 2, ph - 2, 0xFF30373E);
        }

        private static final long ESPERA = 700;

        private void disparar() {
            if (Util.getMillis() - ultFoto <= ESPERA) return;
            byte[] jpg = MovilCaptura.foto(MovilCliente.maxBytesFoto);
            if (jpg == null) {
                MovilCliente.aviso(Component.translatable("movil.miscelanea.aviso.foto_error"));
                return;
            }
            if (MovilCliente.FOTOS.size() >= MovilCliente.maxFotos) {
                MovilCliente.aviso(Component.translatable("movil.miscelanea.aviso.galeria_llena", MovilCliente.maxFotos));
                return;
            }
            tFlash = ultFoto = Util.getMillis();
            MovilCliente.sonido(MovilRegistro.OBTURADOR.get(), 0.9F, 1.0F);
            MovilRed.alServidor(MovilRed.C_FOTO_SUBIR, b -> b.writeByteArray(jpg));
        }

        private void alternarGiro() {
            horiz = !horiz;
            horizAntes = horiz;
            MovilCliente.clic();
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                disparar();
                return true;
            }
            if (key == GLFW.GLFW_KEY_R) {
                alternarGiro();
                return true;
            }
            return false;
        }

        @Override
        boolean clic(double x, double y) {
            if (horiz ? Ui.dentro(x, y, 0, 0, 18, 16) : Ui.dentro(x, y, 0, BARRA, 18, 16)) {
                MovilCliente.clic();
                m.atras();
                return true;
            }
            int[] d = disparador(), c = cambiar(), u = ultima(), gr = girar();
            if (Ui.enCirculo(x, y, d[0], d[1], 14)) {
                disparar();
                return true;
            }
            if (Ui.enCirculo(x, y, c[0], c[1], 11)) {
                selfie = !selfie;
                MovilCliente.clic();
                return true;
            }
            if (Ui.enCirculo(x, y, gr[0], gr[1], 9)) {
                alternarGiro();
                return true;
            }
            if (Ui.dentro(x, y, u[0] - 13, u[1] - 13, 26, 26)) {
                MovilCliente.clic();
                m.abrir(new Galeria(null));
                return true;
            }
            return false;
        }
    }

    // ================================================================== galería
    static final class Galeria extends Vista {
        /** Miniaturas cuadradas (salen bien las fotos verticales y las apaisadas). */
        private static final int TW = 42, TH = 42, PASO_X = 44, PASO_Y = 44;
        private final IntConsumer elegir;

        Galeria(IntConsumer elegir) {
            this.elegir = elegir;
        }

        @Override
        void abierta() {
            MovilRed.alServidor(MovilRed.C_FOTOS, null);
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            fondo(g);
            int y0 = VistasChat.Y0;
            recortar(g, y0, H - ABAJO);
            var l = MovilCliente.FOTOS;
            for (int i = 0; i < l.size(); i++) {
                int x = 1 + (i % 3) * PASO_X, y = y0 + 2 + (i / 3) * PASO_Y - (int) scroll;
                if (y + TH < y0 || y > H) continue;
                FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_PROPIA, l.get(i).id());
                if (t != null) {
                    FotoCache.cubrir(g, t, x, y, TW, TH);
                    if (t.w > t.h) {
                        // apaisada: marquita en la esquina
                        Ui.rect(g, x + TW - 9, y + TH - 7, 7, 5, 0xC0000000);
                        Ui.rect(g, x + TW - 8, y + TH - 6, 5, 3, 0xFFFFFFFF);
                    }
                } else Ui.rect(g, x, y, TW, TH, 0xFF1C232A);
                if (Ui.dentro(mx, my, x, y, TW, TH) && my >= y0) Ui.rect(g, x, y, TW, TH, 0x30FFFFFF);
            }
            scrollMax = ((l.size() + 2) / 3) * PASO_Y + 4 - (H - ABAJO - y0);
            barraScroll(g, y0, H - ABAJO);
            soltarRecorte(g);
            if (l.isEmpty()) {
                Ui.centrado(g, t(MovilCliente.fotosCargadas ? "sin_fotos" : "cargando"), W / 2, H / 2 - 10, Ui.TEXTO2);
                if (MovilCliente.fotosCargadas) Ui.centrado(g, t("sin_fotos2"), W / 2, H / 2 + 2, 0xFF59636D);
            }
            cabecera(g, t(elegir != null ? "elige_foto" : "app_fotos"), mx, my);
            String n = l.size() + "/" + MovilCliente.maxFotos;
            Ui.mini(g, n, W - 6 - Ui.anchoMini(n), BARRA + 8, Ui.TEXTO2);
        }

        @Override
        boolean clic(double x, double y) {
            if (clicAtras(x, y)) return true;
            int y0 = VistasChat.Y0;
            if (y < y0) return false;
            var l = MovilCliente.FOTOS;
            for (int i = 0; i < l.size(); i++) {
                int fx = 1 + (i % 3) * PASO_X, fy = y0 + 2 + (i / 3) * PASO_Y - (int) scroll;
                if (Ui.dentro(x, y, fx, fy, TW, TH)) {
                    MovilCliente.clic();
                    int id = l.get(i).id();
                    if (elegir != null) {
                        m.atras();
                        elegir.accept(id);
                    } else m.abrir(new Foto(MovilRed.FOTO_PROPIA, id));
                    return true;
                }
            }
            return false;
        }
    }

    // ================================================================== ver una foto
    /** Una foto a pantalla completa; si es apaisada, el móvil se gira solo para verla grande. */
    static final class Foto extends Vista {
        private final int tipo, id;
        private long confirmar;

        Foto(int tipo, int id) {
            this.tipo = tipo;
            this.id = id;
        }

        @Override
        boolean horizontal() {
            FotoCache.Tex t = FotoCache.get(tipo, id);
            return t != null && t.w > t.h;
        }

        @Override
        int fondoBarra() {
            return 0xFF000000;
        }

        private boolean propia() {
            return tipo == MovilRed.FOTO_PROPIA;
        }

        // botones: en vertical abajo; en horizontal, arriba a la derecha
        private int[] enviarB() {
            return horizontal() ? new int[]{HW - 116, 4} : new int[]{10, H - ABAJO - 22};
        }

        private int[] borrarB() {
            return horizontal() ? new int[]{HW - 58, 4} : new int[]{W - 62, H - ABAJO - 22};
        }

        private int[] atrasB() {
            return horizontal() ? new int[]{0, 0} : new int[]{0, BARRA};
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            boolean hz = horizontal();
            int w = hz ? HW : W, h = hz ? HH : H;
            Ui.rect(g, 0, 0, w, h, 0xFF000000);
            int top = hz ? 0 : BARRA + 18, bot = hz ? h : H - ABAJO - (propia() ? 26 : 2);
            FotoCache.Tex t = FotoCache.get(tipo, id);
            if (t != null) {
                float k = Math.min((float) w / t.w, (float) (bot - top) / t.h);
                int fw = Math.round(t.w * k), fh = Math.round(t.h * k);
                g.blit(t.rl, (w - fw) / 2, top + (bot - top - fh) / 2, fw, fh, 0, 0, t.w, t.h, t.w, t.h);
            } else Ui.centrado(g, t(FotoCache.falta(tipo, id) ? "foto_no_existe" : "cargando"), w / 2, h / 2 - 4, Ui.TEXTO2);
            int[] at = atrasB();
            if (hz) Ui.circulo(g, 10, 10, 8, 0x90000000);
            Ui.glifo(g, Ui.G_ATRAS, at[0] + 5, at[1] + 5, Ui.dentro(mx, my, at[0], at[1], 18, 18) ? 0xFFFFFFFF : 0xFFCCCCCC);
            if (propia()) {
                int[] e = enviarB(), b = borrarB();
                boolean se = Ui.dentro(mx, my, e[0], e[1], 52, 16), sb = Ui.dentro(mx, my, b[0], b[1], 52, 16);
                Ui.redondo(g, e[0], e[1], 52, 16, 8, se ? 0xFF33404C : 0xE0222A31);
                Ui.glifo(g, Ui.G_ENVIAR, e[0] + 6, e[1] + 4, Ui.ACENTO);
                Ui.texto(g, t("enviar"), e[0] + 18, e[1] + 4, Ui.TEXTO);
                boolean conf = Util.getMillis() - confirmar < 3000;
                Ui.redondo(g, b[0], b[1], 52, 16, 8, conf ? Ui.ROJO : sb ? 0xFF33404C : 0xE0222A31);
                Ui.glifo(g, Ui.G_BORRAR, b[0] + 6, b[1] + 4, conf ? 0xFFFFFFFF : Ui.ROJO);
                Ui.texto(g, t(conf ? "seguro" : "borrar"), b[0] + 18, b[1] + 4, Ui.TEXTO);
            }
        }

        @Override
        boolean clic(double x, double y) {
            int[] at = atrasB();
            if (Ui.dentro(x, y, at[0], at[1], 18, 18)) {
                MovilCliente.clic();
                m.atras();
                return true;
            }
            if (!propia()) return false;
            int[] e = enviarB(), b = borrarB();
            if (Ui.dentro(x, y, e[0], e[1], 52, 16)) {
                MovilCliente.clic();
                m.abrir(new VistasChat.ElegirChat(id));
                return true;
            }
            if (Ui.dentro(x, y, b[0], b[1], 52, 16)) {
                MovilCliente.clic();
                if (Util.getMillis() - confirmar < 3000) {
                    MovilRed.alServidor(MovilRed.C_FOTO_BORRAR, b2 -> b2.writeVarInt(id));
                    MovilCliente.FOTOS.removeIf(f -> f.id() == id);
                    FotoCache.olvidar(tipo, id);
                    m.atras();
                } else confirmar = Util.getMillis();
                return true;
            }
            return false;
        }
    }
}
