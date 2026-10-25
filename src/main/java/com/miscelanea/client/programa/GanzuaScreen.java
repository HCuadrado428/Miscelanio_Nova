package com.miscelanea.client.programa;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.programa.GanzuaPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Minijuego de la ganzúa (como en The Outlast Trials): una rueda gira y hay que pulsar (clic o espacio) cuando la marca
 * está en la zona verde. Tres zonas seguidas antes de que se acabe el tiempo. Fallar resta tiempo.
 */
public class GanzuaScreen extends Screen {
    private static final int ZONAS = 3, TIEMPO = 160;
    private final BlockPos pos;
    private int hecho, ticks;
    private float angulo, velocidad = 6.0F, zona, ancho = 46;
    private int flash, error;

    public GanzuaScreen(BlockPos pos) {
        super(Component.empty());
        this.pos = pos;
        nuevaZona();
    }

    public static void abrir(BlockPos pos) {
        Minecraft.getInstance().setScreen(new GanzuaScreen(pos));
        ModNetwork.CHANNEL.sendToServer(new GanzuaPacket(pos, GanzuaPacket.EMPEZAR));
    }

    private void nuevaZona() {
        zona = (float) (Math.random() * 360);
        ancho = 46 - hecho * 9;
        velocidad = 6.0F + hecho * 1.8F;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        angulo = (angulo + velocidad) % 360;
        if (flash > 0) flash--;
        if (error > 0) error--;
        if (++ticks >= TIEMPO) terminar(false);
        var p = Minecraft.getInstance().player;
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 25) onClose();
    }

    private void pulsar() {
        float d = Math.abs(Mth.wrapDegrees(angulo - zona));
        var p = Minecraft.getInstance().player;
        if (d <= ancho / 2) {
            hecho++;
            flash = 6;
            if (p != null) p.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.6F, 1.4F + hecho * 0.15F);
            if (hecho >= ZONAS) {
                terminar(true);
                return;
            }
            nuevaZona();
        } else {
            error = 8;
            ticks += 25;
            if (p != null) p.playSound(SoundEvents.CHAIN_HIT, 0.7F, 0.6F);
        }
    }

    private void terminar(boolean ok) {
        ModNetwork.CHANNEL.sendToServer(new GanzuaPacket(pos, ok ? GanzuaPacket.EXITO : GanzuaPacket.FALLO));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        pulsar();
        return true;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == GLFW.GLFW_KEY_SPACE || k == GLFW.GLFW_KEY_E) {
            pulsar();
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            terminar(false);
            return true;
        }
        return super.keyPressed(k, s, m);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int cx = width / 2, cy = height / 2;
        int r = 46;
        float ang = angulo + velocidad * partial;
        // anillo
        for (int i = 0; i < 180; i++) {
            float a = i * 2;
            boolean verde = Math.abs(Mth.wrapDegrees(a - zona)) <= ancho / 2;
            int col = verde ? (flash > 0 ? 0xFFB0FFB0 : 0xFF3FBF4A) : (error > 0 ? 0xFF802020 : 0xFF404040);
            double rad = Math.toRadians(a - 90);
            int x = cx + (int) Math.round(Math.cos(rad) * r), y = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(x - 2, y - 2, x + 2, y + 2, col);
        }
        // marca giratoria
        double rad = Math.toRadians(ang - 90);
        for (int k = r - 10; k <= r + 6; k++) {
            int x = cx + (int) Math.round(Math.cos(rad) * k), y = cy + (int) Math.round(Math.sin(rad) * k);
            g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFFFFFFF);
        }
        // pernos hechos
        for (int i = 0; i < ZONAS; i++) {
            int px = cx - 20 + i * 16;
            g.fill(px, cy - 4, px + 8, cy + 4, i < hecho ? 0xFF3FBF4A : 0x80FFFFFF);
        }
        // tiempo
        float t = 1 - ticks / (float) TIEMPO;
        g.fill(cx - 60, cy + r + 18, cx + 60, cy + r + 21, 0x90000000);
        g.fill(cx - 60, cy + r + 18, cx - 60 + (int) (120 * t), cy + r + 21, t < 0.25F ? 0xFFD03030 : 0xFFE0E0E0);
    }
}
