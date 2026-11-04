package com.miscelanea.bromas;

import com.miscelanea.ConfigServidor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Poción arrojadiza de controles cambiados (botella de poción arrojadiza del juego con el líquido verde ácido). Se lanza
 * como una normal; a quien pilla la salpicadura le cambia W↔S y A↔D un rato ({@link PocionControlesEntity}).
 */
public class PocionControlesItem extends Item {
    public PocionControlesItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (!ConfigServidor.bromaControles()) {
            if (!l.isClientSide) p.displayClientMessage(Component.translatable("msg.miscelanea.broma.desactivada").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(st);
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5F,
                0.4F / (l.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!l.isClientSide) {
            PocionControlesEntity e = new PocionControlesEntity(l, p);
            e.setItem(st);
            e.shootFromRotation(p, p.getXRot(), p.getYRot(), -20.0F, 0.5F, 1.0F);
            l.addFreshEntity(e);
        }
        p.awardStat(Stats.ITEM_USED.get(this));
        int enfriar = ConfigServidor.bromaEnfriamientoTicks();
        if (enfriar > 0) p.getCooldowns().addCooldown(this, Math.min(enfriar, 40));
        if (!p.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        l.add(Component.translatable("item.miscelanea.pocion_controles.ayuda", ConfigServidor.bromaControlesTicks() / 20).withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.pocion_controles.ayuda2").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
