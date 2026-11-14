package com.miscelanea.armasfuego.escopeta;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Cartucho de papel para la tolva de la escopeta de presión (se mete como en un saco: clic derecho sobre la escopeta). */
public class CartuchoPresionItem extends Item {
    public final TipoCartucho tipo;

    public CartuchoPresionItem(TipoCartucho tipo, Properties p) {
        super(p);
        this.tipo = tipo;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        l.add(Component.translatable("item.miscelanea.escopeta_cartucho_" + tipo.id + ".ayuda").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.escopeta_cartucho.cargar").withStyle(ChatFormatting.DARK_GRAY));
    }
}
