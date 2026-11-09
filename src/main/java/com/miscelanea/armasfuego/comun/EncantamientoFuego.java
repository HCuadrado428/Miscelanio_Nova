package com.miscelanea.armasfuego.comun;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.function.Supplier;

/**
 * Encantamiento propio de una de las armas de fuego de la segunda tanda (sale en la mesa de encantar y en libros, como
 * los del revólver o el rifle; solo vale para su arma).
 */
public class EncantamientoFuego extends Enchantment {
    private final int max, base, porNivel;
    private final Supplier<Enchantment> incompatible;

    public EncantamientoFuego(Rarity r, EnchantmentCategory c, int max, int base, int porNivel, Supplier<Enchantment> incompatible) {
        super(r, c, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        this.max = max;
        this.base = base;
        this.porNivel = porNivel;
        this.incompatible = incompatible;
    }

    public EncantamientoFuego(Rarity r, EnchantmentCategory c, int max, int base, int porNivel) {
        this(r, c, max, base, porNivel, null);
    }

    @Override
    public int getMaxLevel() {
        return max;
    }

    @Override
    public int getMinCost(int nivel) {
        return base + (nivel - 1) * porNivel;
    }

    @Override
    public int getMaxCost(int nivel) {
        return getMinCost(nivel) + 30;
    }

    @Override
    protected boolean checkCompatibility(Enchantment otro) {
        return super.checkCompatibility(otro) && (incompatible == null || otro != incompatible.get());
    }
}
