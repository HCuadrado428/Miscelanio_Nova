package com.miscelanea.client.torneo;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.torneo.TorneoBlockEntity;
import com.miscelanea.torneo.TorneoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** El cuadro del torneo: inscritos, cruces por rondas y botones para apuntarse, empezar o apuntar quién gana. */
public class TorneoScreen extends Screen {
    private final BlockPos pos;
    private Button apuntar, borrar, empezar, reiniciar;
    private EditBox titulo;
    private int hoverR = -1, hoverS = -1;

    public TorneoScreen(BlockPos pos) {
        super(Component.translatable("miscelanea.torneo.titulo"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TorneoScreen(pos));
    }

    TorneoBlockEntity be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof TorneoBlockEntity b ? b : null;
    }

    boolean organizo() {
        TorneoBlockEntity b = be();
        Minecraft mc = Minecraft.getInstance();
        return b != null && mc.player != null && (b.organizador == null || b.organizador.equals(mc.player.getUUID()) || mc.player.hasPermissions(2));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void enviar(int tipo, int a, int b, String t) {
        ModNetwork.CHANNEL.sendToServer(new TorneoPacket(tipo, pos, a, b, t));
    }

    @Override
    protected void init() {
        int y = height - 28;
        apuntar = addRenderableWidget(Button.builder(Component.translatable("miscelanea.torneo.apuntarme"), b -> enviar(TorneoPacket.APUNTAR, 0, 0, "")).bounds(width / 2 - 206, y, 100, 20).build());
        borrar = addRenderableWidget(Button.builder(Component.translatable("miscelanea.torneo.borrarme"), b -> enviar(TorneoPacket.BORRAR, 0, 0, "")).bounds(width / 2 - 102, y, 100, 20).build());
        empezar = addRenderableWidget(Button.builder(Component.translatable("miscelanea.torneo.empezar"), b -> enviar(TorneoPacket.EMPEZAR, 0, 0, "")).bounds(width / 2 + 2, y, 100, 20).build());
        reiniciar = addRenderableWidget(Button.builder(Component.translatable("miscelanea.torneo.reiniciar"), b -> enviar(TorneoPacket.REINICIAR, 0, 0, "")).bounds(width / 2 + 106, y, 100, 20).build());
        titulo = new EditBox(font, width / 2 - 80, 6, 160, 16, Component.empty());
        titulo.setMaxLength(32);
        TorneoBlockEntity b = be();
        if (b != null) titulo.setValue(b.titulo);
        titulo.setResponder(s -> {});
        addRenderableWidget(titulo);
    }

    @Override
    public void tick() {
        TorneoBlockEntity b = be();
        if (b == null) {
            onClose();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean dentro = mc.player != null && b.ids.contains(mc.player.getUUID());
        apuntar.visible = b.estado == TorneoBlockEntity.INSCRIPCION && !dentro;
        borrar.visible = b.estado == TorneoBlockEntity.INSCRIPCION && dentro;
        empezar.visible = b.estado == TorneoBlockEntity.INSCRIPCION && organizo();
        reiniciar.visible = organizo();
        titulo.setEditable(organizo() && b.estado == TorneoBlockEntity.INSCRIPCION);
        titulo.tick();
        if (!titulo.isFocused() && !titulo.getValue().equals(b.titulo)) titulo.setValue(b.titulo);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (titulo.isFocused() && (k == 257 || k == 335)) {
            enviar(TorneoPacket.TITULO, 0, 0, titulo.getValue());
            titulo.setFocused(false);
            return true;
        }
        return super.keyPressed(k, s, m);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        TorneoBlockEntity b = be();
        hoverR = hoverS = -1;
        if (b != null) {
            g.fill(10, 26, width - 10, height - 34, 0xD0141C2A);
            if (b.estado == TorneoBlockEntity.INSCRIPCION) inscritos(g, b);
            else cuadro(g, b, mx, my);
        }
        super.render(g, mx, my, pt);
        if (!titulo.isFocused() && titulo.getValue().isEmpty())
            g.drawCenteredString(font, Component.translatable("miscelanea.torneo.titulo"), width / 2, 10, 0xFF808080);
    }

    private void inscritos(GuiGraphics g, TorneoBlockEntity b) {
        g.drawCenteredString(font, Component.translatable("miscelanea.torneo.inscritos", b.nombres.size(), TorneoBlockEntity.MAX), width / 2, 34, 0xFFF2C14E);
        for (int i = 0; i < b.nombres.size(); i++) {
            int col = i % 2, fila = i / 2;
            int x = width / 2 - 150 + col * 160, y = 52 + fila * 16;
            g.fill(x, y - 3, x + 140, y + 11, 0xFF26364E);
            g.drawString(font, (i + 1) + ". " + b.nombres.get(i), x + 6, y, 0xFFFFFFFF);
        }
        if (b.nombres.size() < 2) g.drawCenteredString(font, Component.translatable("miscelanea.torneo.ayuda"), width / 2, height - 52, 0xFFA0B0C0);
    }

    private void cuadro(GuiGraphics g, TorneoBlockEntity b, int mx, int my) {
        int cols = b.rondas.size();
        int colW = Math.min(110, (width - 40) / cols), x0 = (width - colW * cols) / 2;
        int top = 40, alto = height - 34 - top - 10;
        int n0 = b.rondas.get(0).length;
        float paso = (float) alto / n0;
        for (int r = 0; r < cols; r++) {
            int[] a = b.rondas.get(r);
            float esp = paso * (n0 / (float) a.length);
            String cab = r == cols - 1 ? Component.translatable("miscelanea.torneo.campeon_col").getString()
                    : r == cols - 2 ? Component.translatable("miscelanea.torneo.final").getString()
                    : Component.translatable("miscelanea.torneo.ronda", r + 1).getString();
            g.drawCenteredString(font, cab, x0 + r * colW + colW / 2 - 6, 30, 0xFF9FD8FF);
            for (int s = 0; s < a.length; s++) {
                int x = x0 + r * colW, y = (int) (top + esp * s + esp / 2 - 6);
                int w = colW - 14;
                boolean decidible = r + 1 < cols && a[s] >= 0 && a[s ^ 1] >= 0 && b.rondas.get(r + 1)[s / 2] == -2;
                boolean sobre = decidible && mx >= x && mx < x + w && my >= y && my < y + 12;
                boolean gano = r + 1 < cols && a[s] >= 0 && b.rondas.get(r + 1)[s / 2] == a[s];
                int fondo = sobre ? 0xFF4A6A30 : r == cols - 1 && a[s] >= 0 ? 0xFF7A5A10 : gano ? 0xFF2E5A3A : 0xFF26364E;
                g.fill(x, y, x + w, y + 12, fondo);
                String t = a[s] >= 0 ? b.nombres.get(a[s]) : a[s] == -1 ? Component.translatable("miscelanea.torneo.pase").getString() : "...";
                if (font.width(t) > w - 6) t = font.plainSubstrByWidth(t, w - 10) + "…";
                g.drawString(font, t, x + 4, y + 2, a[s] >= 0 ? 0xFFFFFFFF : 0xFF7080A0);
                if (r + 1 < cols && s % 2 == 0) {
                    int yb = (int) (top + esp * (s + 1) + esp / 2 - 6);
                    g.fill(x + w, y + 6, x + w + 4, y + 7, 0xFF8090B0);
                    g.fill(x + w, yb + 6, x + w + 4, yb + 7, 0xFF8090B0);
                    g.fill(x + w + 4, y + 6, x + w + 5, yb + 7, 0xFF8090B0);
                    g.fill(x + w + 4, (y + yb) / 2 + 6, x + colW, (y + yb) / 2 + 7, 0xFF8090B0);
                }
                if (sobre) {
                    hoverR = r;
                    hoverS = s;
                }
            }
        }
        if (hoverR >= 0) g.renderTooltip(font, Component.translatable("miscelanea.torneo.marcar_gana"), mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton == 0 && hoverR >= 0) {
            enviar(TorneoPacket.GANA, hoverR, hoverS, "");
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }
}
