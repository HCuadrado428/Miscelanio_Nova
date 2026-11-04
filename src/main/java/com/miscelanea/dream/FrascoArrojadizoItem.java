package com.miscelanea.dream;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Frasco de mezcla arrojadizo (mezcla con pólvora): se lanza como una poción arrojadiza. La poción lanzada del juego
 * lee los efectos y el color del propio frasco, así que el estallido salpica todos los efectos de la mezcla.
 */
public class FrascoArrojadizoItem extends ThrowablePotionItem {
    public FrascoArrojadizoItem(Properties p) {
        super(p);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level l, net.minecraft.world.entity.player.Player p, net.minecraft.world.InteractionHand h) {
        l.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.SPLASH_POTION_THROW, net.minecraft.sounds.SoundSource.PLAYERS,
                0.5F, 0.4F / (l.getRandom().nextFloat() * 0.4F + 0.8F));
        return super.use(l, p, h);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    @Override
    public String getDescriptionId(ItemStack st) {
        return getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        FrascoMezclaItem.lineaMezcla(st, tip, true);
        super.appendHoverText(st, l, tip, f);
    }
}
