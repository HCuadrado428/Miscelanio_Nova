package com.miscelanea.client.cartas;

import com.miscelanea.Miscelanea;
import com.miscelanea.cartas.Baraja;
import com.miscelanea.cartas.CartasPacket;
import com.miscelanea.cartas.MesaCartasBlockEntity;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Pantalla de la brisca: la mano abajo, la mesa en el centro, el mazo y el triunfo a la izquierda, el rival arriba. */
public class CartasScreen extends Screen {
    public static final ResourceLocation ATLAS = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/cartas.png");
    static final int CW = 40, CH = 60;
    private final BlockPos pos;
    private Button cpu, nueva, levantarse;
    private int hover = -1;

    public CartasScreen(BlockPos pos) {
        super(Component.translatable("miscelanea.cartas.titulo"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int x = width / 2 + 120, y = height / 2 - 40;
        cpu = addRenderableWidget(Button.builder(Component.translatable("miscelanea.cartas.vs_cpu"), b -> enviar(CartasPacket.CPU, 0)).bounds(x, y, 100, 20).build());
        nueva = addRenderableWidget(Button.builder(Component.translatable("miscelanea.cartas.nueva"), b -> enviar(CartasPacket.NUEVA, 0)).bounds(x, y + 24, 100, 20).build());
        levantarse = addRenderableWidget(Button.builder(Component.translatable("miscelanea.cartas.levantarse"), b -> {
            enviar(CartasPacket.LEVANTARSE, 0);
            onClose();
        }).bounds(x, y + 48, 100, 20).build());
        actualizar();
    }

    void actualizar() {
        CartasPacket e = CartasCliente.ultimo;
        if (cpu == null || e == null) return;
        cpu.visible = e.estado != MesaCartasBlockEntity.JUGANDO;
        nueva.visible = e.estado == MesaCartasBlockEntity.FIN;
    }

    private void enviar(int tipo, int arg) {
        ModNetwork.CHANNEL.sendToServer(new CartasPacket(tipo, pos, arg));
    }

    static void carta(GuiGraphics g, int c, int x, int y) {
        int u = c < 0 ? 0 : (c % 10) * CW, v = c < 0 ? 4 * CH : (c / 10) * CH;
        g.blit(ATLAS, x, y, u, v, CW, CH, 512, 512);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CartasPacket e = CartasCliente.ultimo;
        int cx = width / 2 - 20, cy = height / 2;
        int x0 = cx - 150, y0 = cy - 115;
        g.fill(x0 - 4, y0 - 4, x0 + 304, y0 + 234, 0xFF4A2A14);
        g.fill(x0, y0, x0 + 300, y0 + 230, 0xFF1E6A3A);
        g.fill(x0 + 6, y0 + 6, x0 + 294, y0 + 224, 0xFF237A43);
        if (e == null || !e.pos.equals(pos)) {
            super.render(g, mx, my, pt);
            return;
        }
        int yo = e.miAsiento < 0 ? 0 : e.miAsiento, rival = 1 - yo;
        // rival
        g.drawCenteredString(font, nombre(e, rival) + "  ·  " + Component.translatable("miscelanea.cartas.bazas", e.bazas[rival]).getString(), cx + 20, y0 + 8, 0xFFFFFFFF);
        for (int i = 0; i < e.enMano[rival]; i++) carta(g, -1, cx - 40 + i * 30, y0 + 20);
        // mazo y triunfo
        if (e.triunfo >= 0 && e.mazo > 0) carta(g, e.triunfo, x0 + 30, cy - 30);
        for (int i = 0; i < Math.min(6, Math.max(0, e.mazo - 1)); i++) carta(g, -1, x0 + 12 - i, cy - 36 - i);
        g.drawString(font, Component.translatable("miscelanea.cartas.mazo", e.mazo).getString(), x0 + 10, cy + 34, 0xFFE0F0E0);
        if (e.triunfo >= 0) g.drawString(font, Component.translatable("miscelanea.cartas.triunfo." + Baraja.palo(e.triunfo)).getString(), x0 + 10, cy + 46, 0xFFFFE070);
        // mesa
        if (e.mesa[rival] >= 0) carta(g, e.mesa[rival], cx + 8, cy - 62);
        if (e.mesa[yo] >= 0) carta(g, e.mesa[yo], cx + 32, cy - 40);
        // mi mano
        hover = -1;
        boolean miTurno = e.estado == MesaCartasBlockEntity.JUGANDO && e.turno == yo && e.mesa[yo] < 0 && !(e.mesa[0] >= 0 && e.mesa[1] >= 0);
        int n = e.mano.length, xm = cx + 20 - (n * 46) / 2;
        for (int i = 0; i < n; i++) {
            int x = xm + i * 46, y = y0 + 160;
            boolean sobre = mx >= x && mx < x + CW && my >= y - 8 && my < y + CH;
            if (sobre && miTurno) {
                hover = i;
                y -= 8;
            }
            carta(g, e.mano[i], x, y);
            if (sobre && miTurno) g.fill(x, y + CH, x + CW, y + CH + 2, 0xFFFFE070);
        }
        g.drawCenteredString(font, nombre(e, yo) + "  ·  " + Component.translatable("miscelanea.cartas.bazas", e.bazas[yo]).getString(), cx + 20, y0 + 222 - 8, 0xFFFFFFFF);
        // estado
        String txt;
        int col = 0xFFFFE070;
        if (e.estado == MesaCartasBlockEntity.ESPERA) txt = Component.translatable("miscelanea.cartas.esperando").getString();
        else if (e.estado == MesaCartasBlockEntity.FIN) {
            txt = Component.translatable("miscelanea.cartas.resultado", e.puntos[yo], e.puntos[rival]).getString();
            col = e.puntos[yo] > e.puntos[rival] ? 0xFF70FF70 : e.puntos[yo] < e.puntos[rival] ? 0xFFFF7070 : 0xFFFFFFFF;
        } else if (e.mesa[0] >= 0 && e.mesa[1] >= 0) txt = Component.translatable("miscelanea.cartas.baza").getString();
        else if (miTurno) txt = Component.translatable("miscelanea.cartas.tu_turno").getString();
        else txt = Component.translatable("miscelanea.cartas.turno_de", nombre(e, e.turno)).getString();
        g.drawCenteredString(font, txt, cx + 20, cy + 28, col);
        super.render(g, mx, my, pt);
    }

    private String nombre(CartasPacket e, int i) {
        return e.nombres[i].isEmpty() ? "—" : e.nombres[i];
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && hover >= 0) {
            enviar(CartasPacket.JUGAR, hover);
            hover = -1;
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }
}
