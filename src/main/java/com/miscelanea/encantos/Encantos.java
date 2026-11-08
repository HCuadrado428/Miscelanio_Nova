package com.miscelanea.encantos;

import com.miscelanea.registry.ModEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Los encantamientos nuevos (estilo Enchancement). Todos sin niveles. */
public final class Encantos {
    private Encantos() {}

    public static final EnchantmentCategory ESPADA = EnchantmentCategory.create("MISCELANEA_ESPADA", i -> i instanceof SwordItem);
    public static final EnchantmentCategory HACHA = EnchantmentCategory.create("MISCELANEA_HACHA", i -> i instanceof AxeItem);
    public static final EnchantmentCategory PICO = EnchantmentCategory.create("MISCELANEA_PICO", i -> i instanceof PickaxeItem);
    public static final EnchantmentCategory PALA = EnchantmentCategory.create("MISCELANEA_PALA", i -> i instanceof ShovelItem);
    public static final EnchantmentCategory AZADA = EnchantmentCategory.create("MISCELANEA_AZADA", i -> i instanceof HoeItem);

    static final List<RegistryObject<Enchantment>> TODOS = new ArrayList<>();

    private static RegistryObject<Enchantment> r(String id, EnchantmentCategory c, EquipmentSlot... s) {
        RegistryObject<Enchantment> o = ModEnchantments.ENCHANTMENTS.register(id, () -> new EncantoMisc(c, s));
        TODOS.add(o);
        return o;
    }

    private static final EquipmentSlot[] MANO = {EquipmentSlot.MAINHAND};
    // casco
    public static final RegistryObject<Enchantment> PERCEPCION = r("percepcion", EnchantmentCategory.ARMOR_HEAD, EquipmentSlot.HEAD);
    public static final RegistryObject<Enchantment> OJO_BUHO = r("ojo_buho", EnchantmentCategory.ARMOR_HEAD, EquipmentSlot.HEAD);
    // peto
    public static final RegistryObject<Enchantment> ADRENALINA = r("adrenalina", EnchantmentCategory.ARMOR_CHEST, EquipmentSlot.CHEST);
    public static final RegistryObject<Enchantment> ESPINAZO = r("espinazo", EnchantmentCategory.ARMOR_CHEST, EquipmentSlot.CHEST);
    public static final RegistryObject<Enchantment> VITALIDAD = r("vitalidad", EnchantmentCategory.ARMOR_CHEST, EquipmentSlot.CHEST);
    // pantalones
    public static final RegistryObject<Enchantment> RAFAGA = r("rafaga", EnchantmentCategory.ARMOR_LEGS, EquipmentSlot.LEGS);
    public static final RegistryObject<Enchantment> DESLIZAR = r("deslizar", EnchantmentCategory.ARMOR_LEGS, EquipmentSlot.LEGS);
    // botas
    public static final RegistryObject<Enchantment> REBOTE = r("rebote", EnchantmentCategory.ARMOR_FEET, EquipmentSlot.FEET);
    public static final RegistryObject<Enchantment> PEGAJOSO = r("pegajoso", EnchantmentCategory.ARMOR_FEET, EquipmentSlot.FEET);
    public static final RegistryObject<Enchantment> ZANCADA = r("zancada", EnchantmentCategory.ARMOR_FEET, EquipmentSlot.FEET);
    // espada
    public static final RegistryObject<Enchantment> BERSERKER = r("berserker", ESPADA, MANO);
    public static final RegistryObject<Enchantment> ESCARCHA = r("escarcha", ESPADA, MANO);
    public static final RegistryObject<Enchantment> VAMPIRO = r("vampiro", ESPADA, MANO);
    // hacha
    public static final RegistryObject<Enchantment> HENDIDURA = r("hendidura", HACHA, MANO);
    public static final RegistryObject<Enchantment> TALA = r("tala", HACHA, MANO);
    // herramientas
    public static final RegistryObject<Enchantment> PRESTEZA = r("presteza", EnchantmentCategory.DIGGER, MANO);
    public static final RegistryObject<Enchantment> VETA = r("veta", PICO, MANO);
    public static final RegistryObject<Enchantment> EXCAVAR = r("excavar", PALA, MANO);
    public static final RegistryObject<Enchantment> COSECHA = r("cosecha", AZADA, MANO);
    // a distancia
    public static final RegistryObject<Enchantment> CERTERO = r("certero", EnchantmentCategory.BOW, MANO);
    public static final RegistryObject<Enchantment> RECUPERACION = r("recuperacion", EnchantmentCategory.BOW, MANO);
    public static final RegistryObject<Enchantment> METRALLA = r("metralla", EnchantmentCategory.CROSSBOW, MANO);
    public static final RegistryObject<Enchantment> TORMENTA = r("tormenta", EnchantmentCategory.TRIDENT, MANO);
    public static final RegistryObject<Enchantment> ARPON = r("arpon", EnchantmentCategory.FISHING_ROD, MANO);

    /** Fuerza la carga de la clase (para que se registren antes de que el registro se cierre). */
    public static void cargar() {}

    public static List<Enchantment> todos() {
        List<Enchantment> l = new ArrayList<>();
        for (var o : TODOS) l.add(o.get());
        return l;
    }

    public static boolean tiene(ItemStack st, RegistryObject<Enchantment> e) {
        return !st.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(e.get(), st) > 0;
    }

    public static boolean tiene(LivingEntity le, EquipmentSlot s, RegistryObject<Enchantment> e) {
        return tiene(le.getItemBySlot(s), e);
    }
}
