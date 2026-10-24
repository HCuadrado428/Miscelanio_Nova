package com.miscelanea.aspira;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

/** Lo que hace la boquilla en cada uso. ASPIRAR es el clic derecho normal; el resto van con Shift + clic derecho. */
public enum Modo {
    ASPIRAR("aspirar", true),
    DESTELLO("destello", true),       // mantener para cargar, soltar para cegar
    DESATASCADOR("desatascador", false), // disparo instantáneo
    SOPLIDO("soplido", true),         // mantener para soplar; toque rápido = cañón de capturas
    TURBO("turbo", false),            // impulso instantáneo
    ALQUIMISTA("alquimista", true),   // mantener para aspirar pociones; toque rápido = rociar
    SOPLAR("soplar", true);           // Shift sin encantamiento: soplido básico

    private final String id;
    private final boolean mantener;

    Modo(String id, boolean mantener) {
        this.id = id;
        this.mantener = mantener;
    }

    public String id() {
        return id;
    }

    /** true si el modo se usa manteniendo el botón, false si es instantáneo. */
    public boolean mantener() {
        return mantener;
    }

    /** El modo especial (Shift) que da el encantamiento de la boquilla, o null si no tiene ninguno. */
    @Nullable
    public static Modo especial(ItemStack stack) {
        for (Enchantment ench : EnchantmentHelper.getEnchantments(stack).keySet()) {
            if (ench instanceof ModoEnchantment modo) {
                return modo.modo();
            }
        }
        return null;
    }

    public static Modo byOrdinal(int i) {
        Modo[] values = values();
        return i >= 0 && i < values.length ? values[i] : ASPIRAR;
    }
}
