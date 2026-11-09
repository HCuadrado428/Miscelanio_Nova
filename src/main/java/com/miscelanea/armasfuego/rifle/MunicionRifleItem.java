package com.miscelanea.armasfuego.rifle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Cartucho del rifle de catalejo (normal, trazadora o de cuerda): solo cambia la ayuda. */
public class MunicionRifleItem extends Item {
    private final String ayuda;

    public MunicionRifleItem(String ayuda, Properties p) {
        super(p);
        this.ayuda = ayuda;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable(ayuda).withStyle(ChatFormatting.GRAY));
    }
}
