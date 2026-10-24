package com.miscelanea.aspira;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Encantamiento de modo. Solo uno por aspiradora (son incompatibles entre sí, como Riptide y Loyalty).
 */
public class ModoEnchantment extends Enchantment {
    private final Modo modo;

    public ModoEnchantment(Modo modo, EnchantmentCategory category) {
        super(Rarity.RARE, category, new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
        this.modo = modo;
    }

    public Modo modo() {
        return modo;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 15;
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other) && !(other instanceof ModoEnchantment);
    }
}
