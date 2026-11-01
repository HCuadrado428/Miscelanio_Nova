package com.miscelanea.tesoros;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Fósil desenterrado: se puede exponer en una vitrina del museo. */
public class FosilItem extends Item {
    public FosilItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.fosil.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
