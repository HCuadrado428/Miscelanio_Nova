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
    /** Esquina de "vídeo" sobre una miniatura: triángulo de play y la duración. */
    static void marcaVideo(GuiGraphics g, int x, int y, int w, int h, int ms) {
        Ui.circulo(g, x + w / 2, y + h / 2, Math.min(9, Math.min(w, h) / 4), 0x90000000);
        Ui.glifo(g, Ui.G_PLAY, x + w / 2 - 4, y + h / 2 - 4, 0xFFFFFFFF);
        String d = MovilCliente.duracion(Math.max(1, (ms + 500) / 1000));
        Ui.rect(g, x + w - 3 - Ui.anchoMini(d) - 2, y + h - 9, Ui.anchoMini(d) + 4, 7, 0xA0000000);
        Ui.mini(g, d, x + w - 3 - Ui.anchoMini(d), y + h - 8, 0xFFFFFFFF);
    }

    /**
     * Cámara: fotos y vídeos cortos, en vertical (9:16) o con el móvil girado (16:9, el botón del móvil tumbado o la tecla
     * R), con selfie, zoom (rueda del ratón, hasta x4), temporizador (3 o 10 s), filtros, flash de verdad (que ilumina y
     * deslumbra a quien tengas delante) y la última foto para ir a la galería. Espacio o Intro disparan.
     */
    static final class Camara extends Vista {
        private boolean selfie, horiz, modoVideo;
        private long tFlash, ultFoto;
        /** Para recordar cómo la dejaste la próxima vez que abras la cámara. */
        private static boolean horizAntes;
        /** Flash: 0 apagado, 1 siempre, 2 automático (si está oscuro). Temporizador en segundos (0, 3 o 10). */
        private static int flash, temporizador;
        private long finCuenta = -1, disparoPendiente = -1, tAviso;
        private int ultSeg = -1;
        private String aviso = "";
        // grabando un vídeo
        private boolean grabando, linternaAntes;
        private long tGrab, ultImagen;
        private final java.util.List<byte[]> imagenes = new java.util.ArrayList<>();
        private int bytesGrab;
        private static final int CLIP_FPS = 8, CLIP_W = 90, CLIP_H = 160, CLIP_MAX_IMAGEN = 7000;

        Camara() {
            horiz = horizAntes;
        }

        @Override
        void abierta() {
            if (!MovilCliente.fotosCargadas) MovilRed.alServidor(MovilRed.C_FOTOS, null);
        }

        @Override
        void cerrada() {
            if (grabando) pararGrabacion(false);
            MovilCaptura.zoom = 1;
            MovilCaptura.soltarPrevia();
            finCuenta = disparoPendiente = -1;
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
            return horiz ? new int[]{HW - 24, HH / 2} : new int[]{W / 2, H - 28};
        }

        private int[] ultima() {
            return horiz ? new int[]{HW - 24, HH - 20} : new int[]{23, H - 28};
        }

        private int[] cambiar() {
            return horiz ? new int[]{HW - 24, 18} : new int[]{W - 23, H - 28};
        }

        private int[] girar() {
            return horiz ? new int[]{18, HH - 18} : new int[]{W - 16, BARRA + 8};
        }

        /** Botones pequeños: 0 flash, 1 temporizador, 2 filtro. */
        private int[] ajuste(int i) {
            return horiz ? new int[]{28 + i * 18, 8} : new int[]{12 + i * 22, BARRA + 26};
        }

        /** Las pestañas FOTO / VÍDEO (en horizontal, una sola que cambia). */
        private int[] pestanas() {
            return horiz ? new int[]{HW - 24, HH / 2 + 19} : new int[]{W / 2, H - 50};
        }

        private boolean oscuro() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return false;
            return mc.level.getMaxLocalRawBrightness(net.minecraft.core.BlockPos.containing(mc.player.getEyePosition())) < 8;
        }

        private boolean usaFlash() {
            return flash == 1 || (flash == 2 && oscuro());
        }

        private void avisar(String s) {
            aviso = s;
            tAviso = Util.getMillis();
        }

        @Override
        void tick() {
            long ahora = Util.getMillis();
            // cuenta atrás: un pitido por segundo
            if (finCuenta > 0) {
                int seg = (int) Math.ceil((finCuenta - ahora) / 1000.0);
                if (seg != ultSeg && seg > 0) {
                    ultSeg = seg;
                    MovilCliente.sonido(MovilRegistro.CLIC.get(), 0.8F, seg <= 3 ? 1.7F : 1.3F);
                }
            }
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            int w = w(), h = h();
            long ahora = Util.getMillis();
            MovilCaptura.pedir(horiz);
            // lo que está programado: la cuenta atrás, el disparo tras el flash y las imágenes del vídeo
            if (finCuenta > 0 && ahora >= finCuenta) {
                finCuenta = -1;
                ultSeg = -1;
                if (modoVideo) empezarGrabacion();
                else conFlash();
            }
            if (disparoPendiente > 0 && ahora >= disparoPendiente) {
                disparoPendiente = -1;
                disparar();
            }
            if (grabando) grabar(ahora);

            Ui.rect(g, 0, 0, w, h, 0xFF000000);
            int vw = horiz ? w - 48 : w;      // en horizontal el visor deja sitio a la columna de botones
            if (MovilCaptura.lista()) {
                net.minecraft.resources.ResourceLocation pr = MovilCaptura.filtro > 0 ? MovilCaptura.previa() : null;
                if (pr != null) {
                    // la previa tiene la forma del visor entero (9:16 o 16:9): se recorta igual que él
                    int tw = horiz ? MovilCaptura.PH : MovilCaptura.PW, th = horiz ? MovilCaptura.PW : MovilCaptura.PH;
                    float k = Math.max((float) vw / tw, (float) h / th);
                    float sw = vw / k, sh = h / k;
                    g.blit(pr, 0, 0, vw, h, (tw - sw) / 2, (th - sh) / 2, (int) sw, (int) sh, tw, th);
                } else MovilCaptura.dibujarVisor(g, 0, 0, vw, h);
            } else Ui.centrado(g, t("iniciando_camara"), vw / 2, h / 2 - 4, Ui.TEXTO2);
            // cuadrícula de tercios
            int gy0 = horiz ? 0 : BARRA + 34, gy1 = horiz ? h : h - 58;
            for (int i = 1; i < 3; i++) {
                Ui.rect(g, vw * i / 3, gy0, 1, gy1 - gy0, 0x22FFFFFF);
                Ui.rect(g, 0, gy0 + (gy1 - gy0) * i / 3, vw, 1, 0x22FFFFFF);
            }
            long f = ahora - tFlash;
            if (f < 220) Ui.rect(g, 0, 0, w, h, ((int) (255 * (1 - f / 220F)) << 24) | 0xFFFFFF);
            String titulo = grabando ? "" : t(modoVideo ? "video" : selfie ? "selfie" : "foto");
            String n = modoVideo ? MovilCliente.duracion(MovilCliente.clipMaxSeg) : MovilCliente.FOTOS.size() + "/" + MovilCliente.maxFotos;
            int ty;
            if (horiz) {
                Ui.rect(g, w - 48, 0, 48, h, 0xB0000000);
                Ui.rect(g, 0, 0, vw, 16, 0x70000000);
                Ui.glifo(g, Ui.G_ATRAS, 5, 4, Ui.dentro(mx, my, 0, 0, 18, 16) ? 0xFFFFFFFF : 0xFFD0D0D0);
                Ui.centrado(g, titulo, vw / 2 + 20, 4, Ui.AMARILLO);
                Ui.mini(g, n, vw - 6 - Ui.anchoMini(n), 6, 0xFFFFFFFF);
                ty = 4;
            } else {
                Ui.rect(g, 0, 0, w, BARRA + 34, 0x90000000);
                Ui.centrado(g, titulo, w / 2, BARRA + 4, Ui.AMARILLO);
                Ui.mini(g, n, 22, BARRA + 6, 0xFFFFFFFF);
                Ui.glifo(g, Ui.G_ATRAS, 5, BARRA + 4, Ui.dentro(mx, my, 0, BARRA, 18, 16) ? 0xFFFFFFFF : 0xFFD0D0D0);
                Ui.rect(g, 0, h - 58, w, 58, 0xA0000000);
                ty = BARRA + 4;
            }
            // grabando: punto rojo y tiempo
            if (grabando) {
                String tt = MovilCliente.duracion((ahora - tGrab) / 1000) + " / " + MovilCliente.duracion(MovilCliente.clipMaxSeg);
                int cx = horiz ? vw / 2 + 20 : w / 2;
                if ((ahora / 500) % 2 == 0) Ui.circulo(g, cx - Ui.ancho(tt) / 2 - 6, ty + 4, 3, Ui.ROJO);
                Ui.centrado(g, tt, cx, ty, 0xFFFFFFFF);
            }
            dibujarAjustes(g, mx, my, vw);
            // zoom
            if (MovilCaptura.zoom > 1.01F || ahora - tAviso < 1200) {
                String z = String.format("x%.1f", MovilCaptura.zoom);
                int zx = horiz ? vw - 30 : w - 8 - Ui.ancho(z), zy = horiz ? 20 : BARRA + 22;
                Ui.redondo(g, zx - 3, zy - 2, Ui.ancho(z) + 6, 12, 5, 0x90000000);
                Ui.texto(g, z, zx, zy, MovilCaptura.zoom > 1.01F ? Ui.AMARILLO : 0xFFFFFFFF);
            }
            // aviso corto (nombre del filtro, del temporizador...)
            if (ahora - tAviso < 1200 && !aviso.isEmpty()) {
                int ay = horiz ? h - 30 : h - 72;
                int aw = Ui.ancho(aviso) + 10;
                Ui.redondo(g, vw / 2 - aw / 2, ay - 3, aw, 13, 6, 0xB0000000);
                Ui.centrado(g, aviso, vw / 2, ay, 0xFFFFFFFF);
            }
            // cuenta atrás
            if (finCuenta > 0) {
                int seg = (int) Math.ceil((finCuenta - ahora) / 1000.0);
                Ui.grande(g, Integer.toString(Math.max(1, seg)), vw / 2, h / 2 - 16, 4, 0xE0FFFFFF);
            }
            // pestañas FOTO / VÍDEO
            if (MovilCliente.clipsServidor && !grabando) {
                int[] p = pestanas();
                if (horiz) Ui.centrado(g, t(modoVideo ? "video" : "foto"), p[0], p[1], Ui.AMARILLO);
                else {
                    Ui.centrado(g, t("foto"), p[0] - 20, p[1], modoVideo ? 0xFFB0B0B0 : Ui.AMARILLO);
                    Ui.centrado(g, t("video"), p[0] + 20, p[1], modoVideo ? Ui.AMARILLO : 0xFFB0B0B0);
                }
            }
            // disparador (gris mientras se guarda la foto anterior; rojo en vídeo)
            int[] d = disparador();
            boolean listo = ahora - ultFoto > ESPERA && disparoPendiente < 0;
            boolean s = Ui.enCirculo(mx, my, d[0], d[1], 13);
            Ui.circulo(g, d[0], d[1], 13, 0xFFFFFFFF);
            Ui.circulo(g, d[0], d[1], 11, 0xFF000000);
            if (grabando) Ui.redondo(g, d[0] - 5, d[1] - 5, 10, 10, 2, Ui.ROJO);
            else if (modoVideo) Ui.circulo(g, d[0], d[1], s ? 9 : 10, 0xFFE0413A);
            else Ui.circulo(g, d[0], d[1], s && listo ? 9 : 10, listo ? 0xFFFFFFFF : 0xFF8A9199);
            if (grabando) return;
            // última foto
            int[] u = ultima();
            Ui.redondo(g, u[0] - 11, u[1] - 11, 22, 22, 5, 0xFF2A2F35);
            if (!MovilCliente.FOTOS.isEmpty()) {
                FotoCache.Tex tx = FotoCache.get(MovilRed.FOTO_PROPIA, MovilCliente.FOTOS.get(0).id());
                if (tx != null) FotoCache.cubrir(g, tx, u[0] - 10, u[1] - 10, 20, 20);
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

        private void dibujarAjustes(GuiGraphics g, int mx, int my, int vw) {
            if (grabando) return;
            for (int i = 0; i < 3; i++) {
                int[] p = ajuste(i);
                boolean s = Ui.enCirculo(mx, my, p[0], p[1], 8);
                if (s) Ui.circulo(g, p[0], p[1], 8, 0x50FFFFFF);
                switch (i) {
                    case 0 -> {
                        Ui.glifo(g, flash == 0 ? Ui.G_FLASH_NO : Ui.G_FLASH, p[0] - 4, p[1] - 4, flash == 0 ? 0xFFB0B0B0 : Ui.AMARILLO);
                        if (flash == 2) Ui.mini(g, "A", p[0] + 4, p[1] + 1, 0xFFFFFFFF);
                    }
                    case 1 -> {
                        Ui.glifo(g, Ui.G_TEMPORIZADOR, p[0] - 4, p[1] - 4, temporizador == 0 ? 0xFFB0B0B0 : Ui.AMARILLO);
                        if (temporizador > 0) Ui.mini(g, Integer.toString(temporizador), p[0] + 4, p[1] + 1, 0xFFFFFFFF);
                    }
                    default -> Ui.glifo(g, Ui.G_FILTRO, p[0] - 4, p[1] - 4, MovilCaptura.filtro == 0 ? 0xFFB0B0B0 : Ui.AMARILLO);
                }
            }
        }

        private static final long ESPERA = 700;

        /** Pulsar el disparador. */
        private void pulsar() {
            if (finCuenta > 0) {
                // segunda pulsación durante la cuenta atrás: se cancela
                finCuenta = -1;
                ultSeg = -1;
                MovilCliente.clic();
                return;
            }
            if (modoVideo) {
                if (grabando) pararGrabacion(true);
                else if (temporizador > 0) finCuenta = Util.getMillis() + temporizador * 1000L;
                else empezarGrabacion();
                return;
            }
            if (Util.getMillis() - ultFoto <= ESPERA || disparoPendiente > 0) return;
            if (temporizador > 0) finCuenta = Util.getMillis() + temporizador * 1000L;
            else conFlash();
        }

        /** Con flash: el servidor pone la luz y la foto se hace un momento después (cuando ya ha llegado). */
        private void conFlash() {
            if (usaFlash()) {
                MovilRed.alServidor(MovilRed.C_FLASH, null);
                disparoPendiente = Util.getMillis() + 230;
                tFlash = Util.getMillis();
            } else disparar();
        }

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

        // ------------------------------------------------------------------ vídeo
        private void empezarGrabacion() {
            if (grabando || !MovilCliente.clipsServidor) return;
            if (MovilCliente.FOTOS.size() >= MovilCliente.maxFotos) {
                MovilCliente.aviso(Component.translatable("movil.miscelanea.aviso.galeria_llena", MovilCliente.maxFotos));
                return;
            }
            grabando = true;
            tGrab = Util.getMillis();
            ultImagen = 0;
            imagenes.clear();
            bytesGrab = 0;
            NotasVozCliente.empezarVideo();
            // el flash en vídeo es la linterna encendida mientras se graba
            linternaAntes = MovilCliente.linterna;
            if (usaFlash()) MovilCliente.linterna(true);
            MovilCliente.sonido(MovilRegistro.CLIC.get(), 0.9F, 0.8F);
        }

        private void grabar(long ahora) {
            if (ahora - tGrab >= MovilCliente.clipMaxSeg * 1000L) {
                pararGrabacion(true);
                return;
            }
            if (ahora - ultImagen < 1000 / CLIP_FPS) return;
            // si se ha retrasado mucho, se cuenta desde ahora (no se meten imágenes de golpe)
            ultImagen = ahora - ultImagen > 2000 / CLIP_FPS ? ahora : ultImagen + 1000 / CLIP_FPS;
            byte[] f = MovilCaptura.clip(CLIP_W, CLIP_H, CLIP_MAX_IMAGEN);
            if (f == null) {
                // la primera aún no está: repetir la última para no descuadrar con el sonido
                if (!imagenes.isEmpty()) imagenes.add(imagenes.get(imagenes.size() - 1));
                return;
            }
            if (bytesGrab + f.length > MovilCliente.clipMaxBytes - 60000) {
                pararGrabacion(true);
                return;
            }
            imagenes.add(f);
            bytesGrab += f.length;
        }

        private void pararGrabacion(boolean guardar) {
            if (!grabando) return;
            grabando = false;
            byte[] audio = NotasVozCliente.terminarVideo();
            if (!linternaAntes) MovilCliente.linterna(false);
            java.util.List<byte[]> l = new java.util.ArrayList<>(imagenes);
            imagenes.clear();
            if (!guardar) return;
            if (l.size() < CLIP_FPS) {
                MovilCliente.aviso(Component.translatable("movil.miscelanea.video_corto_poco"));
                return;
            }
            boolean hz = horiz;
            byte[] blob = ClipCliente.empaquetar(CLIP_FPS, hz ? CLIP_H : CLIP_W, hz ? CLIP_W : CLIP_H, l, audio);
            if (blob.length > MovilCliente.clipMaxBytes && audio != null)
                blob = ClipCliente.empaquetar(CLIP_FPS, hz ? CLIP_H : CLIP_W, hz ? CLIP_W : CLIP_H, l, null);
            if (blob.length > MovilCliente.clipMaxBytes) {
                MovilCliente.aviso(Component.translatable("movil.miscelanea.aviso.video_grande"));
                return;
            }
            ClipCliente.subir(blob);
            tFlash = ultFoto = Util.getMillis();
            MovilCliente.sonido(MovilRegistro.CLIC.get(), 0.9F, 1.2F);
        }

        // ------------------------------------------------------------------ ajustes
        private void alternarGiro() {
            if (grabando) return;
            horiz = !horiz;
            horizAntes = horiz;
            MovilCliente.clic();
        }

        private void cambiarFlash() {
            flash = (flash + 1) % 3;
            avisar(t("flash" + flash));
            MovilCliente.clic();
        }

        private void cambiarTemporizador() {
            temporizador = temporizador == 0 ? 3 : temporizador == 3 ? 10 : 0;
            avisar(temporizador == 0 ? t("temporizador_no") : t("temporizador_s", temporizador));
            MovilCliente.clic();
        }

        private void cambiarFiltro(int d) {
            MovilCaptura.filtro = Math.floorMod(MovilCaptura.filtro + d, Filtros.N);
            avisar(t("filtro" + MovilCaptura.filtro));
            MovilCliente.clic();
        }

        private void cambiarModo() {
            if (grabando || !MovilCliente.clipsServidor) return;
            modoVideo = !modoVideo;
            finCuenta = -1;
            MovilCliente.clic();
        }

        @Override
        void rueda(double d) {
            float z = MovilCaptura.zoom * (d > 0 ? 1.15F : 1 / 1.15F);
            MovilCaptura.zoom = Math.max(1, Math.min(4, z));
            if (MovilCaptura.zoom < 1.04F) MovilCaptura.zoom = 1;
            avisar("");
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                pulsar();
                return true;
            }
            if (key == GLFW.GLFW_KEY_R) {
                alternarGiro();
                return true;
            }
            if (key == GLFW.GLFW_KEY_F) {
                cambiarFiltro((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_T) {
                cambiarTemporizador();
                return true;
            }
            if (key == GLFW.GLFW_KEY_V) {
                cambiarModo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
                rueda(1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
                rueda(-1);
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
            int[] d = disparador(), c = cambiar(), u = ultima(), gr = girar(), p = pestanas();
            if (Ui.enCirculo(x, y, d[0], d[1], 14)) {
                pulsar();
                return true;
            }
            if (grabando) return false;
            for (int i = 0; i < 3; i++) {
                int[] a = ajuste(i);
                if (!Ui.enCirculo(x, y, a[0], a[1], 9)) continue;
                if (i == 0) cambiarFlash();
                else if (i == 1) cambiarTemporizador();
                else cambiarFiltro(1);
                return true;
            }
            if (MovilCliente.clipsServidor && (horiz ? Ui.dentro(x, y, p[0] - 22, p[1] - 3, 44, 12) : Ui.dentro(x, y, p[0] - 40, p[1] - 3, 80, 12))) {
                if (horiz) cambiarModo();
                else if ((x >= p[0]) != modoVideo) cambiarModo();
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
                    if (l.get(i).ms() > 0) marcaVideo(g, x, y, TW, TH, l.get(i).ms());
                    else if (t.w > t.h) {
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
                    } else m.abrir(new Foto(MovilRed.FOTO_PROPIA, id, l.get(i).ms()));
                    return true;
                }
            }
            return false;
        }
    }

    // ================================================================== ver una foto
    /**
     * Una foto a pantalla completa; si es apaisada, el móvil se gira solo para verla grande. Si es un vídeo corto (ms &gt; 0),
     * se descarga al abrirla y se reproduce tocándola (con su sonido, si lo grabó con Simple Voice Chat).
     */
    static final class Foto extends Vista {
        private final int tipo, id, ms;
        private long confirmar;
        private ClipCliente.Reproductor rep;

        Foto(int tipo, int id) {
            this(tipo, id, 0);
        }

        Foto(int tipo, int id, int ms) {
            this.tipo = tipo;
            this.id = id;
            this.ms = ms;
        }

        @Override
        void cerrada() {
            if (rep != null) rep.soltar();
            rep = null;
        }

        /** Si es vídeo: el reproductor (cuando ya se ha descargado). */
        private ClipCliente.Reproductor reproductor() {
            if (ms <= 0) return null;
            if (rep == null) {
                ClipCliente.Clip c = ClipCliente.get(tipo, id);
                if (c != null) {
                    rep = new ClipCliente.Reproductor(c);
                    rep.play();
                }
            }
            return rep;
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
            ClipCliente.Reproductor r = reproductor();
            if (r != null) r.actualizar();
            if (r != null && r.rl != null) {
                float k = Math.min((float) w / r.w(), (float) (bot - top) / r.h());
                int fw = Math.round(r.w() * k), fh = Math.round(r.h() * k);
                int fx = (w - fw) / 2, fy = top + (bot - top - fh) / 2;
                g.blit(r.rl, fx, fy, fw, fh, 0, 0, r.w(), r.h(), r.w(), r.h());
                if (!r.reproduciendo()) {
                    Ui.circulo(g, w / 2, fy + fh / 2, 14, 0x90000000);
                    Ui.glifo(g, Ui.G_PLAY, w / 2 - 4, fy + fh / 2 - 4, 0xFFFFFFFF);
                }
                // barra de progreso y tiempo
                Ui.rect(g, fx, fy + fh - 3, fw, 2, 0x60FFFFFF);
                Ui.rect(g, fx, fy + fh - 3, (int) (fw * r.progreso()), 2, Ui.ROJO);
                String d = MovilCliente.duracion((long) (r.progreso() * r.ms()) / 1000) + " / " + MovilCliente.duracion(Math.max(1, (r.ms() + 500) / 1000));
                Ui.mini(g, d, fx + 3, fy + fh - 10, 0xFFFFFFFF);
            } else if (t != null) {
                float k = Math.min((float) w / t.w, (float) (bot - top) / t.h);
                int fw = Math.round(t.w * k), fh = Math.round(t.h * k);
                g.blit(t.rl, (w - fw) / 2, top + (bot - top - fh) / 2, fw, fh, 0, 0, t.w, t.h, t.w, t.h);
                if (ms > 0) Ui.centrado(g, t(ClipCliente.falta(tipo, id) ? "foto_no_existe" : "cargando"), w / 2, top + (bot - top) / 2 + 18, 0xFFFFFFFF);
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
            if (propia()) {
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
            }
            if (rep != null) {
                // tocar el vídeo: reproducir o parar
                if (rep.reproduciendo()) rep.pausa();
                else rep.play();
                return true;
            }
            return false;
        }
    }
}
