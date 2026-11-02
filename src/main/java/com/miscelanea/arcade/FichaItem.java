package com.miscelanea.arcade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Ficha de recreativa: una ficha, una partida. */
public class FichaItem extends Item {
    public FichaItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.ficha.ayuda").withStyle(ChatFormatting.GRAY));
    }

    /** Agachado con una ficha en la mano también se usa el puesto de feria (churrería: chocolate con churros). */
    @Override
    public boolean doesSneakBypassUse(net.minecraft.world.item.ItemStack st, net.minecraft.world.level.LevelReader l, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player p) {
        return l.getBlockState(pos).getBlock() instanceof com.miscelanea.feria.PuestoFeriaBlock;
    }
}
