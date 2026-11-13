package com.miscelanea.espera.eventos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ficha infinita de la Recreativa Infinita: vale en cualquier recreativa como una ficha normal, pero no se gasta
 * (créditos: ∞). En los puestos de la feria no la aceptan: "eso no es moneda de curso legal".
 */
public class FichaInfinitaItem extends com.miscelanea.arcade.FichaItem {
    public FichaInfinitaItem(Properties p) {
        super(p);
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.ficha_infinita.ayuda").withStyle(ChatFormatting.GRAY));
        lista.add(Component.translatable("item.miscelanea.ficha_infinita.creditos").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack st, net.minecraft.world.level.LevelReader l, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player p) {
        return false;
    }
}
