package com.miscelanea.client.laboratorio;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** La pantalla del cuaderno de laboratorio: una ficha por página, con sus lecturas. */
public class CuadernoScreen extends Screen {
    private static final int ANCHO = 220, ALTO = 150;
    private final ListTag fichas;
    private int pagina;

    public CuadernoScreen(ListTag fichas) {
        super(Component.translatable("item.miscelanea.cuaderno_laboratorio"));
        this.fichas = fichas;
        this.pagina = Math.max(0, fichas.size() - 1);
    }

    @Override
    protected void init() {
        int x = (width - ANCHO) / 2, y = (height - ALTO) / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> pagina = Math.max(0, pagina - 1)).bounds(x, y + ALTO + 6, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> pagina = Math.min(Math.max(0, fichas.size() - 1), pagina + 1)).bounds(x + ANCHO - 30, y + ALTO + 6, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 60, y + ALTO + 6, ANCHO - 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = (width - ANCHO) / 2, y = (height - ALTO) / 2;
        g.fill(x - 2, y - 2, x + ANCHO + 2, y + ALTO + 2, 0xFF1C503A);
        g.fill(x, y, x + ANCHO, y + ALTO, 0xFFF0ECDF);
        g.drawString(font, title, x + 8, y + 8, 0xFF1C503A, false);
        if (fichas.isEmpty()) {
            int ty = y + 30;
            for (var l : font.split(Component.translatable("laboratorio.miscelanea.cuaderno.vacio"), ANCHO - 16)) {
                g.drawString(font, l, x + 8, ty, 0xFF444444, false);
                ty += 10;
            }
        } else {
            CompoundTag f = fichas.getCompound(pagina);
            g.drawString(font, Component.translatable("laboratorio.miscelanea.cuaderno.pagina", pagina + 1, fichas.size()), x + ANCHO - 70, y + 8, 0xFF666666, false);
            g.drawString(font, Component.translatable("laboratorio.miscelanea.estacion." + f.getString("Estacion")), x + 8, y + 24, 0xFF000000, false);
            g.drawString(font, Component.translatable("laboratorio.miscelanea.cuaderno.sujeto", f.getString("Sujeto")), x + 8, y + 38, 0xFF333333, false);
            g.drawString(font, Component.translatable("laboratorio.miscelanea.cuaderno.nivel", Component.translatable("laboratorio.miscelanea.nivel." + f.getInt("Nivel"))), x + 8, y + 50, 0xFF333333, false);
            g.drawString(font, Component.translatable("laboratorio.miscelanea.cuaderno.dia", f.getLong("Dia")), x + 8, y + 62, 0xFF333333, false);
            int ty = y + 80;
            ListTag ls = f.getList("Lecturas", Tag.TAG_COMPOUND);
            for (int i = 0; i < ls.size() && ty < y + ALTO - 12; i++) {
                CompoundTag l = ls.getCompound(i);
                g.drawString(font, Component.translatable("laboratorio.miscelanea.lectura." + l.getString("K")).append(": " + l.getString("V")), x + 14, ty, 0xFF1C503A, false);
                ty += 11;
            }
        }
        super.render(g, mx, my, pt);
    }
}
