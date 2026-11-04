package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/** Icono del efecto «Desangrado» (una gota de sangre), dibujado desde su propia textura. */
public final class DesangradoIcono implements IClientMobEffectExtensions {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/efecto_desangrado.png");

    @Override
    public boolean renderInventoryIcon(MobEffectInstance inst, EffectRenderingInventoryScreen<?> screen, GuiGraphics g, int x, int y, int blitOffset) {
        g.blit(TEX, x, y + 7, 0, 0.0F, 0.0F, 18, 18, 18, 18);
        return true;
    }

    @Override
    public boolean renderGuiIcon(MobEffectInstance inst, Gui gui, GuiGraphics g, int x, int y, float z, float alpha) {
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(TEX, x + 3, y + 3, 0, 0.0F, 0.0F, 18, 18, 18, 18);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }
}
