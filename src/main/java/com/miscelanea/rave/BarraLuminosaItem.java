package com.miscelanea.rave;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Barra luminosa de neón (16 colores): clic derecho la parte y la lanza; donde cae se queda alumbrando 5 minutos. */
public class BarraLuminosaItem extends Item {
    public BarraLuminosaItem(Properties p) {
        super(p);
    }

    public static ItemStack de(DyeColor c) {
        ItemStack s = new ItemStack(RaveRegistro.BARRA.get());
        s.getOrCreateTag().putString("color", c.getName());
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player j, InteractionHand m) {
        ItemStack s = j.getItemInHand(m);
        l.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.4F, 1.8F);
        if (!l.isClientSide) {
            BarraLanzada b = new BarraLanzada(l, j);
            ItemStack una = s.copy();
            una.setCount(1);
            b.setItem(una);
            b.shootFromRotation(j, j.getXRot(), j.getYRot(), 0.0F, 1.1F, 1.0F);
            l.addFreshEntity(b);
        }
        j.getCooldowns().addCooldown(this, 8);
        if (!j.getAbilities().instabuild) s.shrink(1);
        return InteractionResultHolder.sidedSuccess(s, l.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
        t.add(Component.translatable("color.minecraft." + Neon.de(s).getName()).withStyle(ChatFormatting.GRAY));
        t.add(Component.translatable("item.miscelanea.barra_luminosa.ayuda").withStyle(ChatFormatting.DARK_GRAY));
    }
}
