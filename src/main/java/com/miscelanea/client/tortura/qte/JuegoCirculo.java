package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Prueba de habilidad de Dead by Daylight: aguja que gira, zona blanca y franja "perfecta" al principio de la zona. */
public class JuegoCirculo extends JuegoQte {
    private static final int PREVIO = 0, GIRANDO = 1, PAUSA = 2;
    private int ronda, aciertos, perfectos, estado = PREVIO;
    private long rondaInicio, pausaFin;
    private float zona;
    private float marca = -1;
    private int marcaCol;
    private long marcaT;

    public JuegoCirculo(QteInicioPacket p) {
        super(p);
        zona = QteLogica.circZona(semilla, 0);
        sonido(TorturaRegistro.QTE_TIC.get(), 1F, 1F);
    }

    private float angulo() {
        long t = ms() - rondaInicio - QteLogica.CIRC_PREVIO_MS;
        return t < 0 ? 0 : t * QteLogica.circVel(dif);
    }

    @Override
    protected void logica() {
        long ahora = ms();
        if (estado == PREVIO && ahora >= rondaInicio + QteLogica.CIRC_PREVIO_MS) estado = GIRANDO;
        if (estado == GIRANDO && angulo() >= 360F) {
            marca = 359.9F;
            marcaCol = 0xFFFF4040;
            marcaT = ahora;
            acabar(QteResultado.FALLO);
        }
        if (estado == PAUSA && ahora >= pausaFin) {
            ronda++;
            rondaInicio = ahora;
            zona = QteLogica.circZona(semilla, ronda);
            estado = PREVIO;
            marca = -1;
            sonido(TorturaRegistro.QTE_TIC.get(), 1F, 1F);
        }
    }

    @Override
    public void tecla(int key, int scan, int accion) {
        if (accion != GLFW.GLFW_PRESS || !esSalto(key, scan) || estado != GIRANDO) return;
        float a = angulo();
        float ancho = QteLogica.circAncho(dif), perf = QteLogica.circPerfecto(dif);
        marca = a;
        marcaT = ms();
        if (a >= zona && a <= zona + perf) {
            aciertos++;
            perfectos++;
            marcaCol = 0xFFFFE070;
            sonido(TorturaRegistro.QTE_PULSAR.get(), 1.5F, 1F);
        } else if (a >= zona && a <= zona + ancho) {
            aciertos++;
            marcaCol = 0xFFFFFFFF;
            sonido(TorturaRegistro.QTE_PULSAR.get(), 1.1F, 1F);
        } else {
            marcaCol = 0xFFFF4040;
            acabar(QteResultado.FALLO);
            return;
        }
        if (aciertos >= rondas) acabar(perfectos >= rondas ? QteResultado.PERFECTO : QteResultado.EXITO);
        else {
            estado = PAUSA;
            pausaFin = ms() + QteLogica.CIRC_PAUSA_MS;
        }
    }

    @Override
    protected int[] datos() {
        return new int[]{aciertos, perfectos};
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        float cx = w / 2F + temblorX(), cy = h * 0.56F;
        float R = 30;
        long ahora = ms();
        // aparece con un pequeño zoom
        float aparicion = Math.min(1, (ahora - rondaInicio) / 140F);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        float esc = 0.75F + 0.25F * aparicion;
        g.pose().scale(esc, esc, 1);
        g.pose().translate(-cx, -cy, 0);
        Dibujo.arco(g, cx, cy, R + 6, R + 16, 0, 360, 0x50000000, 0x00000000);
        Dibujo.disco(g, cx, cy, R + 6, 0x8A08090C);
        // pista
        Dibujo.arco(g, cx, cy, R - 1.2F, R + 1.2F, 0, 360, 0xE0F4F4F4);
        // zona buena (hueca) y perfecta (rellena)
        float ancho = QteLogica.circAncho(dif), perf = QteLogica.circPerfecto(dif);
        boolean fin = terminado;
        int cz = fin && resultado != null && !resultado.exito() ? 0xFFFF5050 : 0xFFFFFFFF;
        Dibujo.arco(g, cx, cy, R - 5, R + 5, zona, zona + ancho, Dibujo.alfa(cz, 0.22F));
        Dibujo.arco(g, cx, cy, R + 4, R + 5.5F, zona, zona + ancho, cz);
        Dibujo.arco(g, cx, cy, R - 5.5F, R - 4, zona, zona + ancho, cz);
        Dibujo.arco(g, cx, cy, R - 5.5F, R + 5.5F, zona + ancho - 0.9F, zona + ancho, cz);
        Dibujo.arco(g, cx, cy, R - 5, R + 5, zona, zona + perf, 0xFFFFFFFF);
        // aguja
        if (estado != PREVIO || fin) {
            float a = marca >= 0 ? marca : angulo();
            double rad = Math.toRadians(a);
            float sx = (float) Math.sin(rad), sy = (float) -Math.cos(rad);
            Dibujo.linea(g, cx, cy, cx + sx * (R + 9), cy + sy * (R + 9), 5F, 0x40FF2020);
            Dibujo.linea(g, cx, cy, cx + sx * (R + 8), cy + sy * (R + 8), 2.2F, 0xFFE8241C);
            Dibujo.linea(g, cx + sx * (R - 6), cy + sy * (R - 6), cx + sx * (R + 8), cy + sy * (R + 8), 1.0F, 0xFFFF9A90);
        }
        if (marca >= 0) {
            float k = Math.max(0, 1 - (ahora - marcaT) / 400F);
            double rad = Math.toRadians(marca);
            Dibujo.brillo(g, cx + (float) Math.sin(rad) * R, cy - (float) Math.cos(rad) * R, 2.5F, 5 + 6 * (1 - k), Dibujo.alfa(marcaCol, 0.4F + 0.6F * k));
        }
        Dibujo.disco(g, cx, cy, 3.2F, 0xFF2A0606);
        Dibujo.disco(g, cx, cy, 2.2F, 0xFFE8241C);
        g.pose().popPose();
        // tecla en el centro, debajo, y rondas
        String t = nombreTecla(mc.options.keyJump);
        int tw = Math.max(34, mc.font.width(t) + 12);
        Dibujo.tecla(g, mc.font, (int) (cx - tw / 2F), (int) (cy + R + 14), tw, 14, t, estado == GIRANDO ? 1 : 0, 0.6F);
        for (int i = 0; i < rondas && rondas > 1; i++) {
            float x = cx - (rondas - 1) * 5 + i * 10;
            Dibujo.disco(g, x, cy - R - 14, 3F, 0xFF000000);
            Dibujo.disco(g, x, cy - R - 14, 2.2F, i < aciertos ? 0xFFFFFFFF : 0xFF505058);
        }
        marco(g, cx, cy - R - 22, cy + R + 30, Component.translatable("qte.miscelanea.ayuda.circulo", t));
    }
}
