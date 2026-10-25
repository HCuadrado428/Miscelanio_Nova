package com.miscelanea.client.programa;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.programa.EntrarPruebaPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Terminal del Programa: eliges el rig (como con Noakes en el juego) y entras en la prueba. */
public class RigScreen extends Screen {
    private int elegido;

    public RigScreen() {
        super(Component.translatable("programa.miscelanea.rig.titulo"));
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 50;
        for (int i = 0; i < 4; i++) {
            final int r = i;
            addRenderableWidget(Button.builder(Component.translatable("programa.miscelanea.rig." + i), b -> elegido = r)
                    .bounds(cx - 160 + i * 80, y, 76, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("programa.miscelanea.rig.entrar"), b -> {
            ModNetwork.CHANNEL.sendToServer(new EntrarPruebaPacket(elegido));
            onClose();
        }).bounds(cx - 60, y + 90, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float p) {
        g.fill(0, 0, width, height, 0xE0080808);
        int cx = width / 2, y = height / 2 - 50;
        g.drawCenteredString(font, Component.translatable("programa.miscelanea.intro1"), cx, y - 46, 0xFFB0B0B0);
        g.pose().pushPose();
        g.pose().translate(cx, y - 32, 0);
        g.pose().scale(2, 2, 1);
        g.drawCenteredString(font, Component.translatable("programa.miscelanea.intro2"), 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        super.render(g, mx, my, p);
        int bx = cx - 160 + elegido * 80;
        g.fill(bx - 1, y - 2, bx + 77, y - 1, 0xFFFFFFFF);
        g.fill(bx - 1, y + 21, bx + 77, y + 22, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("programa.miscelanea.rig." + elegido + ".desc"), cx, y + 36, 0xFFD0D0D0);
        g.drawCenteredString(font, Component.translatable("programa.miscelanea.rig.tecla"), cx, y + 54, 0xFF8A8A8A);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
