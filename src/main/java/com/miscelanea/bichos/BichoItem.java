package com.miscelanea.bichos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Un bicho atrapado o un pez pescado. Los peces guardan su tamaño (cm). Se pueden donar en una vitrina del museo. */
public class BichoItem extends Item {
    public final Especie especie;

    public BichoItem(Especie especie, Properties p) {
        super(p);
        this.especie = especie;
    }

    public static int cm(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt("cm") : 0;
    }

    @Override
    public Component getName(ItemStack s) {
        int cm = cm(s);
        Component base = super.getName(s);
        return cm > 0 ? Component.translatable("item.miscelanea.pez_con_tamano", base, cm) : base;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.literal("★".repeat(especie.estrellas) + "☆".repeat(5 - especie.estrellas)).withStyle(ChatFormatting.GOLD));
        lista.add(Component.translatable("bichopedia.miscelanea.donar").withStyle(ChatFormatting.GRAY));
    }
}
