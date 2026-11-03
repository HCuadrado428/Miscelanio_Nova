package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Electrocardiograma del monitor de la silla de interrogatorio: la forma del latido (como en JuegoPulso), la traza a
 * partir de los latidos que apunta el BlockEntity en el cliente y su versión de HUD (la del mundo la dibuja
 * {@link SillaInterrogatorioRenderer}).
 */
public final class MonitorEcg {
    private MonitorEcg() {}

    /** Ticks que caben en la pantalla. */
    public static final float VENTANA = 50F;

    /** Forma del electrocardiograma (onda P, QRS, onda T) a 'd' ms del pico; 1 = pico. */
    public static float onda(float d) {
        float y = 0;
        y += 0.12F * (float) Math.exp(-Math.pow((d + 170) / 35F, 2));
        y -= 0.18F * (float) Math.exp(-Math.pow((d + 28) / 10F, 2));
        y += 1.00F * (float) Math.exp(-Math.pow(d / 11F, 2));
        y -= 0.30F * (float) Math.exp(-Math.pow((d - 26) / 11F, 2));
        y += 0.22F * (float) Math.exp(-Math.pow((d - 200) / 55F, 2));
        return y;
    }

    /** Valor de la traza en el tick 't' (reloj del cliente del BlockEntity). */
    public static float valor(SillaInterrogatorioBlockEntity be, float t) {
        float y = 0;
        for (float l : be.latidosT) {
            float d = (t - l) * 50F;
            if (d > -260 && d < 320) y += onda(d);
        }
        // ruido de la línea (más con el corazón parado: la línea "tiembla")
        y += Mth.sin(t * 7.3F) * 0.008F + Mth.sin(t * 13.1F + 1F) * 0.006F;
        return y;
    }

    public static int color(float bpm, boolean paro) {
        if (paro) return 0xFFFF4038;
        return bpm >= SillaInterrogatorioBlockEntity.RIESGO ? 0xFFFF6040 : bpm >= 130 ? 0xFFF0D040 : 0xFF60FF90;
    }

    /** El monitor en el HUD (el interrogador con el maletín cerca de la silla). */
    public static void dibujarGui(Minecraft mc, GuiGraphics g, int x0, int y0, int ancho, int alto, SillaInterrogatorioBlockEntity be, float pt) {
        boolean paro = be.paroCli > 0;
        float bpm = Mth.lerp(pt, be.bpmVisO, be.bpmVis);
        int col = color(bpm, paro);
        Dibujo.panelMetal(g, x0 - 6, y0 - 16, x0 + ancho + 6, y0 + alto + 6, 0xFF1C2420);
        g.fillGradient(x0, y0, x0 + ancho, y0 + alto, 0xFF041A0C, 0xFF02100A);
        for (int gx = x0; gx < x0 + ancho; gx += 10) g.fill(gx, y0, gx + 1, y0 + alto, 0x1A40FF80);
        for (int gy = y0; gy < y0 + alto; gy += 10) g.fill(x0, gy, x0 + ancho, gy + 1, 0x1A40FF80);
        float ahora = be.reloj + pt;
        float base = y0 + alto * 0.7F, amp = alto * 0.55F;
        float px = -1, py = -1;
        for (int i = 0; i <= ancho; i += 2) {
            float t = ahora - (1F - i / (float) ancho) * VENTANA;
            float y = paro ? Mth.sin(t * 9.1F) * 0.01F : valor(be, t);
            float sx = x0 + i, sy = base - y * amp;
            if (px >= 0) {
                Dibujo.linea(g, px, py, sx, sy, 2.6F, Dibujo.alfa(col, 0.2F));
                Dibujo.linea(g, px, py, sx, sy, 1.2F, col);
            }
            px = sx;
            py = sy;
        }
        // cabeza de la traza (brilla)
        Dibujo.disco(g, px, py, 1.8F, 0xFFE8FFF0);
        Component titulo = Component.translatable("hud.miscelanea.interrogatorio.monitor").withStyle(ChatFormatting.BOLD);
        Dibujo.texto(g, mc.font, titulo, x0 + ancho / 2F, y0 - 12, 0.75F, 0xFFC8FFD8, true);
        String lpm = paro ? "---" : String.valueOf(Math.round(bpm));
        boolean parpadeo = paro && (be.reloj / 5) % 2 == 0;
        Dibujo.texto(g, mc.font, Component.literal(lpm + " LPM"), x0 + ancho - 24, y0 + 3, 0.85F, parpadeo ? 0xFFFFFFFF : col, true);
    }
}
