package com.miscelanea.carcel;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Pila: clic derecho con las gafas puestas para recargarlas. */
public class PilaItem extends Item {
    public PilaItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pila = player.getItemInHand(hand);
        ItemStack casco = player.getItemBySlot(EquipmentSlot.HEAD);
        if (casco.getItem() instanceof GafasItem && GafasItem.bateria(casco) < GafasItem.BATERIA_MAX) {
            if (!level.isClientSide) GafasItem.recargar(player, casco, pila);
            return InteractionResultHolder.sidedSuccess(pila, level.isClientSide);
        }
        return InteractionResultHolder.pass(pila);
    }
}
