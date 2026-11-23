package com.miscelanea.laboratorio;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * La ropa del científico: gafas de seguridad, bata y pantalón de laboratorio. Protege poco (como la de cuero); lo suyo es
 * que las salpicaduras de los sueros te duran menos: la bata un 50 %, las gafas un 25 % y el pantalón un 10 %, y se suman.
 */
public enum LabRopa implements ArmorMaterial {
    CIENTIFICO;

    private static final int[] VIDA = {10, 12, 11, 8};
    private static final int[] DEFENSA = {1, 2, 1, 1};

    private static int pos(ArmorItem.Type t) {
        return switch (t) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    @Override public int getDurabilityForType(ArmorItem.Type t) { return VIDA[pos(t)] * 7; }
    @Override public int getDefenseForType(ArmorItem.Type t) { return DEFENSA[pos(t)]; }
    @Override public int getEnchantmentValue() { return 10; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.WHITE_WOOL); }
    @Override public String getName() { return com.miscelanea.Miscelanea.MOD_ID + ":cientifico"; }
    @Override public float getToughness() { return 0F; }
    @Override public float getKnockbackResistance() { return 0F; }

    /** Cuánto dura un efecto de suero (1 = entero) en quien lleva puesto lo que lleva. */
    public static double factorSuero(LivingEntity v) {
        double f = 1.0;
        if (v.getItemBySlot(EquipmentSlot.CHEST).is(LabRegistro.BATA.get())) f *= 0.5;
        if (v.getItemBySlot(EquipmentSlot.HEAD).is(LabRegistro.GAFAS.get())) f *= 0.75;
        if (v.getItemBySlot(EquipmentSlot.LEGS).is(LabRegistro.PANTALON_LAB.get())) f *= 0.9;
        return f;
    }
}
