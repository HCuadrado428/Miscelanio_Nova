package com.miscelanea.client.movil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Una pantalla de una app del móvil. Coordenadas locales a la pantalla del móvil (0..{@link #W}, 0..{@link #H}); la barra
 * de estado ocupa las primeras {@link #BARRA} filas y la barra de inicio las últimas {@link #ABAJO}.
 */
abstract class Vista {
    static final int W = MovilScreen.SW, H = MovilScreen.SH, BARRA = 11, CAB = 20, ABAJO = 8;
    MovilScreen m;
    /** Desplazamiento vertical de la lista (0 = arriba) y su máximo. */
    double scroll, scrollMax;

    abstract void dibujar(GuiGraphics g, int mx, int my, float pt);

    /** Toque (al soltar sin arrastrar). */
    boolean clic(double x, double y) {
        return false;
    }

    /**
     * Al bajar el botón. Si devuelve true, la vista se queda con el gesto: recibe {@link #soltar} al levantarlo (y no hay
     * clic ni desplazamiento). Para botones de "mantener pulsado" (grabar una nota de voz).
     */
    boolean pulsar(double x, double y) {
        return false;
    }

    void soltar(double x, double y) {}

    void arrastrar(double x, double y, double dx, double dy) {
        scroll = Mth.clamp(scroll - dy, 0, Math.max(0, scrollMax));
    }

    void rueda(double d) {
        scroll = Mth.clamp(scroll - d * 14, 0, Math.max(0, scrollMax));
    }

    boolean tecla(int key, int scan, int mods) {
        return false;
    }

    boolean caracter(char c) {
        return false;
    }

    /** Hay un campo de texto activo (las letras no cierran el móvil). */
    boolean escribiendo() {
        return false;
    }

    void tick() {}

    void abierta() {}

    void cerrada() {}

    /** Quiere la cámara del juego en tercera persona de frente (selfie). */
    boolean quiereFrontal() {
        return false;
    }

    /** Barra de estado con texto oscuro (para fondos claros). */
    boolean barraOscura() {
        return false;
    }

    /** Color de fondo bajo la barra de estado (0 = transparente). */
    int fondoBarra() {
        return Ui.CABECERA;
    }

    static String t(String clave, Object... args) {
        return Component.translatable("movil.miscelanea." + clave, args).getString();
    }

    /** Cabecera con flecha atrás y título. */
    void cabecera(GuiGraphics g, String titulo, int mx, int my) {
        Ui.rect(g, 0, 0, W, BARRA + CAB, Ui.CABECERA);
        boolean sobre = Ui.dentro(mx, my, 0, BARRA, 18, CAB);
        if (sobre) Ui.circulo(g, 9, BARRA + 10, 7, 0x22FFFFFF);
        Ui.glifo(g, Ui.G_ATRAS, 5, BARRA + 6, Ui.TEXTO);
        Ui.texto(g, Ui.cortar(titulo, W - 44), 20, BARRA + 6, Ui.TEXTO);
        Ui.rect(g, 0, BARRA + CAB, W, 1, Ui.LINEA);
    }

    boolean clicAtras(double x, double y) {
        if (Ui.dentro(x, y, 0, BARRA, 18, CAB)) {
            MovilCliente.clic();
            m.atras();
            return true;
        }
        return false;
    }

    /** Fondo liso de la app. */
    void fondo(GuiGraphics g) {
        Ui.rect(g, 0, 0, W, H, Ui.FONDO);
    }

    /** Recorta lo que se dibuje a la franja [y0, y1) de la pantalla. */
    void recortar(GuiGraphics g, int y0, int y1) {
        m.recortar(g, 0, y0, W, y1);
    }

    void soltarRecorte(GuiGraphics g) {
        g.disableScissor();
    }

    /** Barra de desplazamiento fina a la derecha. */
    void barraScroll(GuiGraphics g, int y0, int y1) {
        if (scrollMax <= 0) return;
        int alto = y1 - y0, largo = Math.max(10, (int) (alto * alto / (alto + scrollMax)));
        int y = y0 + (int) ((alto - largo) * (scroll / scrollMax));
        Ui.redondo(g, W - 3, y, 2, largo, 1, 0x40FFFFFF);
    }

    /** Hora o fecha corta de un instante real (hoy: hh:mm; otro día: dd/mm). */
    static String cuando(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance(), h = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        boolean hoy = c.get(java.util.Calendar.YEAR) == h.get(java.util.Calendar.YEAR) && c.get(java.util.Calendar.DAY_OF_YEAR) == h.get(java.util.Calendar.DAY_OF_YEAR);
        return hoy ? String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
                : String.format("%02d/%02d", c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1);
    }
}
