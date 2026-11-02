package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Secuencia de flechas (WASD o cursores) en orden y a contrarreloj. Una equivocada = fallo. */
public class JuegoSecuencia extends JuegoQte {
    private final int[] teclas;
    private int idx = 0, error = -1;
    private long ultimoAcierto = -1000;
    private final int tiempo;

    public JuegoSecuencia(QteInicioPacket p) {
        super(p);
        teclas = QteLogica.secTeclas(semilla, dif);
        tiempo = QteLogica.secTiempoMs(dif);
        sonido(TorturaRegistro.QTE_TIC.get(), 0.9F, 1F);
    }

    @Override
    protected void logica() {
        if (ms() > QteLogica.SEC_PREVIO_MS + tiempo) acabar(QteResultado.FALLO);
    }

    @Override
    public void tecla(int key, int scan, int accion) {
        if (accion != GLFW.GLFW_PRESS || ms() < QteLogica.SEC_PREVIO_MS) return;
        int d = direccion(key, scan);
        if (d < 0) return;
        if (d == teclas[idx]) {
            idx++;
            ultimoAcierto = ms();
            sonido(TorturaRegistro.QTE_PULSAR.get(), 0.9F + idx * 0.08F, 1F);
            if (idx >= teclas.length) acabar(QteResultado.EXITO);
        } else {
            error = idx;
            acabar(QteResultado.FALLO);
        }
    }

    @Override
    protected int[] datos() {
        return new int[]{idx};
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        int n = teclas.length, t = 24, sep = 6;
        int ancho = n * t + (n - 1) * sep;
        float cx = w / 2F + temblorX(), cy = h * 0.58F;
        int x0 = (int) (cx - ancho / 2F), y0 = (int) cy - t / 2;
        Dibujo.panel(g, x0 - 12, y0 - 14, x0 + ancho + 12, y0 + t + 20, 0xFFE0B040);
        long ahora = ms();
        boolean listo = ahora >= QteLogica.SEC_PREVIO_MS;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (t + sep);
            int est = i < idx ? 3 : i == error ? 4 : (i == idx && listo && !terminado ? 1 : 0);
            int y = y0;
            if (i == idx - 1) y -= (int) (Math.max(0, 1 - (ahora - ultimoAcierto) / 160F) * 4);
            float brillo = 0.5F + 0.5F * (float) Math.sin(ahora / 90.0);
            Dibujo.tecla(g, mc.font, x, y, t, t, "", est, brillo);
            int col = est == 0 ? 0xFF2A2A30 : 0xFF141418;
            Dibujo.flecha(g, x + t / 2F, y + t / 2F - 1 + (est == 2 ? 2 : 0), 7.5F, teclas[i], col);
        }
        // tiempo
        float resto = listo ? 1F - (ahora - QteLogica.SEC_PREVIO_MS) / (float) tiempo : 1F;
        int c0 = resto > 0.5F ? 0xFF60E070 : resto > 0.25F ? 0xFFE8C040 : 0xFFF04030;
        Dibujo.barra(g, x0, y0 + t + 8, ancho, 4, resto, c0, Dibujo.mezcla(c0, 0xFF000000, 0.35F));
        marco(g, cx, y0 - 14, y0 + t + 20, Component.translatable("qte.miscelanea.ayuda.secuencia"));
    }
}
