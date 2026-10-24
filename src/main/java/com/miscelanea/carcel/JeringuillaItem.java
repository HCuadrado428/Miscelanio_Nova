package com.miscelanea.carcel;

import com.miscelanea.registry.ModEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Jeringuilla que se pincha en el brazo (mantener clic derecho 1,5 s).
 * - Antídoto: quita la psicosis y te protege del gas 60 s.
 * - Medicina: cura 8 corazones poco a poco.
 */
public class JeringuillaItem extends Item {
    private final boolean antidoto;

    public JeringuillaItem(Properties p, boolean antidoto) {
        super(p);
        this.antidoto = antidoto;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 30;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        if (!level.isClientSide) {
            if (antidoto) {
                e.removeEffect(ModEffects.PSICOSIS.get());
                e.removeEffect(MobEffects.CONFUSION);
                e.addEffect(new MobEffectInstance(ModEffects.INMUNIDAD_GAS.get(), 1200, 0));
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 2));
            }
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
        if (e instanceof Player p) {
            p.getCooldowns().addCooldown(this, 40);
            if (!p.getAbilities().instabuild) stack.shrink(1);
        }
        return stack;
    }
}
