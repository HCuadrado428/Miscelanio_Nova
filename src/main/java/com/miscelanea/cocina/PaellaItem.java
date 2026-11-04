package com.miscelanea.cocina;

import com.miscelanea.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Paellera: la normal o la de bacalao (etiqueta "bacalao"), que al ponerla en la mesa queda con BACALAO = true. */
public class PaellaItem extends BlockItem {
    public static final String BACALAO = "bacalao";

    public PaellaItem(Block b, Properties p) {
        super(b, p);
    }

    public static boolean conBacalao(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(BACALAO);
    }

    public static ItemStack bacalao() {
        ItemStack s = new ItemStack(ModItems.PAELLA.get());
        s.getOrCreateTag().putBoolean(BACALAO, true);
        return s;
    }

    @Override
    public String getDescriptionId(ItemStack s) {
        return conBacalao(s) ? "block.miscelanea.paella_bacalao" : super.getDescriptionId(s);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
        t.add(Component.translatable(conBacalao(s) ? "tooltip.miscelanea.paella_bacalao" : "tooltip.miscelanea.paella").withStyle(ChatFormatting.GRAY));
    }
}
