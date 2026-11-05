package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Random;

/** Mantener el objetivo dentro de la zona (pesca de Stardew Valley): SALTAR sube la zona, si no cae. */
public class JuegoBarraZona extends JuegoQte {
    private final Random r;
    private float obj = 0.5F, objO = 0.5F, objMeta = 0.5F, objVel;
    private float zona = 0.1F, zonaO = 0.1F, zonaVel;
    private float progreso = QteLogica.BARRA_INICIO, progresoO = QteLogica.BARRA_INICIO;
    private boolean dentro;

    public JuegoBarraZona(QteInicioPacket p) {
        super(p);
        r = new Random(semilla);
        sonido(TorturaRegistro.QTE_TIC.get(), 0.8F, 1F);
    }

    @Override
    protected void logica() {
        objO = obj;
        zonaO = zona;
        progresoO = progreso;
        if (ms() < QteLogica.BARRA_PREVIO_MS) return;
        if (ms() > QteLogica.BARRA_PREVIO_MS + QteLogica.BARRA_TIEMPO_MS) {
            acabar(QteResultado.FALLO);
            return;
        }
        if (r.nextFloat() < 0.04F + dif * 0.05F) objMeta = 0.05F + r.nextFloat() * 0.9F;
        objVel += (objMeta - obj) * (0.012F + dif * 0.02F);
        objVel *= 0.84F;
        obj = Mth.clamp(obj + objVel, 0.02F, 0.98F);
        boolean subir = com.miscelanea.client.TeclasMod.accionPulsada();
        zonaVel += subir ? 0.012F : -0.010F;
        zonaVel *= 0.9F;
        zona += zonaVel;
        float tz = QteLogica.barraZona(dif);
        if (zona < 0) {
            zona = 0;
            zonaVel = Math.max(0, -zonaVel * 0.3F);
        }
        if (zona > 1 - tz) {
            zona = 1 - tz;
            zonaVel = Math.min(0, -zonaVel * 0.3F);
        }
        dentro = obj >= zona && obj <= zona + tz;
        progreso = Mth.clamp(progreso + (dentro ? QteLogica.BARRA_GANA : -QteLogica.barraPierde(dif)), 0, 1);
        if (dentro && ticks % 5 == 0) sonido(TorturaRegistro.QTE_PULSAR.get(), 1.2F + progreso * 0.6F, 0.25F);
        if (progreso >= 1) acabar(QteResultado.EXITO);
        else if (progreso <= 0) acabar(QteResultado.FALLO);
    }

    @Override
    protected int[] datos() {
        return new int[0];
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        int alto = 130, ancho = 20;
        int x = (int) (w / 2F + 50 + temblorX()), y = (h - alto) / 2;
        Dibujo.panel(g, x - 8, y - 8, x + ancho + 26, y + alto + 8, 0xFF40C0E0);
        // fondo del carril
        g.fillGradient(x, y, x + ancho, y + alto, 0xFF0E2A3A, 0xFF1C5A6E);
        for (int i = 0; i < alto; i += 6) g.fill(x, y + i, x + ancho, y + i + 1, 0x18FFFFFF);
        float tz = QteLogica.barraZona(dif);
        float z = Mth.lerp(pt, zonaO, zona);
        int zy1 = y + alto - (int) ((z + tz) * alto), zy2 = y + alto - (int) (z * alto);
        int cz = dentro ? 0xB060F078 : 0x9050B060;
        g.fillGradient(x + 1, zy1, x + ancho - 1, zy2, cz, Dibujo.mezcla(cz, 0xFF000000, 0.4F));
        g.fill(x + 1, zy1, x + ancho - 1, zy1 + 1, 0xFFC8FFD0);
        g.fill(x + 1, zy2 - 1, x + ancho - 1, zy2, 0xFF2A6030);
        // objetivo: rombo dorado con halo
        float o = Mth.lerp(pt, objO, obj);
        float oy = y + alto - o * alto, ox = x + ancho / 2F;
        Dibujo.brillo(g, ox, oy, 0.1F, 7, dentro ? 0x90FFE070 : 0x40FFE070);
        Dibujo.triangulo(g, ox, oy - 6, ox + 5, oy, ox - 5, oy, 0xFFFFD040);
        Dibujo.triangulo(g, ox - 5, oy, ox + 5, oy, ox, oy + 6, 0xFFC08A20);
        // progreso (vertical a la derecha)
        float pr = Mth.lerp(pt, progresoO, progreso);
        int bx = x + ancho + 8;
        g.fill(bx - 1, y - 1, bx + 9, y + alto + 1, 0xFF000000);
        g.fill(bx, y, bx + 8, y + alto, 0xFF1C1C20);
        int col = pr > 0.66F ? 0xFF50E060 : pr > 0.33F ? 0xFFE8C040 : 0xFFE85030;
        g.fillGradient(bx, y + alto - (int) (pr * alto), bx + 8, y + alto, Dibujo.mezcla(col, 0xFFFFFFFF, 0.25F), col);
        marco(g, x + ancho / 2F + 13, y - 8, y + alto + 8, Component.translatable("qte.miscelanea.ayuda.barra", nombreTecla(com.miscelanea.client.TeclasMod.MJ_ACCION)));
    }
}
