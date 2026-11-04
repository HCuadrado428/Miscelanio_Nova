package com.miscelanea.dream;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Esencia de Dream (solo de creativo): un vial de veneno verde oscuro. Al beberlo da «Mordisco venenoso» durante 5
 * minutos. También se puede echar al caldero de mezclas.
 */
public class EsenciaDreamItem extends Item {
    public EsenciaDreamItem(Properties p) {
        super(p);
    }

    public static MobEffectInstance efecto() {
        return new MobEffectInstance(DreamRegistro.MORDISCO.get(), Mezclas.DURACION_ESENCIA);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level l, LivingEntity e) {
        Player p = e instanceof Player pl ? pl : null;
        if (p instanceof ServerPlayer sp) CriteriaTriggers.CONSUME_ITEM.trigger(sp, st);
        if (!l.isClientSide) {
            e.addEffect(efecto());
            l.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.35F, 1.7F);
        }
        if (p != null) {
            p.awardStat(Stats.ITEM_USED.get(this));
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
        if (p == null || !p.getAbilities().instabuild) {
            if (st.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (p != null) p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        e.gameEvent(GameEvent.DRINK);
        return st;
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        return ItemUtils.startUsingInstantly(l, p, h);
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        MobEffectInstance e = efecto();
        tip.add(Component.translatable("potion.withDuration", Component.translatable(e.getDescriptionId()), MobEffectUtil.formatDuration(e, 1.0F))
                .withStyle(ChatFormatting.DARK_GREEN));
        tip.add(Component.empty());
        tip.add(Component.translatable("item.miscelanea.esencia_dream.lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tip.add(Component.translatable("item.miscelanea.esencia_dream.lore2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tip.add(Component.translatable("item.miscelanea.esencia_dream.uso").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
