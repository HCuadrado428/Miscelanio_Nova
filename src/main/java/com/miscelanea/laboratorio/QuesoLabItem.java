package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** El queso de laboratorio, premio del laberinto de la rata: se come siempre y da un efecto absurdo al azar. */
public class QuesoLabItem extends Item {
    private static final MobEffect[] EFECTOS = {MobEffects.MOVEMENT_SPEED, MobEffects.JUMP, MobEffects.GLOWING, MobEffects.SLOW_FALLING,
            MobEffects.NIGHT_VISION, MobEffects.CONFUSION, MobEffects.LEVITATION, MobEffects.INVISIBILITY};

    public QuesoLabItem(Properties p) {
        super(p.food(new FoodProperties.Builder().nutrition(4).saturationMod(0.5F).alwaysEat().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level l, LivingEntity e) {
        if (!l.isClientSide) {
            MobEffect m = EFECTOS[l.random.nextInt(EFECTOS.length)];
            e.addEffect(new MobEffectInstance(m, m == MobEffects.LEVITATION ? 60 : 200, 0));
        }
        return super.finishUsingItem(st, l, e);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.queso_laboratorio.desc").withStyle(ChatFormatting.GRAY));
    }
}
