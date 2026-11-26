package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** El cronómetro: clic derecho para arrancar y para parar (te dice el tiempo); agachado, lo pone a cero. */
public class CronometroItem extends Item {
    public CronometroItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        if (!l.isClientSide) {
            CompoundTag t = st.getOrCreateTag();
            if (p.isShiftKeyDown()) {
                t.remove("Inicio");
                t.remove("Ultimo");
                p.displayClientMessage(Component.translatable("laboratorio.miscelanea.cronometro.cero"), true);
            } else if (t.contains("Inicio")) {
                int seg = (int) ((l.getGameTime() - t.getLong("Inicio")) / 20);
                t.remove("Inicio");
                t.putInt("Ultimo", seg);
                p.displayClientMessage(Component.translatable("laboratorio.miscelanea.cronometro.parado", seg).withStyle(ChatFormatting.YELLOW), true);
            } else {
                t.putLong("Inicio", l.getGameTime());
                p.displayClientMessage(Component.translatable("laboratorio.miscelanea.cronometro.marcha").withStyle(ChatFormatting.GREEN), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return st.getTag() != null && st.getTag().contains("Inicio");
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        if (st.getTag() != null && st.getTag().contains("Ultimo"))
            tip.add(Component.translatable("laboratorio.miscelanea.cronometro.ultimo", st.getTag().getInt("Ultimo")).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.miscelanea.cronometro.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
