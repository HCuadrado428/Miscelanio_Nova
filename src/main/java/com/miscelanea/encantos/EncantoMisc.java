package com.miscelanea.encantos;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Encantamiento de Miscelánea al estilo Enchancement: sin niveles (siempre "I", que funciona a tope), no sale en la mesa
 * de encantar vanilla ni en el botín al azar (se sacan con la mesa de elegir, a partir de libros en estanterías
 * cinceladas) y los aldeanos no lo venden.
 */
public class EncantoMisc extends Enchantment {
    public EncantoMisc(EnchantmentCategory c, EquipmentSlot... slots) {
        super(Rarity.RARE, c, slots);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int l) {
        return 15;
    }

    @Override
    public int getMaxCost(int l) {
        return 50;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack st) {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
