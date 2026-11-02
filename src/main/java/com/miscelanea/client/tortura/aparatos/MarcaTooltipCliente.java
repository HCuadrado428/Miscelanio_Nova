package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.HierroMarcarItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/** El símbolo del hierro de marcar en su tooltip (8x8, como quedará quemado). */
public class MarcaTooltipCliente implements ClientTooltipComponent {
    private static final int T = 4;
    private final long bits;

    public MarcaTooltipCliente(HierroMarcarItem.MarcaTooltip m) {
        this.bits = m.bits();
    }

    @Override
    public int getHeight() {
        return T * 8 + 6;
    }

    @Override
    public int getWidth(Font f) {
        return T * 8 + 4;
    }

    @Override
    public void renderImage(Font f, int x, int y, GuiGraphics g) {
        g.fill(x, y + 1, x + T * 8 + 2, y + T * 8 + 3, 0xFF6A4632);
        g.fill(x + 1, y + 2, x + T * 8 + 1, y + T * 8 + 2, 0xFFD2A486);
        for (int j = 0; j < 8; j++)
            for (int i = 0; i < 8; i++)
                if (((bits >>> (j * 8 + i)) & 1L) != 0)
                    g.fill(x + 1 + i * T, y + 2 + j * T, x + 1 + i * T + T, y + 2 + j * T + T, 0xFF6A1E14);
    }
}
