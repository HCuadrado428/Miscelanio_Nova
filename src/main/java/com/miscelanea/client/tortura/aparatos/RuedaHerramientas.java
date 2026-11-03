package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.aparatos.AparatosRed;
import com.miscelanea.tortura.aparatos.Herramienta;
import com.miscelanea.tortura.aparatos.InterrogaAccionPacket;
import com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Rueda de herramientas del maletín (como la rueda de armas del GTA V): sale al mantener el clic derecho con el maletín
 * mirando a la víctima de la silla de interrogatorio. Se apunta con el ratón y al SOLTAR el clic derecho se usa la
 * herramienta marcada (si se suelta enseguida, la rueda se queda abierta y se elige con un clic). 1-4 eligen directamente,
 * Esc cierra. No pausa el juego: el monitor sigue corriendo detrás.
 */
public class RuedaHerramientas extends Screen {
    private static final ResourceLocation[] ICONOS = new ResourceLocation[Herramienta.V.length];

    static {
        for (Herramienta h : Herramienta.V)
            ICONOS[h.ordinal()] = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/tortura/herramienta_" + h.id() + ".png");
    }

    private static final float R0 = 34F, R1 = 96F;
    private final BlockPos silla;
    private final long abierta = Util.getMillis();
    private int marcada = -1;
    private boolean hecho;
    private float aparece;

    public RuedaHerramientas(BlockPos silla) {
        super(Component.translatable("gui.miscelanea.rueda_herramientas"));
        this.silla = silla;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.level.getBlockEntity(silla) instanceof SillaInterrogatorioBlockEntity be) || !be.ocupado()
                || mc.player.distanceToSqr(silla.getX() + 0.5, silla.getY() + 0.5, silla.getZ() + 0.5) > 36) onClose();
    }

    /** Sector bajo el ratón (o -1 en el centro). 0 = arriba y en el sentido de las agujas del reloj. */
    private int sector(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < 14 * 14) return -1;
        double a = Math.toDegrees(Math.atan2(dx, -dy));
        if (a < 0) a += 360;
        int n = Herramienta.V.length;
        return (int) Math.floor((a + 180.0 / n) / (360.0 / n)) % n;
    }

    private void usar(int i) {
        if (hecho || i < 0) return;
        hecho = true;
        AparatosRed.CANAL.sendToServer(new InterrogaAccionPacket(InterrogaAccionPacket.HERRAMIENTA, silla, i));
        onClose();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int boton) {
        if (boton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int s = sector(mx, my);
            if (s >= 0 && Util.getMillis() - abierta > 120) usar(s);
            else if (Util.getMillis() - abierta > 900) onClose();   // soltado en el centro: no se usa nada
            return true;
        }
        return super.mouseReleased(mx, my, boton);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        int s = sector(mx, my);
        if (s >= 0) usar(s);
        else if (boton == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClose();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + Herramienta.V.length) {
            usar(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        aparece = Math.min(1F, (Util.getMillis() - abierta) / 140F);
        float k = 1F - (1F - aparece) * (1F - aparece);
        float cx = width / 2F, cy = height / 2F;
        marcada = sector(mx, my);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // velo suave (no se oscurece todo: se tiene que ver a la víctima detrás)
        Dibujo.arco(g, cx, cy, 0, R1 * 1.25F * k, 0, 360, 0x70000000, 0x00000000);
        int n = Herramienta.V.length;
        float paso = 360F / n;
        for (int i = 0; i < n; i++) {
            float a0 = i * paso - paso / 2F + 1.5F, a1 = i * paso + paso / 2F - 1.5F;
            boolean sel = i == marcada;
            float r1 = (sel ? R1 + 8 : R1) * k, r0 = R0 * k;
            Dibujo.arco(g, cx, cy, r0, r1, a0, a1, sel ? 0xE0303A30 : 0xC0101210, sel ? 0xE0506850 : 0xC0242824);
            Dibujo.arco(g, cx, cy, r1 - 2.5F, r1, a0, a1, sel ? 0xFFE8D27A : 0xA0808878);
            // icono en el centro del sector
            double am = Math.toRadians(i * paso);
            float rm = (r0 + r1) / 2F;
            int ix = Math.round(cx + (float) Math.sin(am) * rm), iy = Math.round(cy - (float) Math.cos(am) * rm);
            int t = sel ? 40 : 32;
            RenderSystem.setShaderColor(1F, 1F, 1F, sel ? 1F : 0.82F);
            g.blit(ICONOS[i], ix - t / 2, iy - t / 2, t, t, 0F, 0F, 64, 64, 64, 64);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            Dibujo.texto(g, font, Component.literal(String.valueOf(i + 1)), ix + t / 2F - 2, iy + t / 2F - 6, 0.6F, 0xFFB0B8A8, true);
        }
        // centro: la herramienta marcada y lo que hace; si no, las pulsaciones de la víctima
        Dibujo.disco(g, cx, cy, (R0 - 3) * k, 0xD0080A08);
        if (marcada >= 0) {
            Herramienta h = Herramienta.de(marcada);
            Dibujo.texto(g, font, Component.translatable(h.clave()).withStyle(ChatFormatting.BOLD), cx, cy - 8, 0.72F, 0xFFF0E6C8, true);
            Dibujo.texto(g, font, Component.translatable(h.clave() + ".corto"), cx, cy + 3, 0.55F, 0xFFB8B0A0, true);
        } else if (mc.level != null && mc.level.getBlockEntity(silla) instanceof SillaInterrogatorioBlockEntity be) {
            boolean paro = be.paroCli > 0;
            float bpm = Mth.lerp(pt, be.bpmVisO, be.bpmVis);
            Dibujo.texto(g, font, Component.literal(paro ? "---" : String.valueOf(Math.round(bpm))), cx, cy - 7, 1.1F, MonitorEcg.color(bpm, paro), true);
            Dibujo.texto(g, font, Component.literal("LPM"), cx, cy + 5, 0.55F, 0xFFB8B0A0, true);
        }
        Dibujo.texto(g, font, Component.translatable("gui.miscelanea.rueda_herramientas.ayuda"), cx, cy + R1 + 18, 0.7F, 0xFFD8D0C0, true);
        RenderSystem.disableBlend();
    }
}
