package com.miscelanea.client.rave;

import com.miscelanea.rave.MesaDjBlockEntity;
import com.miscelanea.rave.Pistas;
import com.miscelanea.rave.RaveRed;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Pantalla de la mesa de DJ: lista de canciones (las de la pista de baile y los discos), poner y parar, volumen, repetir y
 * un botón para encender o apagar todas las luces de la rave de alrededor. Muestra lo que suena, cuánto falta y el alcance.
 */
public class MesaDjScreen extends Screen {
    private static final int W = 320, H = 250, FILA = 13, FILAS = 10;
    private Component error;
    private final BlockPos pos;
    private int x0, y0, scroll, elegida = -1;

    public static void abrir(BlockPos p) {
        Minecraft.getInstance().setScreen(new MesaDjScreen(p));
    }

    public MesaDjScreen(BlockPos pos) {
        super(Component.translatable("block.miscelanea.mesa_dj"));
        this.pos = pos;
    }

    private MesaDjBlockEntity mesa() {
        var l = Minecraft.getInstance().level;
        return l != null && l.getBlockEntity(pos) instanceof MesaDjBlockEntity m ? m : null;
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        MesaDjBlockEntity m = mesa();
        List<Pistas.Pista> l = Pistas.todas();
        if (m != null) for (int i = 0; i < l.size(); i++) if (l.get(i).id().equals(m.pista())) elegida = i;
        int bx = x0 + 186;
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.poner"), b -> {
            if (elegida >= 0 && elegida < l.size()) RaveRed.dj(pos, RaveRed.DJ_PONER, l.get(elegida).id(), 0);
        }).bounds(bx, y0 + 58, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.parar"), b -> RaveRed.dj(pos, RaveRed.DJ_PARAR, "", 0))
                .bounds(bx + 64, y0 + 58, 60, 20).build());
        addRenderableWidget(new AbstractSliderButton(bx, y0 + 84, 124, 20, Component.empty(), (m == null ? 80 : m.volumen()) / 100.0) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("rave.miscelanea.dj.volumen", (int) Math.round(value * 100)));
            }

            @Override
            protected void applyValue() {
                RaveRed.dj(pos, RaveRed.DJ_VOLUMEN, "", (int) Math.round(value * 100));
            }
        });
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.bucle"), b -> {
            MesaDjBlockEntity mm = mesa();
            RaveRed.dj(pos, RaveRed.DJ_BUCLE, "", mm != null && mm.bucle() ? 0 : 1);
        }).bounds(bx, y0 + 110, 124, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.luces_on"), b -> RaveRed.dj(pos, RaveRed.DJ_LUCES_ON, "", 0))
                .bounds(bx, y0 + 136, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.luces_off"), b -> RaveRed.dj(pos, RaveRed.DJ_LUCES_OFF, "", 0))
                .bounds(bx + 64, y0 + 136, 60, 20).build());
        // audio del ordenador (Spotify o lo que suene) por la mesa y sus altavoces
        int ay = y0 + 196;
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.entrada", CapturaPc.nombreEntrada()), b -> {
            CapturaPc.siguienteEntrada();
            b.setMessage(Component.translatable("rave.miscelanea.dj.entrada", CapturaPc.nombreEntrada()));
        }).bounds(x0 + 8, ay, 170, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(CapturaPc.emitiendo() ? "rave.miscelanea.dj.emitir_parar" : "rave.miscelanea.dj.emitir"), b -> {
            if (CapturaPc.emitiendo()) {
                CapturaPc.parar();
                error = null;
            } else {
                String e = CapturaPc.empezar(pos);
                error = e == null ? null : Component.translatable(e);
            }
            b.setMessage(Component.translatable(CapturaPc.emitiendo() ? "rave.miscelanea.dj.emitir_parar" : "rave.miscelanea.dj.emitir"));
        }).bounds(bx, ay, 124, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // panel: negro con borde de neón
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFFFF3CF0);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0101018);
        g.fill(x0, y0, x0 + W, y0 + 18, 0xFF1C1030);
        g.drawString(font, Component.translatable("block.miscelanea.mesa_dj").withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 5, 0xFF4FD8FF);
        // lista
        List<Pistas.Pista> l = Pistas.todas();
        int lx = x0 + 8, ly = y0 + 24, lw = 170;
        g.fill(lx - 2, ly - 2, lx + lw + 2, ly + FILAS * FILA + 2, 0xFF05050A);
        scroll = Mth.clamp(scroll, 0, Math.max(0, l.size() - FILAS));
        MesaDjBlockEntity m = mesa();
        for (int i = 0; i < FILAS && scroll + i < l.size(); i++) {
            int k = scroll + i, y = ly + i * FILA;
            boolean sobre = mx >= lx && mx < lx + lw && my >= y && my < y + FILA;
            boolean suena = m != null && m.sonando() && l.get(k).id().equals(m.pista());
            if (k == elegida) g.fill(lx, y, lx + lw, y + FILA, 0xFF3A1A6A);
            else if (sobre) g.fill(lx, y, lx + lw, y + FILA, 0xFF22222E);
            String t = (suena ? "♪ " : "") + l.get(k).titulo().getString();
            g.drawString(font, font.plainSubstrByWidth(t, lw - 6), lx + 3, y + 3, suena ? 0xFF7CFF3A : 0xFFE8E8F0, false);
        }
        if (l.isEmpty()) g.drawString(font, Component.translatable("rave.miscelanea.dj.vacia"), lx + 3, ly + 3, 0xFF888899, false);
        // estado
        int sx = x0 + 186;
        Component estado;
        if (m != null && m.sonando()) {
            Pistas.Pista pi = Pistas.de(m.pista());
            long pasados = Minecraft.getInstance().level.getGameTime() - m.inicio();
            int quedan = pi == null ? 0 : (int) Math.max(0, (pi.ticks() - pasados) / 20);
            estado = Component.translatable("rave.miscelanea.dj.sonando", pi == null ? "?" : pi.titulo().getString(), quedan / 60, String.format("%02d", quedan % 60));
        } else estado = Component.translatable("rave.miscelanea.dj.parada");
        int yy = y0 + 24;
        for (var linea : font.split(estado, 124)) {
            g.drawString(font, linea, sx, yy, 0xFFFFF23A, false);
            yy += 10;
        }
        if (m != null) {
            g.drawString(font, Component.translatable("rave.miscelanea.dj.alcance", m.alcance(), m.altavoces()), sx, y0 + 162, 0xFF9AA0B0, false);
            g.drawString(font, Component.translatable(m.bucle() ? "rave.miscelanea.dj.bucle_si" : "rave.miscelanea.dj.bucle_no"), sx, y0 + 174, 0xFF9AA0B0, false);
        }
        // emisión del ordenador
        int ay = y0 + 184;
        g.fill(x0 + 6, ay - 4, x0 + W - 6, ay - 3, 0xFF3A1A6A);
        Component info = m != null && m.emisor() != null ? Component.translatable("rave.miscelanea.dj.emite", m.emisorNombre())
                : Component.translatable("rave.miscelanea.dj.emitir_info");
        g.drawString(font, font.plainSubstrByWidth(info.getString(), W - 16), x0 + 8, ay, 0xFF9AA0B0, false);
        if (CapturaPc.emitiendo()) {
            int w = (int) (CapturaPc.nivel() * 120);
            g.fill(x0 + 186, y0 + 220, x0 + 186 + 124, y0 + 224, 0xFF22222E);
            g.fill(x0 + 186, y0 + 220, x0 + 186 + w, y0 + 224, 0xFF7CFF3A);
        }
        if (error != null) g.drawString(font, font.plainSubstrByWidth(error.getString(), W - 16), x0 + 8, y0 + 230, 0xFFFF5A5A, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = x0 + 8, ly = y0 + 24, lw = 170;
        if (mx >= lx && mx < lx + lw && my >= ly && my < ly + FILAS * FILA) {
            int k = scroll + (int) ((my - ly) / FILA);
            if (k < Pistas.todas().size()) {
                if (k == elegida) RaveRed.dj(pos, RaveRed.DJ_PONER, Pistas.todas().get(k).id(), 0);   // doble clic: ponerla ya
                elegida = k;
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
