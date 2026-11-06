package com.miscelanea.tortura.efectos;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Saco de arpillera del Espantapájaros (la cabeza de sus sustos). Se puede llevar puesto como un casco, de adorno. */
public class CabezaEspantapajarosItem extends Item implements Equipable {
    public CabezaEspantapajarosItem(Properties p) {
        super(p);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        return swapWithEquipmentSlot(this, l, p, h);
    }
}
