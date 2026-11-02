package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.aparatos.AparatoAccionPacket;
import com.miscelanea.tortura.aparatos.AparatosRed;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PULSO (tras cada descarga de la silla): el electrocardiograma corre de derecha a izquierda; pulsa SALTAR justo cuando
 * el pico de cada latido pasa por la línea de lectura. Cada latido perdido (o pulsar sin latido) te quita consciencia;
 * si llega a cero, te desmayas (el servidor lo sabe por DESMAYO_PULSO). El corazón va desbocado al principio y se calma.
 */
public class JuegoPulso {
    private static final float MS_ANCHO = 2600F;   // lo que cabe en la pantalla
    final BlockPos pos;
    private final Random r;
    private final float dif;
    private final long inicio = Util.getMillis();
    private final long duracion;
    private final List<long[]> latidos = new ArrayList<>();   // {ms, estado: 0 pendiente, 1 bien, 2 fallado}
    private long proximo;
    private float consciencia = 1F, conscienciaVis = 1F;
    private final int ventana;
    boolean terminado, desmayado;
    private long juicioT = -10000;
    private boolean juicioBien;

    JuegoPulso(BlockPos pos, long semilla, float dif) {
        this.pos = pos;
        this.r = new Random(semilla);
        this.dif = dif;
        this.duracion = 7000 + (long) (dif * 6000);
        this.ventana = Math.round(170 - 70 * dif);
        this.proximo = 900;
    }

    private long ms() {
        return Util.getMillis() - inicio;
    }

    private float bpm(long t) {
        float k = Mth.clamp(t / (float) duracion, 0F, 1F);
        return Mth.lerp(k, 95F + 75F * dif, 72F);
    }

    void tick(Minecraft mc) {
        if (terminado) return;
        long t = ms();
        // programar los latidos que van a aparecer por la derecha
        while (proximo < t + MS_ANCHO && proximo < duracion) {
            latidos.add(new long[]{proximo, 0});
            float paso = 60000F / bpm(proximo) * (0.9F + r.nextFloat() * 0.2F);
            if (r.nextFloat() < 0.08F + dif * 0.12F) paso *= 0.6F;   // extrasístole
            proximo += (long) paso;
        }
        for (long[] l : latidos) {
            if (l[1] == 0 && t > l[0] + ventana) {
                l[1] = 2;
                fallo(mc, 0.22F);
            }
            // el latido se oye al pasar por la línea
            if (l[1] != 2 && Math.abs(t - l[0]) < 25) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.TRAMPA_LATIDO.get(), 1F + (bpm(t) - 72F) / 200F, 0.6F));
            }
        }
        latidos.removeIf(l -> l[0] < t - MS_ANCHO * 0.4F);
        conscienciaVis += (consciencia - conscienciaVis) * 0.3F;
        if (consciencia <= 0F && !desmayado) {
            desmayado = true;
            terminado = true;
            AparatosRed.CANAL.sendToServer(new AparatoAccionPacket(AparatoAccionPacket.DESMAYO_PULSO, pos, 0));
        } else if (t > duracion + 600) {
            terminado = true;
        }
    }

    void pulsar(Minecraft mc) {
        if (terminado) return;
        long t = ms();
        long[] mejor = null;
        long dmin = Long.MAX_VALUE;
        for (long[] l : latidos) {
            if (l[1] != 0) continue;
            long d = Math.abs(t - l[0]);
            if (d <= ventana && d < dmin) {
                dmin = d;
                mejor = l;
            }
        }
        if (mejor == null) {
            fallo(mc, 0.08F);
            return;
        }
        mejor[1] = 1;
        consciencia = Math.min(1F, consciencia + 0.08F);
        juicioT = t;
        juicioBien = true;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.QTE_PULSAR.get(), 1.3F, 0.7F));
    }

    private void fallo(Minecraft mc, float cuanto) {
        consciencia = Math.max(0F, consciencia - cuanto);
        juicioT = ms();
        juicioBien = false;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.QTE_FALLO.get(), 1.2F, 0.4F));
    }

    /** Forma del electrocardiograma (onda P, QRS, onda T) a 'd' ms del pico; 1 = pico. */
    private static float onda(float d) {
        float y = 0;
        y += 0.12F * (float) Math.exp(-Math.pow((d + 170) / 35F, 2));   // P
        y -= 0.18F * (float) Math.exp(-Math.pow((d + 28) / 10F, 2));    // Q
        y += 1.00F * (float) Math.exp(-Math.pow(d / 11F, 2));           // R
        y -= 0.30F * (float) Math.exp(-Math.pow((d - 26) / 11F, 2));    // S
        y += 0.22F * (float) Math.exp(-Math.pow((d - 200) / 55F, 2));   // T
        return y;
    }

    void dibujar(Minecraft mc, GuiGraphics g, int w, int h, float pt) {
        int ancho = 240, alto = 70;
        int x0 = (w - ancho) / 2, y0 = h - alto - 64;
        Dibujo.panelMetal(g, x0 - 8, y0 - 22, x0 + ancho + 8, y0 + alto + 18, 0xFF1C2420);
        g.fillGradient(x0, y0, x0 + ancho, y0 + alto, 0xFF041A0C, 0xFF02100A);
        for (int gx = x0; gx < x0 + ancho; gx += 12) g.fill(gx, y0, gx + 1, y0 + alto, 0x1A40FF80);
        for (int gy = y0; gy < y0 + alto; gy += 12) g.fill(x0, gy, x0 + ancho, gy + 1, 0x1A40FF80);
        long t = ms();
        float lx = x0 + ancho * 0.3F;     // línea de lectura (los latidos llegan por la derecha)
        g.fill((int) lx - ventana * ancho / (int) MS_ANCHO, y0, (int) lx + ventana * ancho / (int) MS_ANCHO, y0 + alto, 0x2060FFA0);
        g.fill((int) lx, y0, (int) lx + 1, y0 + alto, 0xA0B0FFC8);
        // la traza
        float base = y0 + alto * 0.68F, amp = alto * 0.55F;
        float px = -1, py = -1;
        for (int i = 0; i <= ancho; i += 2) {
            float tm = t + (i - ancho * 0.3F) / ancho * MS_ANCHO;
            float y = 0;
            for (long[] l : latidos) {
                float d = tm - l[0];
                if (d > -260 && d < 320) y += onda(d) * (l[1] == 2 ? 0.55F : 1F);
            }
            y += (r.nextFloat() - 0.5F) * 0.015F;
            float sx = x0 + i, sy = base - y * amp;
            if (px >= 0) {
                Dibujo.linea(g, px, py, sx, sy, 2.6F, 0x3040FF80);
                Dibujo.linea(g, px, py, sx, sy, 1.2F, 0xFF70FFA0);
            }
            px = sx;
            py = sy;
        }
        // consciencia
        float c = conscienciaVis;
        int colC = c > 0.6F ? 0xFF60E070 : c > 0.3F ? 0xFFE8C040 : 0xFFE84030;
        Dibujo.barra(g, x0, y0 + alto + 6, ancho, 5, c, Dibujo.mezcla(colC, 0xFF000000, 0.3F), colC);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.silla.pulso").withStyle(ChatFormatting.BOLD), x0 + ancho / 2F, y0 - 16,
                1F, 0xFFC8FFD8, true);
        Dibujo.texto(g, mc.font, Component.literal(Math.round(bpm(t)) + " LPM"), x0 + ancho - 22, y0 + 4, 0.75F, 0xFF90FFB0, true);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.silla.pulso_ayuda", mc.options.keyJump.getTranslatedKeyMessage()),
                x0 + ancho / 2F, y0 + alto + 14, 0.7F, 0xFFB0C0B8, true);
        float k = Math.max(0F, 1F - (t - juicioT) / 400F);
        if (k > 0.05F) {
            Dibujo.texto(g, mc.font, Component.translatable(juicioBien ? "qte.miscelanea.ritmo.bien" : "qte.miscelanea.ritmo.fallo").withStyle(ChatFormatting.BOLD),
                    lx, y0 + 4, 1F, Dibujo.alfa(juicioBien ? 0xFF80F0A0 : 0xFFFF5050, k), true);
        }
        // al perder consciencia se oscurece el borde
        if (c < 0.5F) g.fill(0, 0, w, h, ((int) ((0.5F - c) * 2 * 120) << 24));
    }
}
