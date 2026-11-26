package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Un informe impreso: una ficha del cuaderno en papel (estación, sujeto, nivel, día y lecturas). */
public class InformeItem extends Item {
    public InformeItem(Properties p) {
        super(p);
    }

    /** Un informe con esa ficha. */
    public static ItemStack con(CompoundTag ficha) {
        ItemStack s = new ItemStack(LabRegistro.INFORME.get());
        s.getOrCreateTag().put("Ficha", ficha.copy());
        return s;
    }

    @Override
    public Component getName(ItemStack s) {
        if (s.getTag() == null || !s.getTag().contains("Ficha")) return Component.translatable("item.miscelanea.informe_laboratorio");
        return Component.translatable("item.miscelanea.informe_laboratorio.de", Component.translatable("laboratorio.miscelanea.estacion." + s.getTag().getCompound("Ficha").getString("Estacion")));
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        if (s.getTag() == null || !s.getTag().contains("Ficha")) return;
        CompoundTag ficha = s.getTag().getCompound("Ficha");
        tip.add(Component.translatable("laboratorio.miscelanea.cuaderno.sujeto", ficha.getString("Sujeto")).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("laboratorio.miscelanea.cuaderno.nivel", Component.translatable("laboratorio.miscelanea.nivel." + ficha.getInt("Nivel"))).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("laboratorio.miscelanea.cuaderno.dia", ficha.getLong("Dia")).withStyle(ChatFormatting.GRAY));
        ListTag ls = ficha.getList("Lecturas", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size(); i++) {
            CompoundTag lec = ls.getCompound(i);
            String clave = "laboratorio.miscelanea.valor." + lec.getString("V");
            Component valor = net.minecraft.locale.Language.getInstance().has(clave) ? Component.translatable(clave) : Component.literal(lec.getString("V"));
            tip.add(Component.translatable("laboratorio.miscelanea.lectura." + lec.getString("K")).append(": ").append(valor).withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
