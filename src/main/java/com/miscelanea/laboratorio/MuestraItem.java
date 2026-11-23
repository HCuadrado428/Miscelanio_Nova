package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Una muestra (de miedo, de mareo o de frío): sale de los experimentos y es ingrediente de las mezclas. */
public class MuestraItem extends Item {
    public final String tipo;

    public MuestraItem(String tipo, Properties p) {
        super(p);
        this.tipo = tipo;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.muestra_" + tipo + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
