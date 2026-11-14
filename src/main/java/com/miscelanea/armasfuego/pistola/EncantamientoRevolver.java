package com.miscelanea.armasfuego.pistola;

import com.miscelanea.registry.ModEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.function.Supplier;

/**
 * Encantamientos exclusivos del revólver (salen en la mesa de encantar y en libros, como los de la ballesta):
 * Abanico, Carambola, Desenfunde y Señalero. Abanico y Desenfunde no van juntos (o vacías el tambor a lo loco o
 * apuntas el primer tiro).
 */
public class EncantamientoRevolver extends Enchantment {
    private final int max, base, porNivel;
    private final Supplier<Enchantment> incompatible;

    public EncantamientoRevolver(Rarity r, int max, int base, int porNivel, Supplier<Enchantment> incompatible) {
        super(r, ModEnchantments.REVOLVER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        this.max = max;
        this.base = base;
        this.porNivel = porNivel;
        this.incompatible = incompatible;
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

    public static int nivel(Supplier<Enchantment> e, ItemStack s) {
        return EnchantmentHelper.getItemEnchantmentLevel(e.get(), s);
    }
}
