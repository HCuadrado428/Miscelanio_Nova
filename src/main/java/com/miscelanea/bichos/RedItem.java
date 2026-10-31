package com.miscelanea.bichos;

import com.miscelanea.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/** Red para cazar bichos: clic derecho para pasar la red; atrapa el bicho que haya delante. */
public class RedItem extends Item {
    public RedItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, 12);
        if (player instanceof ServerPlayer sp) {
            Vec3 ojo = sp.getEyePosition(), mira = sp.getViewVector(1);
            List<BichoEntity> cerca = level.getEntitiesOfClass(BichoEntity.class, sp.getBoundingBox().inflate(4.0), b -> {
                Vec3 d = b.position().add(0, 0.15, 0).subtract(ojo);
                return d.length() < 4.0 && d.normalize().dot(mira) > 0.55;
            });
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.6F);
            cerca.stream().min(Comparator.comparingDouble(b -> b.distanceToSqr(sp))).ifPresent(b -> {
                Especie e = b.especie();
                b.discard();
                ItemStack bicho = new ItemStack(ModItems.bicho(e));
                if (!sp.getInventory().add(bicho)) sp.drop(bicho, false);
                ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, b.getX(), b.getY() + 0.2, b.getZ(), 6, 0.2, 0.2, 0.2, 0);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.3F);
                Coleccion.atrapar(sp, e, 0);
                s.hurtAndBreak(1, sp, x -> x.broadcastBreakEvent(hand));
            });
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.red.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
