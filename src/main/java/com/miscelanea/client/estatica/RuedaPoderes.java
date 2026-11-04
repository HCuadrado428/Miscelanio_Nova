package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.estatica.Poder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Rueda de poderes del hombre de estática (como la de herramientas del maletín, estilo GTA): sale al mantener la tecla,
 * se apunta con el ratón y al SOLTAR la tecla se elige el poder marcado (en el centro = ninguno). También vale un clic o
 * los números. No pausa el juego. Luego el poder se usa con clic derecho y la mano vacía.
 */
public class RuedaPoderes extends Screen {
    private static final float R0 = 34F, R1 = 100F;
    private final List<Poder> poderes = EstaticaCliente.poderesRueda();
    private final long abierta = Util.getMillis();
    private int marcada = -1;
    private boolean hecho;

    public RuedaPoderes() {
        super(Component.translatable("gui.miscelanea.rueda_estatica"));
    }

    static ResourceLocation icono(Poder p) {
        return new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/estatica/poder_" + p.id + ".png");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (!EstaticaCliente.yo() || EstaticaCine.localActiva()) onClose();
    }

    private int sector(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < 16 * 16 || poderes.isEmpty()) return -1;
        double a = Math.toDegrees(Math.atan2(dx, -dy));
        if (a < 0) a += 360;
        int n = poderes.size();
        return (int) Math.floor((a + 180.0 / n) / (360.0 / n)) % n;
    }

    private void elegir(int i) {
        if (hecho) return;
        hecho = true;
        EstaticaCliente.elegido = i < 0 || i >= poderes.size() ? Poder.NINGUNO : poderes.get(i);
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                com.miscelanea.estatica.EstaticaRegistro.CRUJIDO.get(), 1.4F, 0.5F));
        onClose();
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (EstaticaClienteMod.RUEDA.matches(key, scan)) {
            if (Util.getMillis() - abierta > 120 || marcada >= 0) elegir(marcada);
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + Math.min(9, poderes.size())) {
            elegir(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_0) {
            elegir(-1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        elegir(sector(mx, my));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float ap = Math.min(1F, (Util.getMillis() - abierta) / 140F);
        float k = 1F - (1F - ap) * (1F - ap);
        float cx = width / 2F, cy = height / 2F;
        marcada = sector(mx, my);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        EstaticaHud.borde(g, width, height, 0.5F * k);
        Dibujo.arco(g, cx, cy, 0, R1 * 1.25F * k, 0, 360, 0x70000000, 0x00000000);
        int n = poderes.size();
        float paso = n == 0 ? 360F : 360F / n;
        for (int i = 0; i < n; i++) {
            float a0 = i * paso - paso / 2F + 1.5F, a1 = i * paso + paso / 2F - 1.5F;
            boolean sel = i == marcada;
            float r1 = (sel ? R1 + 8 : R1) * k, r0 = R0 * k;
            Dibujo.arco(g, cx, cy, r0, r1, a0, a1, sel ? 0xE0383838 : 0xC0101012, sel ? 0xE0707078 : 0xC0262628);
            Dibujo.arco(g, cx, cy, r1 - 2.5F, r1, a0, a1, sel ? 0xFFF4F4FF : 0xA0909098);
            double am = Math.toRadians(i * paso);
            float rm = (r0 + r1) / 2F;
            int ix = Math.round(cx + (float) Math.sin(am) * rm), iy = Math.round(cy - (float) Math.cos(am) * rm);
            int t = sel ? 38 : 30;
            RenderSystem.setShaderColor(1F, 1F, 1F, sel ? 1F : 0.8F);
            g.blit(icono(poderes.get(i)), ix - t / 2, iy - t / 2, t, t, 0F, 0F, 64, 64, 64, 64);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            Dibujo.texto(g, font, Component.literal(String.valueOf(i + 1)), ix + t / 2F - 2, iy + t / 2F - 6, 0.6F, 0xFFB0B0B8, true);
        }
        Dibujo.disco(g, cx, cy, (R0 - 3) * k, 0xD0080808);
        Poder p = marcada >= 0 ? poderes.get(marcada) : Poder.NINGUNO;
        Dibujo.texto(g, font, Component.translatable(p.clave()).withStyle(ChatFormatting.BOLD), cx, cy - 8, 0.72F, 0xFFF0F0F4, true);
        Component corto = p == Poder.IMITAR ? Component.translatable("estatica.miscelanea.imitar_hay", ImitarVoz.cuantos(), ImitarVoz.proximo())
                : Component.translatable(p.clave() + ".corto");
        Dibujo.texto(g, font, corto, cx, cy + 3, 0.5F, 0xFFB0B0B8, true);
        Dibujo.texto(g, font, Component.translatable("gui.miscelanea.rueda_estatica.ayuda"), cx, cy + R1 + 18, 0.7F, 0xFFD8D8E0, true);
        RenderSystem.disableBlend();
    }
}
