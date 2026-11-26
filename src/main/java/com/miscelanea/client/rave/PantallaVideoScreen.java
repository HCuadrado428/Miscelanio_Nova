package com.miscelanea.client.rave;

import com.miscelanea.client.movil.MovilCliente;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.rave.RaveRed;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Elegir qué vídeo corto de la galería del móvil enseña la pantalla de vídeo (o quitarlo). */
public class PantallaVideoScreen extends Screen {
    private static final int W = 260, H = 190, FILA = 14, FILAS = 9;
    private final BlockPos pos;
    private int x0, y0, scroll, elegido = -1;

    public static void abrir(BlockPos p) {
        Minecraft.getInstance().setScreen(new PantallaVideoScreen(p));
    }

    public PantallaVideoScreen(BlockPos pos) {
        super(Component.translatable("block.miscelanea.pantalla_video"));
        this.pos = pos;
    }

    private static List<MovilCliente.Foto> videos() {
        List<MovilCliente.Foto> l = new ArrayList<>();
        for (MovilCliente.Foto f : MovilCliente.FOTOS) if (f.ms() > 0) l.add(f);
        return l;
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = Math.max(4, (height - H) / 2);
        if (!MovilCliente.fotosCargadas) MovilRed.alServidor(MovilRed.C_FOTOS, null);
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.pantalla.poner"), b -> {
            List<MovilCliente.Foto> l = videos();
            if (elegido >= 0 && elegido < l.size()) {
                RaveRed.CANAL.sendToServer(new RaveRed.PonerVideo(pos, l.get(elegido).id()));
                onClose();
            }
        }).bounds(x0 + 8, y0 + H - 28, 118, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.pantalla.quitar"), b -> {
            RaveRed.CANAL.sendToServer(new RaveRed.PonerVideo(pos, -1));
            onClose();
        }).bounds(x0 + W - 126, y0 + H - 28, 118, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF4FD8FF);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0101018);
        g.drawString(font, Component.translatable("block.miscelanea.pantalla_video").withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 6, 0xFFFF3CF0);
        List<MovilCliente.Foto> l = videos();
        int lx = x0 + 8, ly = y0 + 22, lw = W - 16;
        g.fill(lx - 2, ly - 2, lx + lw + 2, ly + FILAS * FILA + 2, 0xFF05050A);
        scroll = Mth.clamp(scroll, 0, Math.max(0, l.size() - FILAS));
        SimpleDateFormat fecha = new SimpleDateFormat("dd/MM HH:mm");
        for (int i = 0; i < FILAS && scroll + i < l.size(); i++) {
            int k = scroll + i, y = ly + i * FILA;
            MovilCliente.Foto f = l.get(k);
            if (k == elegido) g.fill(lx, y, lx + lw, y + FILA, 0xFF3A1A6A);
            else if (mx >= lx && mx < lx + lw && my >= y && my < y + FILA) g.fill(lx, y, lx + lw, y + FILA, 0xFF22222E);
            g.drawString(font, Component.translatable("rave.miscelanea.pantalla.video", fecha.format(new Date(f.t())), (f.ms() + 500) / 1000), lx + 4, y + 3, 0xFFE8E8F0, false);
        }
        if (l.isEmpty())
            for (var linea : font.split(Component.translatable("rave.miscelanea.pantalla.sin_videos"), lw - 8))
                g.drawString(font, linea, lx + 4, ly + 4 + 10 * 0, 0xFF9AA0B0, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = x0 + 8, ly = y0 + 22, lw = W - 16;
        if (mx >= lx && mx < lx + lw && my >= ly && my < ly + FILAS * FILA) {
            int k = scroll + (int) ((my - ly) / FILA);
            if (k < videos().size()) {
                elegido = k;
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        scroll -= (int) Math.signum(d);
        return true;
    }
}
