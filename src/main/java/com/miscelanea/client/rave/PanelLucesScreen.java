package com.miscelanea.client.rave;

import com.miscelanea.rave.Neon;
import com.miscelanea.rave.PanelLuces;
import com.miscelanea.rave.RaveRed;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;

/** Pantalla del panel y del mando de luces: encender, apagar, patrón, color (16 neones) y disparar el confeti. */
public class PanelLucesScreen extends Screen {
    private static final int W = 232, H = 170, CELDA = 24;
    @Nullable
    private final BlockPos panel;
    private int x0, y0;

    public static void abrir(@Nullable BlockPos p) {
        Minecraft.getInstance().setScreen(new PanelLucesScreen(p));
    }

    public PanelLucesScreen(@Nullable BlockPos panel) {
        super(Component.translatable("block.miscelanea.panel_luces"));
        this.panel = panel;
    }

    private void mandar(int accion, int valor) {
        RaveRed.CANAL.sendToServer(new RaveRed.Luces(panel == null, panel == null ? BlockPos.ZERO : panel, accion, valor));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = Math.max(4, (height - H) / 2);
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.luces_on"), b -> mandar(PanelLuces.ON, 0)).bounds(x0 + 8, y0 + 22, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.dj.luces_off"), b -> mandar(PanelLuces.OFF, 0)).bounds(x0 + 81, y0 + 22, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.panel.confeti"), b -> mandar(PanelLuces.CONFETI, 0)).bounds(x0 + 154, y0 + 22, 70, 20).build());
        for (int i = 0; i < 4; i++) {
            final int modo = i;
            addRenderableWidget(Button.builder(Component.translatable("rave.miscelanea.panel.modo." + i), b -> mandar(PanelLuces.MODO, modo))
                    .bounds(x0 + 8 + i * 54, y0 + 58, 52, 20).build());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int colorEn(double mx, double my) {
        int gx = x0 + 20, gy = y0 + 96;
        if (mx < gx || my < gy || mx >= gx + 8 * CELDA || my >= gy + 2 * CELDA) return -1;
        return (int) ((my - gy) / CELDA) * 8 + (int) ((mx - gx) / CELDA);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF7CFF3A);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0101018);
        g.drawString(font, Component.translatable(panel == null ? "item.miscelanea.mando_luces" : "block.miscelanea.panel_luces").withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 7, 0xFFFF3CF0);
        g.drawString(font, Component.translatable("rave.miscelanea.panel.patron"), x0 + 8, y0 + 47, 0xFF9AA0B0, false);
        g.drawString(font, Component.translatable("rave.miscelanea.panel.color"), x0 + 8, y0 + 85, 0xFF9AA0B0, false);
        int gx = x0 + 20, gy = y0 + 96, sobre = colorEn(mx, my);
        for (int i = 0; i < 16; i++) {
            int cx = gx + (i % 8) * CELDA, cy = gy + (i / 8) * CELDA;
            g.fill(cx + 1, cy + 1, cx + CELDA - 1, cy + CELDA - 1, i == sobre ? 0xFFFFFFFF : 0xFF2A2A36);
            g.fill(cx + 3, cy + 3, cx + CELDA - 3, cy + CELDA - 3, 0xFF000000 | Neon.rgb(DyeColor.byId(i)));
        }
        g.drawString(font, Component.translatable("rave.miscelanea.panel.alcance", PanelLuces.RADIO), x0 + 8, y0 + H - 14, 0xFF9AA0B0, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int c = colorEn(mx, my);
        if (c >= 0) {
            mandar(PanelLuces.COLOR, c);
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }
}
