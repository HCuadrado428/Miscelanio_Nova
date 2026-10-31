package com.miscelanea.client.bichos;

import com.miscelanea.Miscelanea;
import com.miscelanea.bichos.ColeccionPacket;
import com.miscelanea.bichos.Especie;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * La Bichopedia: libro abierto con dos pestañas (bichos y peces). A la izquierda la cuadrícula de especies
 * (siluetas si aún no las tienes) y a la derecha la ficha: dónde y cuándo sale, rareza, récord y si está en el museo.
 */
public class BichopediaScreen extends Screen {
    private static final int W = 300, H = 190;
    private static boolean peces;
    private static int elegido;
    private int x0, y0;

    public BichopediaScreen() {
        super(Component.translatable("item.miscelanea.bichopedia"));
    }

    public static void abrir() {
        Minecraft.getInstance().setScreen(new BichopediaScreen());
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    private List<Especie> lista() {
        List<Especie> l = new ArrayList<>();
        for (Especie e : Especie.values()) if (e.esPez() == peces) l.add(e);
        return l;
    }

    private static CompoundTag datos() {
        return ColeccionPacket.cliente;
    }

    private static boolean tiene(Especie e) {
        return datos().getCompound("atrapados").contains(e.id());
    }

    private static boolean donado(Especie e) {
        return datos().getCompound("donados").contains(e.id());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        // tapas y páginas
        g.fill(x0 - 6, y0 - 6, x0 + W + 6, y0 + H + 6, 0xFF2F5A2F);
        g.fill(x0 - 4, y0 - 4, x0 + W + 4, y0 + H + 4, 0xFF46804A);
        g.fill(x0, y0, x0 + W / 2 - 1, y0 + H, 0xFFF4EBD2);
        g.fill(x0 + W / 2 + 1, y0, x0 + W, y0 + H, 0xFFF4EBD2);
        g.fill(x0 + W / 2 - 1, y0, x0 + W / 2 + 1, y0 + H, 0xFFC9BC98);
        // pestañas
        String[] tabs = {"bichopedia.miscelanea.bichos", "bichopedia.miscelanea.peces"};
        for (int i = 0; i < 2; i++) {
            int tx = x0 + 10 + i * 70, ty = y0 - 18;
            boolean on = peces == (i == 1);
            g.fill(tx, ty, tx + 64, y0 - 4, on ? 0xFFF4EBD2 : 0xFFB8AC86);
            g.drawCenteredString(font, Component.translatable(tabs[i]), tx + 32, ty + 4, on ? 0xFF3A2A10 : 0xFF6A5A40);
        }
        // contador
        List<Especie> l = lista();
        int n = 0, d = 0;
        for (Especie e : l) {
            if (tiene(e)) n++;
            if (donado(e)) d++;
        }
        g.drawString(font, Component.translatable("bichopedia.miscelanea.cuenta", n, l.size(), d), x0 + 10, y0 + 8, 0xFF5A4A2A, false);
        // cuadrícula 4x3
        for (int i = 0; i < l.size(); i++) {
            Especie e = l.get(i);
            int cx = x0 + 12 + (i % 4) * 33, cy = y0 + 24 + (i / 4) * 40;
            boolean sobre = mx >= cx && mx < cx + 30 && my >= cy && my < cy + 30;
            g.fill(cx - 1, cy - 1, cx + 31, cy + 31, i == elegido ? 0xFFB08A40 : sobre ? 0xFFD8C8A0 : 0xFFE6DAB8);
            g.fill(cx, cy, cx + 30, cy + 30, 0xFFF8F1DE);
            ResourceLocation ico = new ResourceLocation(Miscelanea.MOD_ID, "textures/item/" + e.item() + ".png");
            if (tiene(e)) {
                g.blit(ico, cx + 3, cy + 3, 24, 24, 0, 0, 32, 32, 32, 32);
            } else {
                RenderSystem.setShaderColor(0.25F, 0.2F, 0.15F, 0.55F);
                g.blit(ico, cx + 3, cy + 3, 24, 24, 0, 0, 32, 32, 32, 32);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            if (donado(e)) g.drawString(font, "✔", cx + 21, cy + 21, 0xFF8A6A20, false);
        }
        // ficha
        if (elegido >= l.size()) elegido = 0;
        Especie e = l.get(elegido);
        int fx = x0 + W / 2 + 10, fy = y0 + 10;
        boolean t = tiene(e);
        Component nombre = t ? Component.translatable("item.miscelanea." + e.item()) : Component.literal("???");
        g.drawString(font, nombre, fx, fy, 0xFF3A2A10, false);
        g.drawString(font, "★".repeat(e.estrellas) + "☆".repeat(5 - e.estrellas), fx, fy + 11, 0xFFC09020, false);
        ResourceLocation ico = new ResourceLocation(Miscelanea.MOD_ID, "textures/item/" + e.item() + ".png");
        if (!t) RenderSystem.setShaderColor(0.25F, 0.2F, 0.15F, 0.55F);
        g.blit(ico, fx + 34, fy + 24, 64, 64, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int y = fy + 94;
        String hora = "bichopedia.miscelanea.hora_" + e.hora.name().toLowerCase();
        String lluvia = "bichopedia.miscelanea.lluvia_" + e.lluvia.name().toLowerCase();
        g.drawString(font, Component.translatable("bichopedia.miscelanea.habitat_" + e.id()), fx, y, 0xFF5A4A2A, false);
        g.drawString(font, Component.translatable(hora), fx, y + 11, 0xFF5A4A2A, false);
        g.drawString(font, Component.translatable(lluvia), fx, y + 22, 0xFF5A4A2A, false);
        if (e.esPez()) {
            int rec = datos().getCompound("atrapados").getInt(e.id());
            g.drawString(font, Component.translatable("bichopedia.miscelanea.tamano", e.cmMin, e.cmMax), fx, y + 33, 0xFF5A4A2A, false);
            if (rec > 1) g.drawString(font, Component.translatable("bichopedia.miscelanea.record", rec), fx, y + 44, 0xFF8A5A10, false);
        }
        Component estado = donado(e) ? Component.translatable("bichopedia.miscelanea.en_museo")
                : t ? Component.translatable("bichopedia.miscelanea.capturado") : Component.translatable("bichopedia.miscelanea.sin_capturar");
        g.drawString(font, estado, fx, y0 + H - 14, donado(e) ? 0xFF2A7A2A : 0xFF7A6A4A, false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (int i = 0; i < 2; i++) {
            int tx = x0 + 10 + i * 70, ty = y0 - 18;
            if (mx >= tx && mx < tx + 64 && my >= ty && my < y0 - 4) {
                peces = i == 1;
                elegido = 0;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                return true;
            }
        }
        List<Especie> l = lista();
        for (int i = 0; i < l.size(); i++) {
            int cx = x0 + 12 + (i % 4) * 33, cy = y0 + 24 + (i / 4) * 40;
            if (mx >= cx && mx < cx + 30 && my >= cy && my < cy + 30) {
                elegido = i;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.4F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }
}
