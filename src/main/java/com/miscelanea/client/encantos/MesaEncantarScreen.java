package com.miscelanea.client.encantos;

import com.miscelanea.encantos.EncantosPacket;
import com.miscelanea.encantos.MesaPacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * La mesa de encantar de elegir: arriba el objeto de la mano; debajo, una fila por cada encantamiento disponible con
 * su descripción y lo que cuesta. Clic en una fila y se pone (el servidor lo comprueba todo).
 */
public class MesaEncantarScreen extends Screen {
    private static final int ANCHO = 260, FILA = 26;
    private final MesaPacket datos;
    private int x0, y0, alto;

    public MesaEncantarScreen(MesaPacket d) {
        super(Component.translatable("miscelanea.mesa.titulo"));
        datos = d;
    }

    public static void abrir(MesaPacket p) {
        Minecraft.getInstance().setScreen(new MesaEncantarScreen(p));
    }

    @Override
    protected void init() {
        alto = 46 + Math.max(1, datos.ops.size()) * FILA + 8;
        x0 = (width - ANCHO) / 2;
        y0 = Math.max(4, (height - alto) / 2);
    }

    private static Enchantment enc(String id) {
        return ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(id));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // marco
        g.fill(x0 - 2, y0 - 2, x0 + ANCHO + 2, y0 + alto + 2, 0xFF1A1024);
        g.fill(x0, y0, x0 + ANCHO, y0 + alto, 0xFF2B1C3D);
        g.fill(x0, y0, x0 + ANCHO, y0 + 20, 0xFF3D2856);
        g.drawString(font, title, x0 + 8, y0 + 6, 0xFFE8D8FF, false);
        Minecraft mc = Minecraft.getInstance();
        ItemStack st = mc.player.getMainHandItem();
        g.renderItem(st, x0 + 8, y0 + 24);
        g.drawString(font, st.getHoverName(), x0 + 30, y0 + 28, 0xFFFFFFFF, false);
        int lapis = 0;
        for (ItemStack s : mc.player.getInventory().items) if (s.is(Items.LAPIS_LAZULI)) lapis += s.getCount();
        boolean gratis = mc.player.getAbilities().instabuild;
        if (datos.ops.isEmpty()) {
            Component c = Component.translatable(datos.libros == 0 ? "miscelanea.mesa.sin_libros" : "miscelanea.mesa.nada");
            g.drawWordWrap(font, c, x0 + 8, y0 + 48, ANCHO - 16, 0xFFB8A8D0);
        }
        for (int i = 0; i < datos.ops.size(); i++) {
            MesaPacket.Op o = datos.ops.get(i);
            Enchantment e = enc(o.id());
            if (e == null) continue;
            int y = y0 + 46 + i * FILA;
            boolean puede = gratis || mc.player.experienceLevel >= o.niveles() && lapis >= o.lapis();
            boolean encima = mx >= x0 + 4 && mx < x0 + ANCHO - 4 && my >= y && my < y + FILA - 2;
            g.fill(x0 + 4, y, x0 + ANCHO - 4, y + FILA - 2, encima && puede ? 0xFF5A3E80 : 0xFF3A2852);
            g.drawString(font, Component.translatable(e.getDescriptionId()), x0 + 10, y + 3, puede ? 0xFFFFE680 : 0xFF8A7A9A, false);
            String coste = gratis ? "" : o.niveles() + " niv · " + o.lapis() + " lapis";
            g.drawString(font, coste, x0 + ANCHO - 10 - font.width(coste), y + 3, puede ? 0xFF80FF80 : 0xFFFF7070, false);
            String desc = e.getDescriptionId() + ".desc";
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(desc).getString(), ANCHO - 20), x0 + 10, y + 14, 0xFFB8A8D0, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (int i = 0; i < datos.ops.size(); i++) {
            int y = y0 + 46 + i * FILA;
            if (mx >= x0 + 4 && mx < x0 + ANCHO - 4 && my >= y && my < y + FILA - 2) {
                ModNetwork.CHANNEL.sendToServer(new EncantosPacket(0, datos.mesa, datos.ops.get(i).id()));
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
