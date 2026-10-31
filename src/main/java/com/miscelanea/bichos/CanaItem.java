package com.miscelanea.bichos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Caña de pescar de la Bichopedia: cuando pica un pez empieza un minijuego; si lo ganas, te lo llevas (con su tamaño). */
public class CanaItem extends FishingRodItem {
    public CanaItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.cana.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
