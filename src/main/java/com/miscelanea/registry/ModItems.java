package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.AspirafantasmasItem;
import com.miscelanea.aspira.MochilaArmorMaterial;
import com.miscelanea.aspira.MochilaItem;
import com.miscelanea.porra.BateriaItem;
import com.miscelanea.porra.PorraItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);

    /** La boquilla: el objeto que se usa con el clic derecho. */
    public static final RegistryObject<Item> ASPIRAFANTASMAS = ITEMS.register("aspirafantasmas",
            () -> new AspirafantasmasItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** La mochila: se lleva en el pecho, guarda las capturas y el tanque de pociones. */
    public static final RegistryObject<Item> MOCHILA = ITEMS.register("aspirafantasmas_mochila",
            () -> new MochilaItem(MochilaArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Solo se usa como icono del proyectil del desatascador. */
    public static final RegistryObject<Item> DESATASCADOR = ITEMS.register("desatascador",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** Porra eléctrica: agarra y da descargas. Necesita la batería a la espalda. */
    public static final RegistryObject<Item> PORRA = ITEMS.register("porra",
            () -> new PorraItem(Tiers.IRON, 3, -2.4F, new Item.Properties().rarity(Rarity.RARE)));

    /** Batería de coche que se lleva a la espalda (hueco del pecho). */
    public static final RegistryObject<Item> BATERIA = ITEMS.register("bateria",
            () -> new BateriaItem(MochilaArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Medigun: rayo que cura y ÜberCarga. Necesita la mochila médica. */
    public static final RegistryObject<Item> MEDIGUN = ITEMS.register("medigun",
            () -> new com.miscelanea.medigun.MedigunItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Mochila médica (hueco del pecho). */
    public static final RegistryObject<Item> MOCHILA_MEDICA = ITEMS.register("mochila_medica",
            () -> new com.miscelanea.medigun.MochilaMedicaItem(MochilaArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Núcleo de error en el pecho (solo creativo): Shift + clic derecho con la mano vacía = aliento. */
    public static final RegistryObject<Item> NUCLEO = ITEMS.register("nucleo",
            () -> new com.miscelanea.nucleo.NucleoItem(MochilaArmorMaterial.INSTANCE,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // ---- Splatoon
    public static final RegistryObject<Item> LANZATINTAS = ITEMS.register("lanzatintas",
            () -> new com.miscelanea.tinta.LanzatintasItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<Item> RODILLO = ITEMS.register("rodillo",
            () -> new com.miscelanea.tinta.RodilloItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    // ---- Half-Life 2
    public static final RegistryObject<Item> PISTOLA_GRAVEDAD = ITEMS.register("pistola_gravedad",
            () -> new com.miscelanea.gravedad.GravedadItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ---- Doom / TF2
    public static final RegistryObject<Item> SUPERESCOPETA = ITEMS.register("superescopeta",
            () -> new com.miscelanea.armas.EscopetaItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> LANZACOHETES = ITEMS.register("lanzacohetes",
            () -> new com.miscelanea.armas.LanzacohetesItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> CARTUCHOS = ITEMS.register("cartuchos", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COHETE = ITEMS.register("cohete", () -> new Item(new Item.Properties().stacksTo(16)));

    private ModItems() {}
}
