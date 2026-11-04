package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.estatica.Poder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lo que la estática pinta en la pantalla: la transformación (nieve, líneas de VHS, bandas de cine, el fogonazo), el
 * borde de nieve suave de quien es de estática y sus microcortes, el poder elegido, la nieve del toque/charcos y la barra
 * de objetos corrupta (solo se VE así: los objetos no cambian).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EstaticaHud {
    private EstaticaHud() {}

    private static final ResourceLocation RUIDO = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/estatica_ruido.png");
    private static final ResourceLocation BORDE = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/estatica_borde.png");
    private static final int TW = 256, TH = 144, CUADROS = 8;
    private static long nieveHasta, corruptoHasta;
    private static int nieveDur = 1;
    private static boolean nieveBorde;

    static void nieve(int ticks, boolean borde, int corromper) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        long t = l.getGameTime();
        if (t + ticks > nieveHasta) {
            nieveHasta = t + ticks;
            nieveDur = Math.max(1, ticks);
            nieveBorde = borde;
        }
        if (corromper > 0) corruptoHasta = Math.max(corruptoHasta, t + corromper);
    }

    static boolean corrupto() {
        var l = Minecraft.getInstance().level;
        return l != null && l.getGameTime() < corruptoHasta;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        nieveHasta = corruptoHasta = 0;
    }

    // ------------------------------------------------------------------ piezas
    private static int cuadro() {
        return (int) ((Util.getMillis() / 50) % CUADROS);
    }

    /** Nieve a pantalla completa con la opacidad dada. */
    static void ruido(GuiGraphics g, int w, int h, float a) {
        if (a <= 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, Mth.clamp(a, 0F, 1F));
        g.blit(RUIDO, 0, 0, w, h, 0, cuadro() * TH, TW, TH, TW, TH * CUADROS);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** Nieve solo por los bordes (la textura ya lleva el degradado). */
    static void borde(GuiGraphics g, int w, int h, float a) {
        if (a <= 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, Mth.clamp(a, 0F, 1F));
        g.blit(BORDE, 0, 0, w, h, 0, cuadro() * TH, TW, TH, TW, TH * CUADROS);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** Líneas de seguimiento de VHS: una banda que baja, franjas desplazadas y rayas finas. */
    static void vhs(GuiGraphics g, int w, int h, float k, long t, float pt) {
        if (k <= 0.01F) return;
        float tt = t + pt;
        int y = (int) ((tt * 7.3F) % (h + 60)) - 30;
        g.fill(0, y, w, y + 14, ((int) (70 * k) << 24) | 0xFFFFFF);
        g.fill(0, y + 14, w, y + 18, ((int) (110 * k) << 24));
        long sem = (long) (tt / 2);
        for (int i = 0; i < 2 + (int) (6 * k); i++) {
            float r = Mascara.hash((int) sem, i, 7, 31);
            int yy = (int) (r * h), al = 1 + (int) (Mascara.hash((int) sem, i, 8, 31) * 3);
            int dx = (int) ((Mascara.hash((int) sem, i, 9, 31) - 0.5F) * 30 * k);
            g.fill(Math.max(0, dx), yy, Math.min(w, w + dx), yy + al, ((int) ((60 + 120 * r) * k) << 24) | (r > 0.5F ? 0xFFFFFF : 0x000000));
        }
        // aberración de color: dos franjas finas de color en los bordes de la banda
        g.fill(0, y - 1, w, y, ((int) (60 * k) << 24) | 0xFF3030);
        g.fill(0, y + 18, w, y + 19, ((int) (60 * k) << 24) | 0x30A0FF);
    }

    /** Letras cambiadas por símbolos al azar (nombre que parpadea, objetos corruptos). */
    static String revolver(String s, int semilla) {
        StringBuilder sb = new StringBuilder(s.length());
        String sim = "#%&@$?¿!*+=/\\|░▒▓█";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            float h = Mascara.hash(semilla, i, 2, 17);
            sb.append(c != ' ' && h < 0.45F ? sim.charAt((int) (Mascara.hash(semilla, i, 3, 17) * sim.length()) % sim.length()) : c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ capa del HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long t = mc.level.getGameTime();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        if (EstaticaCine.localActiva()) {
            cine(g, w, h, pt, t);
        } else if (PantallaCliente.dentro()) {
            PantallaCliente.hud(g, pt, w, h);
        } else {
            if (EstaticaCliente.yo() && !mc.options.hideGui) {
                float parp = 0.14F + 0.05F * Mth.sin((t + pt) * 0.11F) + (Mascara.hash((int) (t / 2), 1, 1, 5) < 0.06F ? 0.15F : 0F);
                borde(g, w, h, parp);
                if (t <= EstaticaCliente.cortoHasta) {
                    ruido(g, w, h, 0.25F);
                    vhs(g, w, h, 0.7F, t, pt);
                }
                elegido(mc, g, w, h);
            }
            // nieve del toque / de un charco
            if (t < nieveHasta) {
                float k = Mth.clamp((nieveHasta - t - pt) / nieveDur, 0F, 1F);
                if (nieveBorde) borde(g, w, h, 0.9F * k);
                else {
                    ruido(g, w, h, 0.85F * k);
                    vhs(g, w, h, k, t, pt);
                }
            }
            if (corrupto() && !mc.options.hideGui) barraCorrupta(mc, g, w, h, t);
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void cine(GuiGraphics g, int w, int h, float pt, long t) {
        ruido(g, w, h, EstaticaCine.hud("ruido", pt) * (0.75F + 0.25F * Mascara.hash((int) t, 4, 4, 4)));
        vhs(g, w, h, EstaticaCine.hud("vhs", pt), t, pt);
        float b = EstaticaCine.hud("barras", pt);
        if (b > 0) {
            int alto = Math.round(h * 0.11F * b);
            g.fill(0, 0, w, alto, 0xFF000000);
            g.fill(0, h - alto, w, h, 0xFF000000);
        }
        float d = EstaticaCine.destello(pt);
        if (d > 0) g.fill(0, 0, w, h, ((int) (230 * d) << 24) | 0xF4F4FF);
    }

    /** Icono pequeño del poder elegido, encima de la barra (a la derecha). */
    private static void elegido(Minecraft mc, GuiGraphics g, int w, int h) {
        Poder p = EstaticaCliente.elegido;
        if (p == Poder.NINGUNO) return;
        int x = w / 2 + 96, y = h - 24;
        g.fill(x - 1, y - 1, x + 21, y + 21, 0x90000000);
        g.blit(RuedaPoderes.icono(p), x, y, 20, 20, 0F, 0F, 64, 64, 64, 64);
        String txt = Component.translatable(p.clave()).getString();
        if (p == Poder.IMITAR) txt += " (" + ImitarVoz.cuantos() + ")";
        g.drawString(mc.font, txt, x + 24, y + 6, 0xFFE0E0E0, true);
    }

    /** Cada hueco de la barra de objetos con nieve encima y, de vez en cuando, desplazado. */
    private static void barraCorrupta(Minecraft mc, GuiGraphics g, int w, int h, long t) {
        int x0 = w / 2 - 91;
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().items.get(i);
            if (st.isEmpty() && Mascara.hash(i, (int) (t / 3), 1, 2) > 0.3F) continue;
            int x = x0 + i * 20 + 3, y = h - 19;
            int dx = Mascara.hash(i, (int) (t / 2), 4, 2) < 0.12F ? 2 : 0;
            g.setColor(1F, 1F, 1F, 0.92F);
            int u = (int) (Mascara.hash(i, (int) t, 5, 2) * (TW - 16)), v = cuadro() * TH + (int) (Mascara.hash(i, (int) t, 6, 2) * (TH - 16));
            g.blit(RUIDO, x + dx, y, 16, 16, u, v, 16, 16, TW, TH * CUADROS);
            g.setColor(1F, 1F, 1F, 1F);
        }
        // el nombre del objeto en la mano, roto
        ItemStack mano = mc.player.getMainHandItem();
        if (!mano.isEmpty() && Mascara.hash((int) (t / 4), 7, 7, 7) > 0.25F) {
            String n = revolver(mano.getHoverName().getString(), (int) (t / 3));
            g.drawCenteredString(mc.font, n, w / 2, h - 59, 0xFFB0B0B0);
        }
    }

    /** Con la barra corrupta, el nombre normal del objeto no sale (sale el roto de arriba). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void sinNombre(RenderGuiOverlayEvent.Pre e) {
        if (corrupto() && VanillaGuiOverlay.ITEM_NAME.id().equals(e.getOverlay().id())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void descripcion(ItemTooltipEvent e) {
        if (!corrupto() || e.getToolTip().isEmpty()) return;
        long t = Util.getMillis() / 150;
        for (int i = 0; i < e.getToolTip().size(); i++)
            e.getToolTip().set(i, Component.literal(revolver(e.getToolTip().get(i).getString(), (int) t + i * 31)));
    }
}
