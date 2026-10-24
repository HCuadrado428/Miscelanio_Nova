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

    // ---- Cárcel ----
    /** Llave con un símbolo: abre las rejas con el mismo símbolo. */
    public static final RegistryObject<Item> LLAVE = ITEMS.register("llave",
            () -> new com.miscelanea.carcel.LlaveItem(new Item.Properties().stacksTo(16)));
    /** El chivato atado a la silla con ruedas (se coloca en raíles). */
    public static final RegistryObject<Item> CARRO_CHIVATO = ITEMS.register("carro_chivato",
            () -> new com.miscelanea.carcel.CarroChivatoItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BOTELLA = ITEMS.register("botella",
            () -> new com.miscelanea.carcel.BotellaItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PILA = ITEMS.register("pila",
            () -> new com.miscelanea.carcel.PilaItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> GAFAS = ITEMS.register("gafas_nocturnas",
            () -> new com.miscelanea.carcel.GafasItem(com.miscelanea.carcel.GafasMaterial.INSTANCE,
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ANTIDOTO = ITEMS.register("antidoto",
            () -> new com.miscelanea.carcel.JeringuillaItem(new Item.Properties().stacksTo(8), true));
    public static final RegistryObject<Item> MEDICINA = ITEMS.register("medicina",
            () -> new com.miscelanea.carcel.JeringuillaItem(new Item.Properties().stacksTo(8), false));

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

    private ModItems() {}
}
