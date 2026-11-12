package com.miscelanea.armasfuego.rifle;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Encantamientos exclusivos del rifle de catalejo (solo valen para él, como Multidisparo para la ballesta). Salen en
 * la mesa de encantar y en libros.
 */
public class RifleEncantamiento extends Enchantment {
    private final int max, base, paso;

    public RifleEncantamiento(Rarity r, int max, int base, int paso, EnchantmentCategory c) {
        super(r, c, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        this.max = max;
        this.base = base;
        this.paso = paso;
    }

    @Override
    public int getMaxLevel() {
        return max;
    }

    @Override
    public int getMinCost(int nivel) {
        return base + (nivel - 1) * paso;
    }

    @Override
    public int getMaxCost(int nivel) {
        return getMinCost(nivel) + 30;
    }
}
