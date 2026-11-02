package com.miscelanea.tortura;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Llave de la trampa. La que sale de una caja Jigsaw solo abre SU trampa (guarda el número); la del creativo es maestra.
 * Clic derecho: te quitas la trampa. Clic derecho a otro: se la quitas a él.
 */
public class LlaveTrampaItem extends ObjetoTortura {
    public LlaveTrampaItem(Properties p) {
        super(p, 1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!level.isClientSide) TrampaOso.abrirCon(p, p, s);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return s.getTag() != null && s.getTag().getInt("Trampa") != 0;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        int id = s.getTag() == null ? 0 : s.getTag().getInt("Trampa");
        l.add((id == 0 ? Component.translatable("item.miscelanea.llave_trampa.maestra")
                : Component.translatable("item.miscelanea.llave_trampa.numero", id)).withStyle(ChatFormatting.GOLD));
        super.appendHoverText(s, level, l, f);
    }
}
