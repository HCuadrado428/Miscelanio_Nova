package com.miscelanea.tortura.aparatos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** Objeto de un aparato (bloque de dos partes) con su ayuda en el tooltip ("block.miscelanea.X.ayuda", ".ayuda2"...). */
public class AparatoItem extends BlockItem {
    private final int lineas;

    public AparatoItem(Block b, Properties p, int lineasAyuda) {
        super(b, p);
        this.lineas = lineasAyuda;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        for (int i = 1; i <= lineas; i++)
            l.add(Component.translatable(getDescriptionId() + ".ayuda" + (i == 1 ? "" : String.valueOf(i))).withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
