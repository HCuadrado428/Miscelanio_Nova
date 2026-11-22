package com.miscelanea.client.camaras;

import com.miscelanea.camaras.TeleBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.time.LocalTime;
import java.util.List;

/**
 * La imagen de una cámara en pantalla grande, al hacer clic derecho en una tele: a la mejor resolución y sin pixelar
 * (filtrada), en gris verdoso de laboratorio, con líneas de barrido, viñeta, el nombre del canal, «REC» y la hora. Con las
 * flechas (o los botones) se cambia entre las cámaras que existen y «Fijar en la tele» deja ese canal en la tele.
 */
public class VistaCamaraScreen extends Screen {
    private final BlockPos tele;
    private int canal;
    private Button anterior, siguiente, fijar;

    public static void abrir(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        int c = 1;
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof TeleBlockEntity t) c = CamarasCliente.efectivo(t.canal());
        if (c <= 0) c = CamarasCliente.lista().isEmpty() ? 1 : CamarasCliente.lista().get(0);
        mc.setScreen(new VistaCamaraScreen(pos, c));
    }

    public VistaCamaraScreen(BlockPos tele, int canal) {
        super(Component.translatable("camaras.miscelanea.vista"));
        this.tele = tele;
        this.canal = canal;
    }

    @Override
    protected void init() {
        int y = height - 26;
        anterior = addRenderableWidget(Button.builder(Component.literal("◄"), b -> cambiar(-1)).bounds(width / 2 - 100, y, 30, 20).build());
        siguiente = addRenderableWidget(Button.builder(Component.literal("►"), b -> cambiar(1)).bounds(width / 2 - 66, y, 30, 20).build());
        fijar = addRenderableWidget(Button.builder(Component.translatable("camaras.miscelanea.vista.fijar"), b -> {
            CamarasCliente.fijarCanal(tele, canal);
            onClose();
        }).bounds(width / 2 - 30, y, 130, 20).build());
    }

    private void cambiar(int sentido) {
        List<Integer> l = CamarasCliente.lista();
        if (l.isEmpty()) {
            canal = Math.max(1, Math.min(99, canal + sentido));
            return;
        }
        int i = l.indexOf(canal);
        if (i < 0) i = sentido > 0 ? -1 : 0;
        canal = l.get(Math.floorMod(i + sentido, l.size()));
    }

    @Override
    public boolean keyPressed(int k, int sc, int mod) {
        if (k == 263) {
            cambiar(-1);
            return true;
        }
        if (k == 262) {
            cambiar(1);
            return true;
        }
        return super.keyPressed(k, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CamarasCliente.visto(canal);
        CamarasCliente.visto(0);          // pide también la lista de cámaras (para las flechas y los nombres)
        g.fill(0, 0, width, height, 0xFF050807);
        // la imagen, lo más grande posible en 16:9
        int mw = width - 40, mh = height - 70;
        int w = Math.min(mw, mh * 16 / 9), h = w * 9 / 16;
        int x = (width - w) / 2, y = 12 + (mh - h) / 2;
        boolean senal = CamarasCliente.hayEnvio(canal);
        ResourceLocation rl = senal ? CamarasCliente.senal(canal).rl : CamarasCliente.estatica();
        RenderSystem.setShaderColor(0.80F, 0.96F, 0.88F, 1.0F);      // gris verdoso de laboratorio
        int tw = 64, th = 36;
        if (senal && CamarasCliente.senal(canal).tex.getPixels() != null) {
            tw = CamarasCliente.senal(canal).tex.getPixels().getWidth();
            th = CamarasCliente.senal(canal).tex.getPixels().getHeight();
        }
        g.blit(rl, x, y, w, h, 0, 0, tw, th, tw, th);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // líneas de barrido y viñeta
        for (int yy = y; yy < y + h; yy += 3) g.fill(x, yy, x + w, yy + 1, 0x22000000);
        int v = Math.max(6, h / 8);
        g.fillGradient(x, y, x + w, y + v, 0x88000000, 0x00000000);
        g.fillGradient(x, y + h - v, x + w, y + h, 0x00000000, 0x88000000);
        // marco y esquinas
        int ce = Math.max(8, w / 30), gr = 2, col = 0xFFB8E6CC;
        g.fill(x - 2, y - 2, x + w + 2, y, 0xFF1C2622);
        g.fill(x - 2, y + h, x + w + 2, y + h + 2, 0xFF1C2622);
        g.fill(x - 2, y, x, y + h, 0xFF1C2622);
        g.fill(x + w, y, x + w + 2, y + h, 0xFF1C2622);
        for (int[] c : new int[][]{{x + 6, y + 6, 1, 1}, {x + w - 6, y + 6, -1, 1}, {x + 6, y + h - 6, 1, -1}, {x + w - 6, y + h - 6, -1, -1}}) {
            g.fill(Math.min(c[0], c[0] + c[2] * ce), c[1] - (c[3] < 0 ? gr : 0), Math.max(c[0], c[0] + c[2] * ce), c[1] + (c[3] < 0 ? 0 : gr), col);
            g.fill(c[0] - (c[2] < 0 ? gr : 0), Math.min(c[1], c[1] + c[3] * ce), c[0] + (c[2] < 0 ? 0 : gr), Math.max(c[1], c[1] + c[3] * ce), col);
        }
        // rótulos
        String nombre = CamarasCliente.nombre(canal);
        String cab = String.format("CAM %02d", canal) + (nombre.isEmpty() ? "" : "  " + nombre.toUpperCase(java.util.Locale.ROOT));
        float esc = Math.max(1.0F, w / 320.0F);
        g.pose().pushPose();
        g.pose().translate(x + 14, y + 14, 0);
        g.pose().scale(esc, esc, 1);
        g.drawString(font, Component.literal(cab).withStyle(ChatFormatting.BOLD), 0, 0, 0xFFD8F5E4, true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(x + w - 14 - 38 * esc, y + 14, 0);
        g.pose().scale(esc, esc, 1);
        if (senal) {
            boolean punto = (Util.getMillis() / 500) % 2 == 0;
            g.drawString(font, Component.literal("●").withStyle(ChatFormatting.BOLD), 0, 0, punto ? 0xFFFF3030 : 0xFF601010, true);
            g.drawString(font, Component.literal("REC").withStyle(ChatFormatting.BOLD), 12, 0, 0xFFD8F5E4, true);
        }
        g.pose().popPose();
        LocalTime t = LocalTime.now();
        g.pose().pushPose();
        g.pose().translate(x + 14, y + h - 14 - 9 * esc, 0);
        g.pose().scale(esc, esc, 1);
        g.drawString(font, String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()), 0, 0, 0xFFD8F5E4, true);
        g.pose().popPose();
        if (!senal) {
            Component s = Component.translatable("camaras.miscelanea.vista.sin_senal").withStyle(ChatFormatting.BOLD);
            g.pose().pushPose();
            g.pose().translate(x + w / 2.0F, y + h / 2.0F, 0);
            g.pose().scale(esc * 2, esc * 2, 1);
            g.drawCenteredString(font, s, 0, -4, 0xFFFFFFFF);
            g.pose().popPose();
        }
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.translatable("camaras.miscelanea.vista.ayuda"), width / 2, height - 40, 0xFF8FB8A0);
    }
}
