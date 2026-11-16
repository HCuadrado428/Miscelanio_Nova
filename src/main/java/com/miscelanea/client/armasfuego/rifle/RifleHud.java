package com.miscelanea.client.armasfuego.rifle;

import com.miscelanea.armasfuego.rifle.Rifle;
import com.miscelanea.registry.ModEnchantments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Telémetro en el HUD, encima de la mira del catalejo: distancia y nombre de lo que hay en el centro, qué cartucho
 * hay en la recámara y, con Respiración contenida, cuánto falta para tener la mira firme.
 */
public final class RifleHud {
    private RifleHud() {}

    public static void dibujar(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson() || !p.isScoping() || !RifleCliente.conRifle(p)) return;
        ItemStack s = p.getMainHandItem();
        Font f = mc.font;
        int cx = w / 2;
        // el catalejo es un círculo del alto de la pantalla y a los lados quedan barras negras: el telémetro va en la de la
        // derecha (encima del objetivo estorbaba); si no hay sitio, debajo de la mira como antes
        boolean lateral = w - (cx + h / 2) >= 80;
        int x0 = cx + h / 2 + 6, ancho = w - x0 - 4;
        int y = lateral ? h / 2 - 16 : h / 2 + Math.max(22, h / 7);
        // distancia y objetivo
        Component linea = RifleCliente.distancia < 0 ? Component.translatable("hud.miscelanea.rifle.sin_objetivo")
                : Component.translatable("hud.miscelanea.rifle.distancia", Math.round(RifleCliente.distancia), RifleCliente.objetivo);
        y = texto(g, f, linea, lateral, cx, x0, ancho, y, 0xFFE8E8E8);
        // recámara
        Item bala = Rifle.item(Rifle.recamara(s));
        Component rec;
        if (Rifle.cerrojo(s) > 0) rec = Component.translatable("hud.miscelanea.rifle.cerrojo");
        else if (Rifle.gastada(s) || bala == null) rec = Component.translatable("hud.miscelanea.rifle.recargar");
        else rec = bala.getDescription();
        // lista: nada que decir (el cartucho se ve al sacarlo); si no, el aviso de por qué no dispara
        if (!Rifle.lista(s)) y = texto(g, f, rec, lateral, cx, x0, ancho, y + 2, 0xFFB0B0B0);
        // respiración contenida: barrita que se llena con la mira quieta
        if (Rifle.nivel(s, ModEnchantments.RESPIRACION.get()) > 0) {
            int bw = 50, bx = lateral ? x0 : cx - bw / 2, by = y + 4;
            float k = Math.min(1F, RifleCliente.quietos / (float) Rifle.FIRME_TICKS);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 3, 0x90000000);
            g.fill(bx, by, bx + Math.round(bw * k), by + 2, RifleCliente.firme() ? 0xFF7FD1FF : 0xFFE8E8E8);
            if (RifleCliente.firme()) texto(g, f, Component.translatable("hud.miscelanea.rifle.firme"), lateral, cx, x0, ancho, by + 6, 0xFF7FD1FF);
        }
    }

    /** Una línea del telémetro (con ajuste de línea en la barra lateral); devuelve la y de debajo. */
    private static int texto(GuiGraphics g, Font f, Component c, boolean lateral, int cx, int x0, int ancho, int y, int color) {
        if (!lateral) {
            g.drawCenteredString(f, c, cx, y, color);
            return y + 11;
        }
        g.drawWordWrap(f, c, x0, y, ancho, color);
        return y + f.wordWrapHeight(c, ancho) + 2;
    }
}
