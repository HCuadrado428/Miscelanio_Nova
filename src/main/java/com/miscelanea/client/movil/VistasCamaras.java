package com.miscelanea.client.movil;

import com.miscelanea.client.camaras.CamarasCliente;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.time.LocalTime;
import java.util.List;

/**
 * App «Cámaras» del móvil: la imagen en directo de las cámaras de vigilancia (el mismo reparto de señal que las teles), con el
 * móvil girado para verla grande, en gris verdoso, con líneas de barrido, el nombre de la cámara, «REC» y la hora. Tocar ◄ ► (o
 * usar las flechas) cambia de cámara entre las que existen.
 */
final class VistasCamaras {
    private VistasCamaras() {}

    static final class Camaras extends Vista {
        private int canal = 1;

        @Override
        void abierta() {
            List<Integer> l = CamarasCliente.lista();
            canal = l.isEmpty() ? 1 : l.get(0);
        }

        @Override
        boolean horizontal() {
            return true;
        }

        @Override
        int fondoBarra() {
            return 0xFF000000;
        }

        private void cambiar(int sentido) {
            List<Integer> l = CamarasCliente.lista();
            if (l.isEmpty()) {
                canal = Math.max(1, Math.min(99, canal + sentido));
                return;
            }
            int i = l.indexOf(canal);
            if (i < 0) i = sentido > 0 ? -1 : 0;
            canal = l.get(Math.floorMod(i + sentido, l.size()));
        }

        @Override
        void dibujar(GuiGraphics g, int mx, int my, float pt) {
            CamarasCliente.visto(canal);
            CamarasCliente.pedirLista();      // la lista de cámaras (para ◄ ► y los nombres)
            Ui.rect(g, 0, 0, HW, HH, 0xFF050807);
            int w = HW, h = w * 9 / 16, x = 0, y = (HH - h) / 2;
            boolean senal = CamarasCliente.hayEnvio(canal);
            ResourceLocation rl = senal ? CamarasCliente.senal(canal).rl : CamarasCliente.estatica();
            int tw = 128, th = 72;
            if (senal && CamarasCliente.senal(canal).tex.getPixels() != null) {
                tw = CamarasCliente.senal(canal).tex.getPixels().getWidth();
                th = CamarasCliente.senal(canal).tex.getPixels().getHeight();
            }
            RenderSystem.setShaderColor(0.80F, 0.96F, 0.88F, 1.0F);      // gris verdoso de laboratorio
            g.blit(rl, x, y, w, h, 0, 0, tw, th, tw, th);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (int yy = y; yy < y + h; yy += 3) Ui.rect(g, x, yy, w, 1, 0x22000000);
            g.fillGradient(x, y, x + w, y + 14, 0x99000000, 0x00000000);
            g.fillGradient(x, y + h - 22, x + w, y + h, 0x00000000, 0xAA000000);

            // rótulos
            String nombre = CamarasCliente.nombre(canal);
            String cab = String.format("CAM %02d", canal) + (nombre.isEmpty() ? "" : " " + nombre.toUpperCase(java.util.Locale.ROOT));
            g.drawString(Ui.font(), Component.literal(Ui.cortar(cab, 120)).withStyle(ChatFormatting.BOLD), 24, y + 5, 0xFFD8F5E4, true);
            if (senal) {
                boolean punto = (Util.getMillis() / 500) % 2 == 0;
                g.drawString(Ui.font(), Component.literal("●").withStyle(ChatFormatting.BOLD), HW - 36, y + 5, punto ? 0xFFFF3030 : 0xFF601010, true);
                g.drawString(Ui.font(), Component.literal("REC").withStyle(ChatFormatting.BOLD), HW - 26, y + 5, 0xFFD8F5E4, true);
            } else {
                g.drawCenteredString(Ui.font(), Component.translatable("camaras.miscelanea.vista.sin_senal").withStyle(ChatFormatting.BOLD), HW / 2, y + h / 2 - 4, 0xFFFFFFFF);
            }
            LocalTime t = LocalTime.now();
            g.drawString(Ui.font(), String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()), 6, y + h - 12, 0xFFD8F5E4, true);

            // botones: atrás arriba a la izquierda (como en las fotos horizontales) y ◄ ► abajo
            Ui.circulo(g, 10, 10, 8, 0x90000000);
            Ui.glifo(g, Ui.G_ATRAS, 5, 5, Ui.dentro(mx, my, 0, 0, 18, 18) ? 0xFFFFFFFF : 0xFFCCCCCC);
            int by = HH - 14;
            Ui.circulo(g, HW / 2 - 30, by, 8, Ui.dentro(mx, my, HW / 2 - 38, by - 8, 16, 16) ? 0xC0333F3A : 0x90000000);
            Ui.glifo(g, Ui.G_ATRAS, HW / 2 - 34, by - 4, 0xFFFFFFFF);
            Ui.circulo(g, HW / 2 + 30, by, 8, Ui.dentro(mx, my, HW / 2 + 22, by - 8, 16, 16) ? 0xC0333F3A : 0x90000000);
            Ui.glifo(g, Ui.G_DERECHA, HW / 2 + 26, by - 4, 0xFFFFFFFF);
            Ui.centrado(g, String.format("%02d", canal), HW / 2, by - 4, 0xFFD8F5E4);
        }

        @Override
        boolean clic(double x, double y) {
            if (Ui.dentro(x, y, 0, 0, 18, 18)) {
                MovilCliente.clic();
                m.atras();
                return true;
            }
            int by = HH - 14;
            if (Ui.dentro(x, y, HW / 2 - 38, by - 8, 16, 16)) {
                MovilCliente.clic();
                cambiar(-1);
                return true;
            }
            if (Ui.dentro(x, y, HW / 2 + 22, by - 8, 16, 16)) {
                MovilCliente.clic();
                cambiar(1);
                return true;
            }
            return false;
        }

        @Override
        boolean tecla(int key, int scan, int mods) {
            if (key == 263) {
                cambiar(-1);
                return true;
            }
            if (key == 262) {
                cambiar(1);
                return true;
            }
            return false;
        }
    }
}
