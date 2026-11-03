package com.miscelanea.tortura.saw;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** La puerta de salida (dos bloques de alto). */
public class SalidaItem extends DoubleHighBlockItem {
    public SalidaItem(Block b, Properties p) {
        super(b, p);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        for (String k : new String[]{".ayuda", ".ayuda2", ".ayuda3", ".ayuda4"})
            l.add(Component.translatable(getDescriptionId() + k).withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
