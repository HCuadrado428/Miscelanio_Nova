package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.AparatosRed;
import com.miscelanea.tortura.aparatos.HierroMarcarItem;
import com.miscelanea.tortura.aparatos.MarcaDibujoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * DIBUJAR del hierro de marcar: cuadrícula de 8x8. Clic izquierdo (y arrastrar) pinta, clic derecho borra. Al guardar se
 * manda al servidor y queda en el objeto. Se ve a la derecha cómo quedará quemado en la piel.
 */
public class PantallaMarca extends Screen {
    private static final int CELDA = 18;
    private final InteractionHand mano;
    private long bits;
    private int x0, y0;
    private int pintando = -1;   // -1 nada, 1 pinta, 0 borra

    public PantallaMarca(InteractionHand mano, long bits) {
        super(Component.translatable("gui.miscelanea.marca.titulo"));
        this.mano = mano;
        this.bits = bits;
    }

    public static void abrir(InteractionHand mano) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack st = mc.player.getItemInHand(mano);
        if (!(st.getItem() instanceof HierroMarcarItem)) return;
        mc.setScreen(new PantallaMarca(mano, HierroMarcarItem.simbolo(st)));
    }

    @Override
    protected void init() {
        x0 = width / 2 - CELDA * 4 - 50;
        y0 = height / 2 - CELDA * 4 - 6;
        int bx = x0 + CELDA * 8 + 20, by = y0 + 96;
        addRenderableWidget(Button.builder(Component.translatable("gui.miscelanea.marca.limpiar"), b -> bits = 0L).bounds(bx, by, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.miscelanea.marca.invertir"), b -> bits = ~bits).bounds(bx, by + 24, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.miscelanea.marca.espejo"), b -> bits = espejo(bits)).bounds(bx, by + 48, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.miscelanea.marca.guardar"), b -> guardar()).bounds(x0, y0 + CELDA * 8 + 10, CELDA * 8, 20).build());
    }

    private static long espejo(long b) {
        long r = 0L;
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                if (((b >>> (y * 8 + x)) & 1L) != 0) r |= 1L << (y * 8 + 7 - x);
        return r;
    }

    private void guardar() {
        AparatosRed.CANAL.sendToServer(new MarcaDibujoPacket(mano == InteractionHand.MAIN_HAND, bits));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack st = mc.player.getItemInHand(mano);
            if (st.getItem() instanceof HierroMarcarItem) HierroMarcarItem.ponerSimbolo(st, bits);   // que se vea ya
        }
        onClose();
    }

    private int celda(double mx, double my) {
        int cx = (int) Math.floor((mx - x0) / CELDA), cy = (int) Math.floor((my - y0) / CELDA);
        if (cx < 0 || cx > 7 || cy < 0 || cy > 7) return -1;
        return cy * 8 + cx;
    }

    private boolean pintar(double mx, double my) {
        int c = celda(mx, my);
        if (c < 0 || pintando < 0) return false;
        if (pintando == 1) bits |= 1L << c;
        else bits &= ~(1L << c);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (celda(mx, my) >= 0 && (boton == 0 || boton == 1)) {
            pintando = boton == 0 ? 1 : 0;
            return pintar(mx, my);
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int boton, double dx, double dy) {
        if (pintando >= 0 && pintar(mx, my)) return true;
        return super.mouseDragged(mx, my, boton, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int boton) {
        pintando = -1;
        return super.mouseReleased(mx, my, boton);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, y0 - 22, 0xFFF0D8B0);
        // marco de hierro y cuadrícula
        g.fill(x0 - 4, y0 - 4, x0 + CELDA * 8 + 4, y0 + CELDA * 8 + 4, 0xFF2A2624);
        g.fill(x0 - 3, y0 - 3, x0 + CELDA * 8 + 3, y0 + CELDA * 8 + 3, 0xFF5A524C);
        int hover = celda(mx, my);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                int i = y * 8 + x;
                boolean on = ((bits >>> i) & 1L) != 0;
                int cx = x0 + x * CELDA, cy = y0 + y * CELDA;
                int c = on ? 0xFFE8641E : ((x + y) % 2 == 0 ? 0xFF2E2A28 : 0xFF363230);
                g.fill(cx + 1, cy + 1, cx + CELDA - 1, cy + CELDA - 1, c);
                if (on) g.fill(cx + 2, cy + 2, cx + CELDA - 2, cy + 4, 0xFFFFC070);
                if (i == hover) g.fill(cx + 1, cy + 1, cx + CELDA - 1, cy + CELDA - 1, 0x40FFFFFF);
            }
        // vista previa: quemado sobre piel
        int px = x0 + CELDA * 8 + 20, py = y0, t = 10;
        g.drawString(font, Component.translatable("gui.miscelanea.marca.vista"), px, py - 12, 0xFFC8B8A8);
        g.fill(px - 2, py - 2, px + t * 8 + 2, py + t * 8 + 2, 0xFF8A5A40);
        g.fill(px, py, px + t * 8, py + t * 8, 0xFFD8A88A);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                if (((bits >>> (y * 8 + x)) & 1L) == 0) continue;
                boolean dentro = pix(x - 1, y) && pix(x + 1, y) && pix(x, y - 1) && pix(x, y + 1);
                g.fill(px + x * t, py + y * t, px + x * t + t, py + y * t + t, dentro ? 0xFF46120C : 0xFF76261A);
            }
        g.drawString(font, Component.translatable("gui.miscelanea.marca.ayuda"), x0 - 4, y0 + CELDA * 8 + 36, 0xFFA8A098);
        super.render(g, mx, my, pt);
    }

    private boolean pix(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8 && ((bits >>> (y * 8 + x)) & 1L) != 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
