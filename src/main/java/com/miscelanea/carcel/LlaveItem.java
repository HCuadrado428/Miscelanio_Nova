package com.miscelanea.carcel;

import com.miscelanea.Miscelanea;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Llave con símbolo. Abre la reja con el mismo símbolo.
 * En creativo: Shift + clic derecho al aire cambia el símbolo.
 */
public class LlaveItem extends Item {
    public LlaveItem(Properties properties) {
        super(properties);
    }

    public static int simbolo(ItemStack stack) {
        return stack.hasTag() ? Math.floorMod(stack.getTag().getInt(Simbolo.NBT), Simbolo.TOTAL) : 0;
    }

    public static ItemStack con(Item item, int simbolo) {
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putInt(Simbolo.NBT, simbolo);
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isCreative() && player.isShiftKeyDown()) {
            stack.getOrCreateTag().putInt(Simbolo.NBT, (simbolo(stack) + 1) % Simbolo.TOTAL);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("simbolo." + Miscelanea.MOD_ID + "." + Simbolo.NOMBRES[simbolo(stack)])
                .withStyle(ChatFormatting.GOLD));
    }
}
