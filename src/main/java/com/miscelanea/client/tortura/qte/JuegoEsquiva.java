package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * ESQUIVA: algo viene volando por un lado (la semilla dice cuál); pulsa la dirección CONTRARIA (A / D) antes de que
 * llegue para apartar la cabeza. Pulsar la otra, o no pulsar a tiempo, es fallo. 'rondas' solo cambia el dibujo de lo
 * que llega (1 = tomate, 2 = huevo, otro = piedra).
 */
public class JuegoEsquiva extends JuegoQte {
    private final int ventana, tecla;
    private final boolean izquierda;
    private int pulsada = -1, reaccion;
    private long golpeT = -1000;

    public JuegoEsquiva(QteInicioPacket p) {
        super(p);
        ventana = QteLogica.esquivaVentana(dif);
        izquierda = QteLogica.esquivaPorIzquierda(semilla);
        tecla = QteLogica.esquivaTecla(semilla);
        sonido(TorturaRegistro.QTE_TIC.get(), 1.4F, 1F);
    }

    @Override
    protected void logica() {
        if (ms() > ventana) acabar(QteResultado.FALLO);
    }

    @Override
    public void tecla(int key, int scan, int accion) {
        if (accion != GLFW.GLFW_PRESS || terminado) return;
        int d = direccion(key, scan);
        if (d != 2 && d != 3) return;
        pulsada = d;
        reaccion = (int) ms();
        golpeT = ms();
        if (d == tecla) {
            sonido(TorturaRegistro.ESQUIVA.get(), 1.1F, 0.8F);
            acabar(QteResultado.EXITO);
        } else {
            acabar(QteResultado.FALLO);
        }
    }

    @Override
    protected int[] datos() {
        return new int[]{pulsada, reaccion};
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        float cx = w / 2F + temblorX(), cy = h * 0.5F;
        int ancho = 200;
        int x0 = (int) (cx - ancho / 2F), y0 = (int) cy - 34, y1 = (int) cy + 40;
        Dibujo.panel(g, x0, y0, x0 + ancho, y1, 0xFFE04030);
        long ms = terminado ? Math.min(ventana, fin - inicio) : ms();
        float t = Math.min(1F, ms / (float) ventana);
        // cara en el cepo (de frente) y, si ya se apartó, inclinada hacia el lado bueno
        float lado = pulsada == 3 ? 1F : pulsada == 2 ? -1F : 0F;
        float aparta = Math.min(1F, (ms() - golpeT) / 120F);
        float fx = cx + lado * 14F * aparta, fy = cy - 2;
        g.fill((int) cx - 40, (int) fy + 9, (int) cx + 40, (int) fy + 15, 0xFF6A4A2C);              // tabla del cepo
        g.fill((int) cx - 40, (int) fy + 9, (int) cx + 40, (int) fy + 10, 0xFF8E6A44);
        g.fill((int) fx - 9, (int) fy - 9, (int) fx + 9, (int) fy + 9, 0xFF2A1A12);
        g.fillGradient((int) fx - 8, (int) fy - 8, (int) fx + 8, (int) fy + 8, 0xFFD8A884, 0xFFB88464);
        g.fill((int) fx - 8, (int) fy - 8, (int) fx + 8, (int) fy - 4, 0xFF4A3020);                // pelo
        g.fill((int) fx - 5, (int) fy - 1, (int) fx - 2, (int) fy + 1, 0xFF1A1A2A);                // ojos
        g.fill((int) fx + 2, (int) fy - 1, (int) fx + 5, (int) fy + 1, 0xFF1A1A2A);
        g.fill((int) fx - 3, (int) fy + 4, (int) fx + 3, (int) fy + 5, 0xFF7A3A30);
        // lo que llega: por un lado, cada vez más cerca y más grande
        float s = izquierda ? -1F : 1F;
        float px = cx + s * (90F - 78F * t), py = cy - 2 - 16F * (float) Math.sin(t * Math.PI) * 0.6F;
        float r = 3.5F + 4.5F * t;
        int col = rondas == 2 ? 0xFFEDE6D2 : rondas == 1 ? 0xFFB8261C : 0xFF8A8A8A;
        if (!terminado || resultado != QteResultado.EXITO || t < 1F) {
            Dibujo.brillo(g, px, py, r, 3, Dibujo.alfa(col, 0.9F));
            Dibujo.disco(g, px - r * 0.3F, py - r * 0.35F, r * 0.3F, 0x90FFFFFF);
            if (rondas == 1) Dibujo.disco(g, px + r * 0.2F, py - r * 0.75F, r * 0.25F, 0xFF4A6A2A);   // rabito
            for (int i = 1; i <= 3; i++)
                Dibujo.disco(g, px + s * i * 5F, py + i * 0.6F, Math.max(0.6F, r * (0.5F - i * 0.12F)), Dibujo.alfa(col, 0.35F - i * 0.08F));
        }
        // teclas: la buena brilla
        String a = nombreTecla(com.miscelanea.client.TeclasMod.MJ_IZQ), d = nombreTecla(com.miscelanea.client.TeclasMod.MJ_DER);
        int tt = 22;
        float brilloK = 0.5F + 0.5F * (float) Math.sin(ms() * 0.03);
        int estA = pulsada == 2 ? (tecla == 2 ? 3 : 4) : (tecla == 2 && !terminado ? 1 : 0);
        int estD = pulsada == 3 ? (tecla == 3 ? 3 : 4) : (tecla == 3 && !terminado ? 1 : 0);
        Dibujo.tecla(g, mc.font, (int) cx - 60 - tt, (int) cy + 14, tt, tt - 2, a, estA, brilloK);
        Dibujo.tecla(g, mc.font, (int) cx + 60, (int) cy + 14, tt, tt - 2, d, estD, brilloK);
        Dibujo.flecha(g, cx + (tecla == 3 ? 40 : -40), cy + 24, 6, tecla, Dibujo.alfa(0xFFFFE080, 0.6F + 0.4F * brilloK));
        // reloj
        Dibujo.arco(g, cx, cy + 25, 0, 6, 0, 360 * (1F - t), t < 0.7F ? 0xC0FFFFFF : 0xE0FF5040);
        marco(g, cx, y0, y1, Component.translatable("qte.miscelanea.ayuda.esquiva"));
    }
}
