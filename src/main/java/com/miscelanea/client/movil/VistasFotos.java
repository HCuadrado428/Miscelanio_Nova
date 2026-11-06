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
    static final class Camara extends Vista {
        private boolean selfie;
        private long tFlash;

        @Override
        void abierta() {
            if (!MovilCliente.fotosCargadas) MovilRed.alServidor(MovilRed.C_FOTOS, null);
        }

        @Override
        boolean quiereFrontal() {
            return selfie;
        }

        @Override
        int fondoBarra() {
            return 0;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            MovilCaptura.pedir();
            Ui.rect(g, 0, 0, W, H, 0xFF000000);
            if (MovilCaptura.lista()) MovilCaptura.dibujarVisor(g, 0, 0, W, H);
            else Ui.centrado(g, t("iniciando_camara"), W / 2, H / 2 - 4, Ui.TEXTO2);
            // cuadrícula de tercios
            for (int i = 1; i < 3; i++) {
                Ui.rect(g, W * i / 3, BARRA + 16, 1, H - BARRA - 16 - 52, 0x22FFFFFF);
                Ui.rect(g, 0, BARRA + 16 + (H - BARRA - 68) * i / 3, W, 1, 0x22FFFFFF);
            }
            long f = Util.getMillis() - tFlash;
            if (f < 180) Ui.rect(g, 0, 0, W, H, ((int) (255 * (1 - f / 180F)) << 24) | 0xFFFFFF);
            Ui.rect(g, 0, 0, W, BARRA + 16, 0x90000000);
            Ui.centrado(g, t(selfie ? "selfie" : "foto"), W / 2, BARRA + 4, Ui.AMARILLO);
            String n = MovilCliente.FOTOS.size() + "/" + MovilCliente.maxFotos;
            Ui.mini(g, n, W - 6 - Ui.anchoMini(n), BARRA + 6, 0xFFFFFFFF);
            Ui.glifo(g, Ui.G_ATRAS, 5, BARRA + 4, Ui.dentro(mx, my, 0, BARRA, 18, 16) ? 0xFFFFFFFF : 0xFFD0D0D0);
            // controles
            Ui.rect(g, 0, H - 52, W, 52, 0xA0000000);
            int cx = W / 2, cy = H - 30;
            boolean s = Ui.enCirculo(mx, my, cx, cy, 13);
            Ui.circulo(g, cx, cy, 13, 0xFFFFFFFF);
            Ui.circulo(g, cx, cy, 11, 0xFF000000);
            Ui.circulo(g, cx, cy, s ? 9 : 10, 0xFFFFFFFF);
            // última foto
            Ui.redondo(g, 12, cy - 11, 22, 22, 5, 0xFF2A2F35);
            if (!MovilCliente.FOTOS.isEmpty()) {
                FotoCache.Tex t = FotoCache.get(MovilRed.FOTO_PROPIA, MovilCliente.FOTOS.get(0).id());
                if (t != null) FotoCache.cubrir(g, t, 13, cy - 10, 20, 20);
            }
            boolean sg = Ui.enCirculo(mx, my, W - 23, cy, 10);
            Ui.boton(g, W - 23, cy, 10, sg ? 0xFF4A535C : 0xFF30373E, Ui.G_GIRAR, 0xFFFFFFFF);
        }

        private void disparar() {
            byte[] jpg = MovilCaptura.foto(MovilCliente.maxBytesFoto);
            if (jpg == null) {
                MovilCliente.aviso(Component.translatable("movil.miscelanea.aviso.foto_error"));
                return;
            }
            if (MovilCliente.FOTOS.size() >= MovilCliente.maxFotos) {
                MovilCliente.aviso(Component.translatable("movil.miscelanea.aviso.galeria_llena", MovilCliente.maxFotos));
                return;
            }
            tFlash = Util.getMillis();
            MovilCliente.sonido(MovilRegistro.OBTURADOR.get(), 0.9F, 1.0F);
            MovilRed.alServidor(MovilRed.C_FOTO_SUBIR, b -> b.writeByteArray(jpg));
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                disparar();
                return true;
            }
            return false;
        }

        @Override
        boolean clic(double x, double y) {
            if (Ui.dentro(x, y, 0, BARRA, 18, 16)) {
                MovilCliente.clic();
                m.atras();
                return true;
            }
            int cy = H - 30;
            if (Ui.enCirculo(x, y, W / 2, cy, 14)) {
                disparar();
                return true;
            }
            if (Ui.enCirculo(x, y, W - 23, cy, 11)) {
                selfie = !selfie;
                MovilCliente.clic();
                return true;
            }
            if (Ui.dentro(x, y, 10, cy - 13, 26, 26)) {
                MovilCliente.clic();
                m.abrir(new Galeria(null));
                return true;
            }
            return false;
        }
    }

    // ================================================================== galería
    static final class Galeria extends Vista {
        private static final int TW = 42, TH = 74, PASO_X = 44, PASO_Y = 76;
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
                if (t != null) FotoCache.cubrir(g, t, x, y, TW, TH);
                else Ui.rect(g, x, y, TW, TH, 0xFF1C232A);
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
    static final class Foto extends Vista {
        private final int tipo, id;
        private long confirmar;

        Foto(int tipo, int id) {
            this.tipo = tipo;
            this.id = id;
        }

        @Override
        int fondoBarra() {
            return 0xFF000000;
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            Ui.rect(g, 0, 0, W, H, 0xFF000000);
            int top = BARRA + 18, bot = H - ABAJO - (tipo == MovilRed.FOTO_PROPIA ? 26 : 2);
            FotoCache.Tex t = FotoCache.get(tipo, id);
            if (t != null) {
                float k = Math.min((float) W / t.w, (float) (bot - top) / t.h);
                int w = Math.round(t.w * k), h = Math.round(t.h * k);
                g.blit(t.rl, (W - w) / 2, top + (bot - top - h) / 2, w, h, 0, 0, t.w, t.h, t.w, t.h);
            } else Ui.centrado(g, t(FotoCache.falta(tipo, id) ? "foto_no_existe" : "cargando"), W / 2, H / 2 - 4, Ui.TEXTO2);
            Ui.glifo(g, Ui.G_ATRAS, 5, BARRA + 5, Ui.dentro(mx, my, 0, BARRA, 18, 18) ? 0xFFFFFFFF : 0xFFCCCCCC);
            if (tipo == MovilRed.FOTO_PROPIA) {
                int y = H - ABAJO - 22;
                boolean se = Ui.dentro(mx, my, 10, y, 52, 16), sb = Ui.dentro(mx, my, W - 62, y, 52, 16);
                Ui.redondo(g, 10, y, 52, 16, 8, se ? 0xFF33404C : 0xFF222A31);
                Ui.glifo(g, Ui.G_ENVIAR, 16, y + 4, Ui.ACENTO);
                Ui.texto(g, t("enviar"), 28, y + 4, Ui.TEXTO);
                boolean conf = Util.getMillis() - confirmar < 3000;
                Ui.redondo(g, W - 62, y, 52, 16, 8, conf ? Ui.ROJO : sb ? 0xFF33404C : 0xFF222A31);
                Ui.glifo(g, Ui.G_BORRAR, W - 56, y + 4, conf ? 0xFFFFFFFF : Ui.ROJO);
                Ui.texto(g, t(conf ? "seguro" : "borrar"), W - 44, y + 4, Ui.TEXTO);
            }
        }

        @Override
        boolean clic(double x, double y) {
            if (Ui.dentro(x, y, 0, BARRA, 18, 18)) {
                MovilCliente.clic();
                m.atras();
                return true;
            }
            if (tipo != MovilRed.FOTO_PROPIA) return false;
            int yb = H - ABAJO - 22;
            if (Ui.dentro(x, y, 10, yb, 52, 16)) {
                MovilCliente.clic();
                m.abrir(new VistasChat.ElegirChat(id));
                return true;
            }
            if (Ui.dentro(x, y, W - 62, yb, 52, 16)) {
                MovilCliente.clic();
                if (Util.getMillis() - confirmar < 3000) {
                    MovilRed.alServidor(MovilRed.C_FOTO_BORRAR, b -> b.writeVarInt(id));
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
