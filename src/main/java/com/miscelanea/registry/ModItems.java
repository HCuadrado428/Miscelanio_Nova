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

    // ---- Kingdom Hearts
    public static final RegistryObject<Item> LLAVE_ESPADA = ITEMS.register("llave_espada",
            () -> new com.miscelanea.llave.LlaveEspadaItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // ---- God of War
    public static final RegistryObject<Item> HACHA_LEVIATAN = ITEMS.register("hacha_leviatan",
            () -> new com.miscelanea.hacha.HachaItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** Solo para dibujar el escudo plegable de Kratos en el brazo. */
    public static final RegistryObject<Item> ESCUDO_GUARDIAN = ITEMS.register("escudo_guardian",
            () -> new com.miscelanea.hacha.EscudoItem(new Item.Properties().stacksTo(1)));

    // ---- Fallout
    public static final RegistryObject<Item> PIPBOY = ITEMS.register("pipboy",
            () -> new com.miscelanea.pipboy.PipBoyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ---- Animal Crossing / Stardew: bichos, peces y museo
    public static final RegistryObject<Item> RED = ITEMS.register("red",
            () -> new com.miscelanea.bichos.RedItem(new Item.Properties().durability(240)));
    public static final RegistryObject<Item> CANA = ITEMS.register("cana",
            () -> new com.miscelanea.bichos.CanaItem(new Item.Properties().durability(128)));
    public static final RegistryObject<Item> BICHOPEDIA = ITEMS.register("bichopedia",
            () -> new com.miscelanea.bichos.BichopediaItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> VITRINA = ITEMS.register("vitrina",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.VITRINA.get(), new Item.Properties()));
    private static final java.util.Map<com.miscelanea.bichos.Especie, RegistryObject<Item>> BICHOS = new java.util.EnumMap<>(com.miscelanea.bichos.Especie.class);
    static {
        for (com.miscelanea.bichos.Especie e : com.miscelanea.bichos.Especie.values())
            BICHOS.put(e, ITEMS.register(e.item(), () -> new com.miscelanea.bichos.BichoItem(e, new Item.Properties().stacksTo(e.esPez() ? 16 : 64))));
    }

    // ---- Muebles
    public static final RegistryObject<Item> SILLA = ITEMS.register("silla", () -> new net.minecraft.world.item.BlockItem(ModBlocks.SILLA.get(), new Item.Properties()));
    public static final RegistryObject<Item> SOFA = ITEMS.register("sofa", () -> new net.minecraft.world.item.BlockItem(ModBlocks.SOFA.get(), new Item.Properties()));
    public static final RegistryObject<Item> MESA = ITEMS.register("mesa", () -> new net.minecraft.world.item.BlockItem(ModBlocks.MESA.get(), new Item.Properties()));
    public static final RegistryObject<Item> LAMPARA = ITEMS.register("lampara", () -> new net.minecraft.world.item.BlockItem(ModBlocks.LAMPARA.get(), new Item.Properties()));

    /** Un mueble de un color concreto (el color va en BlockStateTag y se conserva al colocarlo y al romperlo). */
    public static net.minecraft.world.item.ItemStack mueble(Item it, net.minecraft.world.item.DyeColor c) {
        net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(it);
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("color", c.getName());
        s.getOrCreateTag().put("BlockStateTag", t);
        return s;
    }

    // ---- Detector de metales y tesoros
    public static final RegistryObject<Item> DETECTOR = ITEMS.register("detector",
            () -> new com.miscelanea.tesoros.DetectorItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CAPSULA_TIEMPO = ITEMS.register("capsula_tiempo",
            () -> new com.miscelanea.tesoros.CapsulaItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final java.util.List<RegistryObject<Item>> FOSILES = java.util.stream.Stream.of("amonites", "trilobites", "diente", "craneo", "ambar")
            .map(n -> ITEMS.register("fosil_" + n, () -> (Item) new com.miscelanea.tesoros.FosilItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))))
            .toList();
    // ---- Acuario y terrario
    public static final RegistryObject<Item> ACUARIO = ITEMS.register("acuario", () -> new net.minecraft.world.item.BlockItem(ModBlocks.ACUARIO.get(), new Item.Properties()));
    public static final RegistryObject<Item> TERRARIO = ITEMS.register("terrario", () -> new net.minecraft.world.item.BlockItem(ModBlocks.TERRARIO.get(), new Item.Properties()));

    // ---- Juegos de mesa
    public static final RegistryObject<Item> TABLERO = ITEMS.register("tablero", () -> new net.minecraft.world.item.BlockItem(ModBlocks.TABLERO.get(), new Item.Properties()));

    // ---- Tirolina y trampolín
    public static final RegistryObject<Item> POSTE_TIROLINA = ITEMS.register("poste_tirolina", () -> new net.minecraft.world.item.BlockItem(ModBlocks.POSTE_TIROLINA.get(), new Item.Properties()));
    public static final RegistryObject<Item> CABLE_TIROLINA = ITEMS.register("cable_tirolina", () -> new com.miscelanea.tirolina.CableTirolinaItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TRAMPOLIN = ITEMS.register("trampolin", () -> new net.minecraft.world.item.BlockItem(ModBlocks.TRAMPOLIN.get(), new Item.Properties()));

    public static final RegistryObject<Item> IMPERIO = ITEMS.register("imperio", () -> new net.minecraft.world.item.BlockItem(ModBlocks.IMPERIO.get(), new Item.Properties()));

    // ---- Recreativa
    public static final RegistryObject<Item> RECREATIVA = ITEMS.register("recreativa",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.RECREATIVA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FICHA = ITEMS.register("ficha", () -> new com.miscelanea.arcade.FichaItem(new Item.Properties()));

    // ---- Ocarina
    public static final RegistryObject<Item> OCARINA = ITEMS.register("ocarina",
            () -> new com.miscelanea.ocarina.OcarinaItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ---- Consola de bolsillo
    public static final RegistryObject<Item> CONSOLA = ITEMS.register("consola",
            () -> new com.miscelanea.consola.ConsolaItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    private static final java.util.Map<com.miscelanea.consola.Juego, RegistryObject<Item>> CARTUCHOS_JUEGO = new java.util.EnumMap<>(com.miscelanea.consola.Juego.class);
    static {
        for (com.miscelanea.consola.Juego j : com.miscelanea.consola.Juego.values())
            CARTUCHOS_JUEGO.put(j, ITEMS.register(j.item(), () -> new com.miscelanea.consola.CartuchoItem(j, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
    }

    public static Item cartucho(com.miscelanea.consola.Juego j) {
        return CARTUCHOS_JUEGO.get(j).get();
    }

    public static java.util.Collection<RegistryObject<Item>> cartuchosJuego() {
        return CARTUCHOS_JUEGO.values();
    }

    public static Item bicho(com.miscelanea.bichos.Especie e) {
        return BICHOS.get(e).get();
    }

    public static java.util.Collection<RegistryObject<Item>> bichos() {
        return BICHOS.values();
    }

    private ModItems() {}
}
