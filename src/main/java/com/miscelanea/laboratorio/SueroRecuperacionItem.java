package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** El suero de recuperación: quita todos los efectos y cura al que se lo toma o se lo inyectan. Sale de incubar un queso de laboratorio. */
public class SueroRecuperacionItem extends Item {
    public SueroRecuperacionItem(Properties p) {
        super(p);
    }

    /** Lo que hace: sin efectos y con vida (hasta 10 puntos). */
    public static void curar(LivingEntity v) {
        v.removeAllEffects();
        v.heal(10F);
        v.setTicksFrozen(0);
        v.clearFire();
    }

    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.DRINK; }
    @Override public int getUseDuration(ItemStack s) { return 32; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        return ItemUtils.startUsingInstantly(l, p, h);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level l, LivingEntity e) {
        if (!l.isClientSide) curar(e);
        return vaciar(s, e);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack s, Player p, LivingEntity v, InteractionHand h) {
        if (!p.level().isClientSide) {
            curar(v);
            p.setItemInHand(h, vaciar(s, p));
        }
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    private static ItemStack vaciar(ItemStack s, LivingEntity quien) {
        if (quien instanceof Player p && p.getAbilities().instabuild) return s;
        s.shrink(1);
        ItemStack tubo = new ItemStack(LabRegistro.TUBO_ENSAYO.get());
        if (s.isEmpty()) return tubo;
        if (quien instanceof Player p && !p.getInventory().add(tubo)) p.drop(tubo, false);
        return s;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.suero_recuperacion.desc").withStyle(ChatFormatting.GRAY));
    }
}
