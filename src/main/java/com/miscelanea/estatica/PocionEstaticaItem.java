package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Poción de estática: un frasco de poción normal con nieve de televisión dentro (la textura del líquido es animada). Se bebe
 * como cualquier poción (32 ticks) y da «Estática»; quien la bebe ve su transformación en tercera persona, de frente
 * (los demás ven la animación del cuerpo). Deja el frasco vacío.
 */
public class PocionEstaticaItem extends Item {
    public PocionEstaticaItem(Properties p) {
        super(p);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 32;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand mano) {
        if (Estatica.enCinematica(p)) return InteractionResultHolder.fail(p.getItemInHand(mano));
        return ItemUtils.startUsingInstantly(l, p, mano);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level l, LivingEntity e) {
        Player p = e instanceof Player pl ? pl : null;
        if (p instanceof ServerPlayer sp) CriteriaTriggers.CONSUME_ITEM.trigger(sp, st);
        if (!l.isClientSide) {
            e.addEffect(new MobEffectInstance(EstaticaRegistro.EFECTO.get(), ConfigServidor.estaticaDuracionTicks(), 0, false, true, true));
            if (p instanceof ServerPlayer sp) Estatica.transformar(sp);
        }
        if (p != null) {
            p.awardStat(Stats.ITEM_USED.get(this));
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
        if (p == null || !p.getAbilities().instabuild) {
            if (st.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (p != null) p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        return st;
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("effect.miscelanea.estatica").withStyle(ChatFormatting.GRAY));
        tip.add(Component.empty());
        tip.add(Component.translatable("item.miscelanea.pocion_estatica.lore1").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tip.add(Component.translatable("item.miscelanea.pocion_estatica.lore2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tip.add(Component.translatable("item.miscelanea.pocion_estatica.lore3").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
