package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Ritmo: las notas llegan por la derecha; pulsa SALTAR cuando estén en el círculo. Pulsar sin nota también cuenta como fallo. */
public class JuegoRitmo extends JuegoQte {
    private static final float VEL = 0.11F;   // píxeles por ms
    private final int[] notas;
    private final int[] estado;                // 0 pendiente, 1 bien, 2 perfecta, 3 fallada
    private int aciertos, perfectos, fallos, siguienteTic;
    private long juicioT = -1000;
    private int juicio;
    private final int ventana;

    public JuegoRitmo(QteInicioPacket p) {
        super(p);
        notas = QteLogica.ritmoNotas(semilla, dif);
        estado = new int[notas.length];
        ventana = QteLogica.ritmoVentana(dif);
        sonido(TorturaRegistro.QTE_TIC.get(), 0.7F, 1F);
    }

    private long cancion() {
        return ms() - QteLogica.RITMO_PREVIO_MS;
    }

    @Override
    protected void logica() {
        long t = cancion();
        // metrónomo: un tic en cada nota
        while (siguienteTic < notas.length && t >= notas[siguienteTic]) {
            sonido(TorturaRegistro.QTE_TIC.get(), 1.6F, 0.35F);
            siguienteTic++;
        }
        for (int i = 0; i < notas.length; i++)
            if (estado[i] == 0 && t > notas[i] + ventana) {
                estado[i] = 3;
                juzgar(3);
            }
        int n = notas.length;
        if (fallos > n / 3 + 1) {
            acabar(QteResultado.FALLO);
            return;
        }
        if (t > notas[n - 1] + ventana + 200) {
            if (aciertos >= QteLogica.ritmoNecesarias(n, dif))
                acabar(aciertos == n && perfectos >= Math.ceil(n * 0.9) ? QteResultado.PERFECTO : QteResultado.EXITO);
            else acabar(QteResultado.FALLO);
        }
    }

    private void juzgar(int j) {
        juicio = j;
        juicioT = ms();
        if (j == 3) fallos++;
    }

    @Override
    public void tecla(int key, int scan, int accion) {
        if (accion != GLFW.GLFW_PRESS || !esSalto(key, scan) || cancion() < -ventana) return;
        long t = cancion();
        int mejor = -1;
        long dmin = Long.MAX_VALUE;
        for (int i = 0; i < notas.length; i++) {
            if (estado[i] != 0) continue;
            long d = Math.abs(t - notas[i]);
            if (d <= ventana && d < dmin) {
                dmin = d;
                mejor = i;
            }
        }
        if (mejor < 0) {
            juzgar(3);
            sacudida = 3;
            return;
        }
        aciertos++;
        if (dmin <= QteLogica.RITMO_PERFECTO_MS) {
            estado[mejor] = 2;
            perfectos++;
            juzgar(2);
            sonido(TorturaRegistro.QTE_PULSAR.get(), 1.5F, 0.9F);
        } else {
            estado[mejor] = 1;
            juzgar(1);
            sonido(TorturaRegistro.QTE_PULSAR.get(), 1.15F, 0.8F);
        }
    }

    @Override
    protected int[] datos() {
        return new int[]{aciertos, perfectos};
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        int ancho = 230, alto = 30;
        float cx = w / 2F + temblorX();
        int x0 = (int) (cx - ancho / 2F), y0 = (int) (h * 0.6F);
        Dibujo.panel(g, x0 - 8, y0 - 10, x0 + ancho + 8, y0 + alto + 10, 0xFFB060F0);
        g.fillGradient(x0, y0, x0 + ancho, y0 + alto, 0xFF1A1030, 0xFF2A1A48);
        float hx = x0 + 24, hy = y0 + alto / 2F;
        long t = cancion();
        float pulso = 0;
        for (int n : notas) {
            long d = t - n;
            if (d >= 0 && d < 120) pulso = Math.max(pulso, 1 - d / 120F);
        }
        // líneas de compás que pasan
        for (int i = 0; i < notas.length; i++) {
            float x = hx + (notas[i] - t) * VEL;
            if (x < x0 || x > x0 + ancho) continue;
            g.fill((int) x, y0 + 2, (int) x + 1, y0 + alto - 2, 0x20FFFFFF);
        }
        // círculo de golpeo
        Dibujo.arco(g, hx, hy, 10, 12.5F + pulso * 2, 0, 360, 0xFFE8D8FF);
        Dibujo.arco(g, hx, hy, 12.5F, 18 + pulso * 4, 0, 360, Dibujo.alfa(0xFFB070FF, 0.5F), 0x00B070FF);
        // notas
        for (int i = 0; i < notas.length; i++) {
            float x = hx + (notas[i] - t) * VEL;
            if (x < x0 - 10 || x > x0 + ancho + 10) continue;
            int e = estado[i];
            if (e == 1 || e == 2) continue;
            int c = e == 3 ? 0xFF603040 : 0xFFFF70C0;
            if (e == 0) Dibujo.brillo(g, x, hy, 7, 4, Dibujo.alfa(c, 0.45F));
            Dibujo.disco(g, x, hy, 7, c);
            Dibujo.disco(g, x - 2, hy - 2, 2.2F, e == 3 ? 0x40FFFFFF : 0xC0FFFFFF);
        }
        // juicio
        float k = Math.max(0, 1 - (ms() - juicioT) / 450F);
        if (k > 0.05F) {
            String clave = juicio == 2 ? "qte.miscelanea.ritmo.perfecto" : juicio == 1 ? "qte.miscelanea.ritmo.bien" : "qte.miscelanea.ritmo.fallo";
            int c = juicio == 2 ? 0xFFFFE060 : juicio == 1 ? 0xFF80F0A0 : 0xFFFF5050;
            Dibujo.texto(g, mc.font, Component.translatable(clave).withStyle(ChatFormatting.BOLD), hx, y0 - 22 - (1 - k) * 6, 1F, Dibujo.alfa(c, k), true);
        }
        String marc = aciertos + " / " + QteLogica.ritmoNecesarias(notas.length, dif);
        Dibujo.texto(g, mc.font, Component.literal(marc), x0 + ancho - 24, y0 + alto / 2F - 4, 1F, 0xFFE8E0FF, true);
        if (t < 0) {
            int cuenta = (int) Math.ceil(-t / 600.0);
            if (cuenta <= 3 && cuenta > 0)
                Dibujo.texto(g, mc.font, Component.literal(String.valueOf(cuenta)), cx, y0 + alto / 2F - 8, 2F, 0xFFFFFFFF, true);
        }
        marco(g, cx, y0 - 10, y0 + alto + 10, Component.translatable("qte.miscelanea.ayuda.ritmo", nombreTecla(com.miscelanea.client.TeclasMod.MJ_ACCION)));
    }
}
