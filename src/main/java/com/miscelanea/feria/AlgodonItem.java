package com.miscelanea.feria;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Algodón de azúcar (rosa, azul, blanco, amarillo o verde): subidón de azúcar y te devuelve el palito. */
public class AlgodonItem extends Item {
    public AlgodonItem(Properties p) {
        super(p.food(new FoodProperties.Builder().nutrition(3).saturationMod(0.2F).fast().alwaysEat()
                .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 1), 1F)
                .effect(() -> new MobEffectInstance(MobEffects.JUMP, 20 * 12, 0), 1F).build()));
    }

    public static int color(ItemStack st) {
        int c = st.hasTag() ? st.getTag().getInt("color") : 0;
        return MaquinaFeriaBlockEntity.COLORES[Math.floorMod(c, MaquinaFeriaBlockEntity.COLORES.length)];
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level l, LivingEntity e) {
        ItemStack r = super.finishUsingItem(st, l, e);
        if (e instanceof Player p && !p.getAbilities().instabuild) {
            if (r.isEmpty()) return new ItemStack(Items.STICK);
            if (!p.getInventory().add(new ItemStack(Items.STICK))) p.drop(new ItemStack(Items.STICK), false);
        }
        return r;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tt, TooltipFlag f) {
        int c = st.hasTag() ? st.getTag().getInt("color") : 0;
        tt.add(Component.translatable("miscelanea.color." + MaquinaFeriaBlockEntity.NOMBRES_COLOR[Math.floorMod(c, 5)]).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
