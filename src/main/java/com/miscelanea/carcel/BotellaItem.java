package com.miscelanea.carcel;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Botella vacía para lanzar. Los ladrillos normales también se pueden lanzar con clic derecho. */
@Mod.EventBusSubscriber(modid = com.miscelanea.Miscelanea.MOD_ID)
public class BotellaItem extends Item {
    public BotellaItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        lanzar(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    static void lanzar(Level level, Player player, ItemStack stack) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            Arrojable a = new Arrojable(level, player, stack);
            a.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.3F, 1.0F);
            level.addFreshEntity(a);
        }
        player.getCooldowns().addCooldown(stack.getItem(), 15);
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack s = event.getItemStack();
        if (!s.is(Items.BRICK) && !s.is(Items.NETHER_BRICK)) return;
        if (event.getEntity().getCooldowns().isOnCooldown(s.getItem())) return;
        lanzar(event.getLevel(), event.getEntity(), s);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
}
