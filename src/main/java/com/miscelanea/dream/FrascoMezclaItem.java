package com.miscelanea.dream;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Frasco de mezcla del caldero: se bebe como una poción y da todos sus efectos a la vez. Los efectos van en las etiquetas
 * de poción del juego (CustomPotionEffects), así que beber, la descripción y el color funcionan como en una poción.
 */
public class FrascoMezclaItem extends PotionItem {
    public FrascoMezclaItem(Properties p) {
        super(p);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    @Override
    public String getDescriptionId(ItemStack st) {
        return getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        lineaMezcla(st, tip, false);
        super.appendHoverText(st, l, tip, f);
    }

    static void lineaMezcla(ItemStack st, List<Component> tip, boolean arrojadizo) {
        int n = st.getTag() != null ? st.getTag().getInt(Mezclas.TAG_POCIONES) : 0;
        if (n > 0) tip.add(Component.translatable("miscelanea.frasco_mezcla.pociones", n).withStyle(ChatFormatting.DARK_AQUA));
        else tip.add(Component.translatable("miscelanea.frasco_mezcla.vacio").withStyle(ChatFormatting.GRAY));
        if (VenenoLetal.es(st))
            tip.add(arrojadizo ? Component.translatable("miscelanea.frasco_mezcla.veneno_arrojadizo").withStyle(ChatFormatting.DARK_GREEN)
                    : Component.translatable("miscelanea.frasco_mezcla.veneno").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }
}
