package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CORTAR_CABLES: el panel de una bomba ("Keep Talking and Nobody Explodes" / el collar de Dead Money). A la izquierda,
 * el módulo con 4-6 cables de colores, el número de serie, el piloto CARGA y el reloj que pita cada vez más deprisa; a
 * la derecha, la página del manual con las reglas para ese número de cables. Arriba / abajo (o 1-6) elige el cable,
 * SALTAR lo corta. El primer corte decide. Los cables cortados en intentos anteriores del mismo panel (misma semilla)
 * se quedan cortados en el dibujo.
 */
public class JuegoCortarCables extends JuegoQte {
    /** Cables ya cortados por panel (solo para el dibujo de los siguientes intentos; el servidor lleva la cuenta de verdad). */
    private static final Map<Long, Set<Integer>> CORTADOS = new HashMap<>();
    static final int[] COLOR = {0xFFD0281E, 0xFF2A5CD8, 0xFFE6C424, 0xFFE6E6E0, 0xFF222226};
    private static final String[] NOMBRE = {"rojo", "azul", "amarillo", "blanco", "negro"};

    private final int n, tiempo;
    private final int[] colores;
    private final String serie;
    private final boolean piloto;
    private final Set<Integer> cortados;
    private int sel, cortado = -1;
    private long ultimoPitido = -10000, cambioSel;

    public JuegoCortarCables(QteInicioPacket p) {
        super(p);
        n = QteLogica.cablesNum(dif);
        colores = QteLogica.cablesColores(semilla, dif);
        serie = QteLogica.cablesSerie(semilla);
        piloto = QteLogica.cablesPiloto(semilla, dif);
        tiempo = QteLogica.cablesTiempoMs(dif);
        if (CORTADOS.size() > 32) CORTADOS.clear();
        cortados = CORTADOS.computeIfAbsent(semilla, k -> new HashSet<>());
        sel = siguienteLibre(0, 1);
        sonido(TorturaRegistro.QTE_TIC.get(), 0.7F, 1F);
    }

    private int siguienteLibre(int desde, int paso) {
        for (int k = 0; k < n; k++) {
            int i = Math.floorMod(desde + k * paso, n);
            if (!cortados.contains(i)) return i;
        }
        return desde;
    }

    private float quedaFrac() {
        long ms = terminado ? fin - inicio : ms();
        return Mth.clamp(1F - (ms - QteLogica.CABLES_PREVIO_MS) / (float) tiempo, 0F, 1F);
    }

    @Override
    protected void logica() {
        long ms = ms();
        if (ms > QteLogica.CABLES_PREVIO_MS + tiempo) {
            acabar(QteResultado.FALLO);
            return;
        }
        // pitidos: de uno por segundo a casi seguidos al final
        float q = quedaFrac();
        long intervalo = (long) (140 + 860 * q);
        if (ms - ultimoPitido >= intervalo) {
            ultimoPitido = ms;
            sonido(TorturaRegistro.QTE_TIC.get(), 1.7F + (1F - q) * 0.4F, 0.6F);
        }
    }

    @Override
    public void tecla(int key, int scan, int accion) {
        if (terminado || accion == GLFW.GLFW_RELEASE || ms() < QteLogica.CABLES_PREVIO_MS) return;
        int d = direccion(key, scan);
        if (d == 0 || d == 1) {
            sel = siguienteLibre(sel + (d == 0 ? -1 : 1), d == 0 ? -1 : 1);
            cambioSel = ms();
            sonido(TorturaRegistro.QTE_PULSAR.get(), 1.4F, 0.4F);
            return;
        }
        int num = com.miscelanea.client.TeclasMod.numero(key, scan, 6);   // teclas de la barra rápida (1-6)
        if (num >= 0 && accion == GLFW.GLFW_PRESS) {
            int i = num;
            if (i < n && !cortados.contains(i)) {
                sel = i;
                cambioSel = ms();
                sonido(TorturaRegistro.QTE_PULSAR.get(), 1.4F, 0.4F);
            }
            return;
        }
        if (accion == GLFW.GLFW_PRESS && esSalto(key, scan) && !cortados.contains(sel)) {
            cortado = sel;
            sonido(SoundEvents.SHEEP_SHEAR, 1.2F, 1F);
            if (sel == QteLogica.cablesCorrecto(semilla, dif)) {
                CORTADOS.remove(semilla);
                acabar(QteResultado.EXITO);
            } else {
                cortados.add(sel);
                acabar(QteResultado.FALLO);
            }
        }
    }

    @Override
    protected int[] datos() {
        return new int[]{cortado};
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        int anchoTotal = 372, alto = 178;
        float esc = Math.min(1F, (w - 16F) / anchoTotal);
        g.pose().pushPose();
        g.pose().translate(w / 2F + temblorX(), h * 0.48F, 0);
        g.pose().scale(esc, esc, 1F);
        int x0 = -anchoTotal / 2, y0 = -alto / 2;
        modulo(g, x0, y0, 214, alto);
        manual(g, x0 + 222, y0, anchoTotal - 222, alto);
        Component ayuda = Component.translatable("qte.miscelanea.ayuda.cables", nombreTecla(com.miscelanea.client.TeclasMod.MJ_ARRIBA), nombreTecla(com.miscelanea.client.TeclasMod.MJ_ABAJO),
                nombreTecla(com.miscelanea.client.TeclasMod.MJ_ACCION), nombreTecla(mc.options.keyHotbarSlots[0]), nombreTecla(mc.options.keyHotbarSlots[Math.max(0, n - 1)]));
        marco(g, 0, y0, y0 + alto, ayuda);
        g.pose().popPose();
    }

    private void modulo(GuiGraphics g, int x0, int y0, int ancho, int alto) {
        int x1 = x0 + ancho, y1 = y0 + alto;
        Dibujo.panelMetal(g, x0, y0, x1, y1, 0xFF3A3C40);
        // franjas de peligro arriba
        for (int i = 0; i < ancho - 8; i += 8) {
            int c = (i / 8) % 2 == 0 ? 0xFFD8B020 : 0xFF1A1A1A;
            g.fill(x0 + 4 + i, y0 + 3, Math.min(x1 - 4, x0 + 12 + i), y0 + 6, c);
        }
        // placa con el número de serie
        g.fill(x0 + 8, y0 + 10, x0 + 78, y0 + 24, 0xFFC8C2B0);
        g.fill(x0 + 8, y0 + 23, x0 + 78, y0 + 24, 0xFF7A766A);
        g.drawString(mc.font, Component.translatable("qte.miscelanea.cables.serie", serie), x0 + 11, y0 + 13, 0xFF1A1A1A, false);
        // reloj digital
        long quedaMs = (long) (quedaFrac() * tiempo);
        int seg = (int) Math.ceil(quedaMs / 1000.0);
        String reloj = String.format("%d:%02d", seg / 60, seg % 60);
        g.fill(x0 + 86, y0 + 9, x0 + 128, y0 + 25, 0xFF0A0606);
        g.fill(x0 + 86, y0 + 9, x0 + 128, y0 + 10, 0xFF3A2A2A);
        int colReloj = quedaFrac() < 0.25F ? 0xFFFF3A2A : 0xFFE0281E;
        Dibujo.texto(g, mc.font, Component.literal(reloj), x0 + 107, y0 + 13, 1F, colReloj, false);
        // piloto CARGA y LED del pitido
        int px = x1 - 46, py = y0 + 17;
        Dibujo.brillo(g, px, py, 4, piloto ? 4 : 0, piloto ? 0xFFFFB028 : 0xFF4A3A20);
        Dibujo.disco(g, px - 1.2F, py - 1.4F, 1.3F, piloto ? 0xC0FFF0C0 : 0x40FFFFFF);
        g.drawString(mc.font, Component.translatable("qte.miscelanea.cables.piloto"), px + 7, py - 4, 0xFFD8D8D0, false);
        boolean led = !terminado && ms() - ultimoPitido < 90;
        int lx = x1 - 14;
        Dibujo.brillo(g, lx, py, 3.5F, led ? 5 : 0, led ? 0xFFFF2A1A : 0xFF501010);
        // regletas de los bornes
        int ya = y0 + 36, yb = y1 - 14;
        int bx0 = x0 + 26, bx1 = x1 - 22;
        g.fill(bx0 - 8, ya - 6, bx0 + 2, yb + 6, 0xFF1C1C20);
        g.fill(bx1 - 2, ya - 6, bx1 + 8, yb + 6, 0xFF1C1C20);
        float paso = (yb - ya) / (float) Math.max(1, n - 1);
        long t = ms();
        int correcto = terminado && resultado == QteResultado.EXITO ? cortado : -1;
        for (int i = 0; i < n; i++) {
            float y = ya + i * paso;
            boolean elegido = i == sel && !terminado;
            boolean roto = cortados.contains(i) || i == cortado;
            // número del cable
            Dibujo.texto(g, mc.font, Component.literal(String.valueOf(i + 1)), x0 + 12, y - 4, 0.9F, elegido ? 0xFFFFE080 : 0xFF9A9AA0, true);
            // bornes (tornillos)
            for (int bx : new int[]{bx0 - 3, bx1 + 3}) {
                Dibujo.disco(g, bx, y, 3.2F, 0xFF9A8A60);
                Dibujo.linea(g, bx - 2, y, bx + 2, y, 1F, 0xFF4A4030);
            }
            int col = COLOR[colores[i]];
            int borde = colores[i] == QteLogica.NEGRO ? 0xFF6A6A70 : 0xFF0A0A0A;
            if (elegido) {
                float k = 0.5F + 0.5F * Mth.sin(t * 0.012F);
                cable(g, bx0, bx1, y, i, 9F, Dibujo.alfa(0xFFFFE8A0, 0.25F + 0.3F * k), -1, false);
            }
            cable(g, bx0, bx1, y, i, 5.2F, borde, -1, roto);
            cable(g, bx0, bx1, y, i, 3.4F, col, i == correcto ? 0 : -1, roto);
            if (elegido) alicates(g, (bx0 + bx1) / 2F, y + onda(i, 0.5F) - 1, t - cambioSel);
        }
    }

    private static float onda(int i, float u) {
        return Mth.sin(u * (float) Math.PI) * (3F + (i % 3)) + Mth.sin(u * 9F + i * 1.7F) * 1.2F;
    }

    /** Cable de un borne al otro con su comba; si está cortado, dos medias con el cobre a la vista. */
    private void cable(GuiGraphics g, float xa, float xb, float y, int i, float grosor, int col, int marca, boolean roto) {
        int segs = 18;
        float hueco = 0.07F;
        for (int s = 0; s < segs; s++) {
            float u0 = s / (float) segs, u1 = (s + 1) / (float) segs;
            if (roto && u1 > 0.5F - hueco && u0 < 0.5F + hueco) continue;
            float caida = roto ? 4F : 0F;   // las medias cuelgan un poco
            float d0 = roto ? caida * (1F - Math.abs(u0 - 0.5F) * 2F) : 0, d1 = roto ? caida * (1F - Math.abs(u1 - 0.5F) * 2F) : 0;
            Dibujo.linea(g, xa + (xb - xa) * u0, y + onda(i, u0) + d0, xa + (xb - xa) * u1 + 0.4F, y + onda(i, u1) + d1, grosor, col);
        }
        if (roto && grosor < 4F) {
            for (float u : new float[]{0.5F - hueco, 0.5F + hueco}) {
                float x = xa + (xb - xa) * u, yy = y + onda(i, u) + 4F * (1F - Math.abs(u - 0.5F) * 2F);
                Dibujo.disco(g, x, yy, 1.6F, 0xFFD88A40);
            }
        }
    }

    /** Las tenazas abiertas sobre el cable elegido (bajan un poco al cambiar). */
    private void alicates(GuiGraphics g, float cx, float cy, long desde) {
        float baja = Math.max(0F, 1F - desde / 160F) * 6F;
        float y = cy - 4 - baja;
        int metal = 0xFFB8BCC4, mango = 0xFFC0302A;
        Dibujo.linea(g, cx - 5, y - 1, cx - 1, y - 9, 2.4F, metal);
        Dibujo.linea(g, cx + 5, y - 1, cx + 1, y - 9, 2.4F, metal);
        Dibujo.disco(g, cx, y - 10, 2.2F, 0xFF707478);
        Dibujo.linea(g, cx - 1, y - 11, cx - 5, y - 24, 3.6F, mango);
        Dibujo.linea(g, cx + 1, y - 11, cx + 5, y - 24, 3.6F, mango);
    }

    private void manual(GuiGraphics g, int x0, int y0, int ancho, int alto) {
        int x1 = x0 + ancho, y1 = y0 + alto;
        g.fill(x0 + 2, y0 + 2, x1 + 2, y1 + 2, 0x80000000);
        g.fillGradient(x0, y0, x1, y1, 0xFFEDE4C8, 0xFFD8CCA8);
        g.fill(x0, y0, x1, y0 + 1, 0xFFFFF8E0);
        // grapa y doblez
        g.fill(x0 + 6, y0 + 3, x0 + 16, y0 + 5, 0xFF8A8A90);
        Dibujo.triangulo(g, x1 - 10, y1, x1, y1, x1, y1 - 10, 0xFFBDB08C);
        float esc = 0.62F;
        int xi = x0 + 7, anchoTxt = (int) ((ancho - 13) / esc);
        Dibujo.texto(g, mc.font, Component.translatable("qte.miscelanea.cables.manual.titulo", n).withStyle(ChatFormatting.BOLD),
                (x0 + x1) / 2F, y0 + 8, 0.75F, 0xFF3A2410, false);
        g.fill(x0 + 6, y0 + 18, x1 - 6, y0 + 19, 0xFF8A7A5A);
        float y = y0 + 23;
        g.pose().pushPose();
        g.pose().translate(xi, 0, 0);
        g.pose().scale(esc, esc, 1F);
        int reglas = n == 4 ? 5 : 4;
        for (int r = 1; r <= reglas; r++) {
            Component c = Component.translatable("qte.miscelanea.cables.r" + n + "_" + r);
            List<FormattedCharSequence> ls = mc.font.split(Component.literal(r + ". ").append(c), anchoTxt);
            for (FormattedCharSequence l : ls) {
                g.drawString(mc.font, l, 0, (int) (y / esc), 0xFF241A10, false);
                y += 9 * esc;
            }
            y += 2.5F;
        }
        y += 2;
        List<FormattedCharSequence> ls = mc.font.split(Component.translatable("qte.miscelanea.cables.r_piloto").withStyle(ChatFormatting.ITALIC), anchoTxt);
        for (FormattedCharSequence l : ls) {
            g.drawString(mc.font, l, 0, (int) (y / esc), 0xFF7A2A10, false);
            y += 9 * esc;
        }
        y += 2.5F;
        ls = mc.font.split(Component.translatable("qte.miscelanea.cables.r_orden"), anchoTxt);
        for (FormattedCharSequence l : ls) {
            g.drawString(mc.font, l, 0, (int) (y / esc), 0xFF5A4A30, false);
            y += 9 * esc;
        }
        g.pose().popPose();
        // leyenda de colores abajo
        int lx = x0 + 7, ly = y1 - 11;
        for (int i = 0; i < 5; i++) {
            g.fill(lx + i * 26, ly, lx + i * 26 + 7, ly + 5, COLOR[i]);
            g.fill(lx + i * 26, ly + 5, lx + i * 26 + 7, ly + 6, 0xFF000000);
            g.pose().pushPose();
            g.pose().translate(lx + i * 26 + 8.5F, ly - 0.5F, 0);
            g.pose().scale(0.5F, 0.5F, 1F);
            g.drawString(mc.font, Component.translatable("qte.miscelanea.cables.color." + NOMBRE[i]), 0, 1, 0xFF3A2A18, false);
            g.pose().popPose();
        }
    }
}
