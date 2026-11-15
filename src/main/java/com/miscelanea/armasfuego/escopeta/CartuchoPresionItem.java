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

}
