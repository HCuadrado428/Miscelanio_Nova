package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** MACHACAR (aporrear SALTAR) y ALTERNAR (izquierda / derecha): llena la barra antes de que se acabe el tiempo. */
public class JuegoMachacar extends JuegoQte {
    private final boolean alternar;
    private float barra, barraO;
    private int pulsaciones, esperado = 2;
    private long ultima = -1000, golpe = -1000;
    private int ladoGolpe = -1;

    public JuegoMachacar(QteInicioPacket p) {
        super(p);
        alternar = p.tipo == QteTipo.ALTERNAR;
        sonido(TorturaRegistro.QTE_TIC.get(), 0.8F, 1F);
    }

    private boolean listo() {
        return ms() >= QteLogica.MACH_PREVIO_MS;
    }

    @Override
    protected void logica() {
        barraO = barra;
        if (!listo()) return;
        barra = Math.max(0, barra - QteLogica.machCaidaTick(dif));
        if (ms() > QteLogica.MACH_PREVIO_MS + QteLogica.MACH_TIEMPO_MS) acabar(QteResultado.FALLO);
    }

    @Override
    public void tecla(int key, int scan, int accion) {
        if (accion != GLFW.GLFW_PRESS || !listo()) return;
        long ahora = ms();
        if (alternar) {
            int d = direccion(key, scan);
            if (d != 2 && d != 3) return;
            if (ahora - ultima < QteLogica.MACH_MIN_ENTRE_MS) return;
            ultima = ahora;
            golpe = ahora;
            ladoGolpe = d;
            if (d == esperado) {
                esperado = d == 2 ? 3 : 2;
                sumar();
            } else {
                barra = Math.max(0, barra - QteLogica.machGanancia(dif, true) * 0.5F);
                sacudida = 3;
            }
        } else {
            if (!esSalto(key, scan) || ahora - ultima < QteLogica.MACH_MIN_ENTRE_MS) return;
            ultima = ahora;
            golpe = ahora;
            sumar();
        }
    }

    private void sumar() {
        pulsaciones++;
        barra += QteLogica.machGanancia(dif, alternar);
        sonido(TorturaRegistro.QTE_PULSAR.get(), 0.8F + barra * 0.6F, 0.7F);
        if (barra >= 1F) {
            barra = 1F;
            acabar(QteResultado.EXITO);
        }
    }

    @Override
    protected int[] datos() {
        return new int[]{pulsaciones};
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        float cx = w / 2F + temblorX(), cy = h * 0.58F;
        int bw = 150, bh = 10;
        int x0 = (int) (cx - bw / 2F), y0 = (int) cy + 14;
        Dibujo.panel(g, x0 - 14, (int) cy - 30, x0 + bw + 14, y0 + bh + 18, 0xFFE04030);
        long ahora = ms();
        float pulso = Math.max(0, 1 - (ahora - golpe) / 110F);
        if (alternar) {
            String a = nombreTecla(mc.options.keyLeft), d = nombreTecla(mc.options.keyRight);
            int t = 26;
            for (int lado = 2; lado <= 3; lado++) {
                int x = (int) (cx + (lado == 2 ? -t - 8 : 8));
                int est = (ladoGolpe == lado && pulso > 0) ? 2 : (esperado == lado && !terminado && listo() ? 1 : 0);
                Dibujo.tecla(g, mc.font, x, (int) cy - 22, t, t, lado == 2 ? a : d, est, 0.8F);
            }
        } else {
            String s = nombreTecla(mc.options.keyJump);
            int t = Math.max(70, mc.font.width(s) + 20);
            int est = pulso > 0.4F ? 2 : (terminado ? 0 : 1);
            Dibujo.tecla(g, mc.font, (int) (cx - t / 2F), (int) cy - 20, t, 22, s, est, 0.5F + pulso * 0.5F);
            // chispas al pulsar
            for (int i = 0; i < 6 && pulso > 0; i++) {
                double a = i * Math.PI / 3 + golpe * 0.01;
                float r = 18 + (1 - pulso) * 22;
                Dibujo.disco(g, cx + (float) Math.cos(a) * r * 1.6F, cy - 9 + (float) Math.sin(a) * r * 0.6F, 1.4F * pulso + 0.3F, Dibujo.alfa(0xFFFFE070, pulso));
            }
        }
        float v = Mth.lerp(pt, barraO, barra);
        int c0 = Dibujo.mezcla(0xFFE04030, 0xFF60E070, v);
        Dibujo.barra(g, x0, y0, bw, bh, v, Dibujo.mezcla(c0, 0xFFFFFFFF, 0.2F), Dibujo.mezcla(c0, 0xFF000000, 0.3F));
        // reloj
        float resto = listo() ? 1F - (ms() - QteLogica.MACH_PREVIO_MS) / (float) QteLogica.MACH_TIEMPO_MS : 1F;
        Dibujo.arco(g, x0 + bw + 2, (int) cy - 9, 0, 6, 0, 360 * Math.max(0, resto), resto > 0.3F ? 0xC0FFFFFF : 0xE0FF5040);
        marco(g, cx, (int) cy - 30, y0 + bh + 18, Component.translatable(alternar ? "qte.miscelanea.ayuda.alternar" : "qte.miscelanea.ayuda.machacar"));
    }
}
