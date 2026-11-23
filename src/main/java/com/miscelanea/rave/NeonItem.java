package com.miscelanea.rave;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** Objeto de un bloque de neón: el color va en BlockStateTag (se conserva al ponerlo y al romperlo). */
public class NeonItem extends BlockItem {
    public NeonItem(Block b, Properties p) {
        super(b, p);
    }

    public static ItemStack de(net.minecraft.world.item.Item it, DyeColor c) {
        return com.miscelanea.registry.ModItems.mueble(it, c);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
        t.add(Component.translatable("color.minecraft." + Neon.de(s).getName()).withStyle(ChatFormatting.GRAY));
        t.add(Component.translatable("block.miscelanea.neon.ayuda").withStyle(ChatFormatting.DARK_GRAY));
    }
}
