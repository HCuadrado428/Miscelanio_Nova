package com.miscelanea.client.baile;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Pantalla invisible que está abierta mientras se usa la pista de baile o la gramola (menú, canción y resultado). Con
 * ella abierta las teclas no llegan a Minecraft ni a los demás mods (nada de disparar un arma, abrir un menú o usar una
 * habilidad sin querer): solo las lee el baile. Excepciones: el chat (T y /) y las teclas del chat de voz, para poder
 * hablar mientras se baila. Lo de dibujar lo sigue haciendo el HUD del baile, por debajo.
 */
public class BaileScreen extends Screen {
    public BaileScreen() {
        super(Component.translatable("block.miscelanea.pista_baile"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {}

    /** Las teclas del chat de voz sí pasan (para pulsar para hablar). */
    private static boolean deVoz(KeyMapping k) {
        return k.getCategory().toLowerCase().contains("voicechat") || k.getName().toLowerCase().contains("voice_chat");
    }

    private static void pasarVoz(int key, int scan, boolean abajo) {
        InputConstants.Key k = InputConstants.getKey(key, scan);
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings)
            if (deVoz(km) && km.getKey().equals(k)) km.setDown(abajo);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || mc.options.keyShift.matches(key, scan)) {
            BaileCliente.salirPorTecla();
            return true;
        }
        if (mc.options.keyChat.matches(key, scan) || mc.options.keyCommand.matches(key, scan)) {
            mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(mc.options.keyCommand.matches(key, scan) ? "/" : ""));
            return true;
        }
        pasarVoz(key, scan, true);
        BaileCliente.teclaBaile(key, scan, true);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        pasarVoz(key, scan, false);
        BaileCliente.teclaBaile(key, scan, false);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        // ni atacar ni usar objetos mientras se baila
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
