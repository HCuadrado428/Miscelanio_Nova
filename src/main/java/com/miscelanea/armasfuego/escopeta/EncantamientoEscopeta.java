package com.miscelanea.armasfuego.escopeta;

import com.miscelanea.registry.ModEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Encantamientos exclusivos de la escopeta de presión (salen en la mesa y en libros, como los de la ballesta).
 * Costes al estilo vanilla: {@code min(nivel) = base + (nivel - 1) * paso}, {@code max = min + 30}.
 */
public class EncantamientoEscopeta extends Enchantment {
    private final Presion.Encantamiento cual;
    private final int max, base, paso;

    public EncantamientoEscopeta(Presion.Encantamiento cual, Rarity rareza, int max, int base, int paso) {
        super(rareza, ModEnchantments.ESCOPETA_PRESION, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        this.cual = cual;
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

    /** Compresor y Válvula de seguridad no van juntos: una deja apurar más, la otra quita el riesgo. */
    @Override
    protected boolean checkCompatibility(Enchantment otro) {
        if (otro instanceof EncantamientoEscopeta e) {
            boolean a = cual == Presion.Encantamiento.COMPRESOR && e.cual == Presion.Encantamiento.VALVULA;
            boolean b = cual == Presion.Encantamiento.VALVULA && e.cual == Presion.Encantamiento.COMPRESOR;
            if (a || b) return false;
        }
        return super.checkCompatibility(otro);
    }
}
