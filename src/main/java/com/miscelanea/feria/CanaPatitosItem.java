package com.miscelanea.feria;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Caña de la pesca de patitos: apunta a un pato del estanque y clic derecho. */
public class CanaPatitosItem extends Item {
    public CanaPatitosItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        p.getCooldowns().addCooldown(this, 15);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.6F, 1.2F);
        if (!l.isClientSide) {
            PuestoFeriaBlockEntity be = PuestoFeriaBlockEntity.buscar(l, p);
            if (be != null) be.pescar(p, p.getEyePosition(), p.getLookAngle());
        }
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
    }
}
