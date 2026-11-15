package com.miscelanea.ropa;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * El material de la ropa cosmética (dos juegos: vaquero y bandido). Es ropa de cuero: protege lo mismo que una armadura de cuero
 * (1, 3, 2 y 1) y dura algo más; lo que cambia es cómo se ve. La capa de textura sale de {@code textures/models/armor/<nombre>_layer_N.png}.
 */
public enum Ropa implements ArmorMaterial {
    VAQUERO("vaquero"),
    BANDIDO("bandido");

    private static final int[] VIDA = {13, 15, 16, 11};   // casco, peto, pantalón, botas (el de cuero, por 8 en vez de por 5)
    private static final int[] DEFENSA = {1, 3, 2, 1};

    private final String nombre;

    Ropa(String nombre) {
        this.nombre = nombre;
    }

    private static int pos(ArmorItem.Type t) {
        return switch (t) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type t) {
        return VIDA[pos(t)] * 8;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type t) {
        return DEFENSA[pos(t)];
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.LEATHER);
    }

    @Override
    public String getName() {
        return com.miscelanea.Miscelanea.MOD_ID + ":" + nombre;
    }

    @Override
    public float getToughness() {
        return 0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0F;
    }

    // ------------------------------------------------------------------ perks de llevarla puesta
    // Vaquero (puntería): dispara más recto, recarga más deprisa y el cobre se oxida más despacio.
    // Bandido (sigilo): los disparos se oyen menos y a veces no se gasta la bala.
    // Cada pieza suma; el juego completo (casco, peto, pantalón y botas del mismo) da un extra. Todo en la config, sección «ropa».

    /** Cuántas piezas de este juego lleva puestas (0 a 4). */
    public static int piezas(net.minecraft.world.entity.LivingEntity e, Ropa juego) {
        if (e == null) return 0;
        int n = 0;
        for (net.minecraft.world.entity.EquipmentSlot s : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET})
            if (e.getItemBySlot(s).getItem() instanceof ArmorItem a && a.getMaterial() == juego) n++;
        return n;
    }

    /** Lo que suma el juego: por pieza, más el extra si lo lleva entero. */
    private static double suma(net.minecraft.world.entity.LivingEntity e, Ropa juego, double porPieza, double extra) {
        if (!com.miscelanea.ConfigServidor.ropaPerks()) return 0;
        int n = piezas(e, juego);
        return n * porPieza + (n >= 4 ? extra : 0);
    }

    /** Por cuánto se multiplica la dispersión del que dispara (Vaquero: más puntería). */
    public static double dispersion(net.minecraft.world.entity.LivingEntity e) {
        return Math.max(0.3, 1 - suma(e, VAQUERO, com.miscelanea.ConfigServidor.ropaPrecision(), com.miscelanea.ConfigServidor.ropaPrecisionExtra()));
    }

    /** Por cuánto se multiplican los ticks de recarga (Vaquero: recarga más rápida). */
    public static double recarga(net.minecraft.world.entity.LivingEntity e) {
        return Math.max(0.4, 1 - suma(e, VAQUERO, com.miscelanea.ConfigServidor.ropaRecarga(), com.miscelanea.ConfigServidor.ropaRecargaExtra()));
    }

    /** Los disparos que aguanta el cobre antes de oxidarse una fase (Vaquero: se oxida más despacio). */
    public static int usosPorFase(net.minecraft.world.entity.LivingEntity e, int usos) {
        double menos = Math.min(0.8, suma(e, VAQUERO, com.miscelanea.ConfigServidor.ropaOxido(), 0));
        return menos <= 0 ? usos : (int) Math.round(usos / (1 - menos));
    }

    /** Por cuánto se multiplica la distancia a la que se oyen sus disparos (Bandido: menos ruido). */
    public static double ruido(net.minecraft.world.entity.LivingEntity e) {
        return Math.max(0.2, 1 - suma(e, BANDIDO, com.miscelanea.ConfigServidor.ropaRuido(), com.miscelanea.ConfigServidor.ropaRuidoExtra()));
    }

    /** ¿Este disparo no gasta bala? (Bandido: una probabilidad por pieza.) */
    public static boolean ahorraBala(net.minecraft.world.entity.LivingEntity e) {
        double prob = suma(e, BANDIDO, com.miscelanea.ConfigServidor.ropaAhorro(), com.miscelanea.ConfigServidor.ropaAhorroExtra());
        return prob > 0 && e.getRandom().nextDouble() < prob;
    }
}
