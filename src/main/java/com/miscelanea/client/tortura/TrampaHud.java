package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.LlaveTrampaItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Cuenta atrás de la trampa (arriba, estilo reloj digital de Saw), marco de metal en la vista y aviso de los grilletes. */
public final class TrampaHud {
    private TrampaHud() {}

    private static final ResourceLocation VISTA = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/tortura/trampa_vista.png");

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui) return;
        TorturaCliente.Estado est = TorturaCliente.estado(p.getId());
        if (est == null) return;
        // en la ejecución ya no hay reloj (lo pinta EjecucionCliente)
        if (est.trampa > 0 && !EjecucionCliente.activa(p.getId())) reloj(mc, g, w, h, est, pt);
        if (est.grilletes) grilletes(mc, g, w, h);
    }

    private static boolean tieneLlave(Player p) {
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof LlaveTrampaItem) return true;
        return p.getOffhandItem().getItem() instanceof LlaveTrampaItem;
    }

    private static void reloj(Minecraft mc, GuiGraphics g, int w, int h, TorturaCliente.Estado est, float pt) {
        float t = est.trampa - pt;
        int seg = Math.max(0, (int) Math.ceil(t / 20F));
        boolean prisa = seg <= 10;
        float latido = prisa ? (float) Math.pow(Math.max(0, Mth.sin((mc.player.tickCount + pt) * 0.63F)), 6) : 0;
        // la trampa vista desde dentro: barras y dientes por los bordes de la pantalla (primera persona)
        if (mc.options.getCameraType().isFirstPerson()) {
            RenderSystem.enableBlend();
            g.setColor(1F, 1F, 1F, 0.92F);
            g.blit(VISTA, 0, 0, w, h, 0, 0, 512, 256, 512, 256);
            g.setColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
        }
        if (prisa) {
            int a = (int) (40 + 90 * latido);
            int rojo = (a << 24) | 0x900000;
            g.fillGradient(0, 0, w, h / 5, rojo, 0x00900000);
            g.fillGradient(0, h - h / 5, w, h, 0x00900000, rojo);
        }
        Font f = mc.font;
        String txt = String.format("%d:%02d", seg / 60, seg % 60);
        int cx = w / 2, y0 = 6;
        int pw = 92, ph = 38;
        Dibujo.panelMetal(g, cx - pw / 2, y0, cx + pw / 2, y0 + ph, prisa ? 0xFF3A0808 : 0xFF141416);
        // pantalla LCD
        g.fill(cx - pw / 2 + 6, y0 + 5, cx + pw / 2 - 6, y0 + ph - 5, 0xFF0C0202);
        g.fill(cx - pw / 2 + 6, y0 + 5, cx + pw / 2 - 6, y0 + 6, 0xFF2A0A0A);
        float esc = 2.4F + latido * 0.25F;
        int col = prisa && (mc.player.tickCount / 5) % 2 == 0 ? 0xFFFF6A50 : 0xFFFF2A1E;
        // "88:88" apagado detrás, como un reloj de segmentos
        Dibujo.texto(g, f, Component.literal("8:88"), cx, y0 + ph / 2F - 4 * esc, esc, 0x30FF2A1E, false);
        Dibujo.texto(g, f, Component.literal(txt), cx + 0.6F, y0 + ph / 2F - 4 * esc + 0.6F, esc, 0x60FF0000, false);
        Dibujo.texto(g, f, Component.literal(txt), cx, y0 + ph / 2F - 4 * esc, esc, col, false);
        // pista
        Component pista;
        if (tieneLlave(mc.player)) pista = Component.translatable("hud.miscelanea.trampa.con_llave");
        else if (est.sinCajas) pista = Component.translatable("hud.miscelanea.trampa.sin_cajas");
        else pista = Component.translatable("hud.miscelanea.trampa.buscar");
        Dibujo.texto(g, f, pista, cx, y0 + ph + 5, 1F, 0xFFE8D0C0, true);
    }

    private static void grilletes(Minecraft mc, GuiGraphics g, int w, int h) {
        Component c = Component.translatable("hud.miscelanea.grilletes");
        // por encima de la línea de avisos de vanilla (antes se pisaban los dos textos)
        int y = h - 92;
        int tw = mc.font.width(c);
        g.fill(w / 2 - tw / 2 - 6, y - 3, w / 2 + tw / 2 + 6, y + 11, 0x90000000);
        g.fill(w / 2 - tw / 2 - 6, y + 11, w / 2 + tw / 2 + 6, y + 12, 0xFF8A2020);
        Dibujo.texto(g, mc.font, c, w / 2F, y, 1F, 0xFFFFC8B0, true);
    }
}
