package com.miscelanea.feria;

import com.miscelanea.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Chapa coleccionable del gachapón: 8 dibujos y una de oro. */
public class ChapaItem extends Item {
    public static final String[] DIBUJOS = {"estrella", "corazon", "rayo", "luna", "sol", "seta", "fantasma", "calavera", "oro"};
    public static final int ORO = 8;

    public ChapaItem(Properties p) {
        super(p);
    }

    public static int dibujo(ItemStack st) {
        return st.hasTag() ? Math.floorMod(st.getTag().getInt("chapa"), DIBUJOS.length) : 0;
    }

    public static ItemStack de(int d) {
        ItemStack st = new ItemStack(ModItems.CHAPA.get());
        st.getOrCreateTag().putInt("chapa", d);
        return st;
    }

    @Override
    public Component getName(ItemStack st) {
        return Component.translatable("item.miscelanea.chapa." + DIBUJOS[dibujo(st)]);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.chapa.ayuda", dibujo(s) + 1).withStyle(dibujo(s) == ORO ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return dibujo(st) == ORO;
    }
}
