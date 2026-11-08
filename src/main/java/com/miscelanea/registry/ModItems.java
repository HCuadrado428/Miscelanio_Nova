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
    public static final RegistryObject<Item> BFG = ITEMS.register("bfg",
            () -> new com.miscelanea.armas.BfgItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> MAZO_WEMMBU = ITEMS.register("mazo_wemmbu",
            () -> new com.miscelanea.armas.MazoItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> CARTUCHOS = ITEMS.register("cartuchos", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COHETE = ITEMS.register("cohete", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BALLESTA_REPETICION = ITEMS.register("ballesta_repeticion",
            () -> new com.miscelanea.armas.BallestaItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

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
    public static final RegistryObject<Item> PARCHIS = ITEMS.register("parchis", () -> new net.minecraft.world.item.BlockItem(ModBlocks.PARCHIS.get(), new Item.Properties()));
    public static final RegistryObject<Item> OCA = ITEMS.register("oca", () -> new net.minecraft.world.item.BlockItem(ModBlocks.OCA.get(), new Item.Properties()));
    public static final RegistryObject<Item> CUATRO = ITEMS.register("cuatro", () -> new net.minecraft.world.item.BlockItem(ModBlocks.CUATRO.get(), new Item.Properties()));
    public static final RegistryObject<Item> FLOTA = ITEMS.register("flota", () -> new net.minecraft.world.item.BlockItem(ModBlocks.FLOTA.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOMINO = ITEMS.register("domino", () -> new net.minecraft.world.item.BlockItem(ModBlocks.DOMINO.get(), new Item.Properties()));

    // ---- Recreativa
    public static final RegistryObject<Item> RECREATIVA = ITEMS.register("recreativa",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.RECREATIVA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> RECREATIVA_DOBLE = ITEMS.register("recreativa_doble",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.RECREATIVA_DOBLE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CABINA_CARRERAS = ITEMS.register("cabina_carreras",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.CABINA_CARRERAS.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> RECREATIVA_PISTOLA = ITEMS.register("recreativa_pistola",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.RECREATIVA_PISTOLA.get(), new Item.Properties().rarity(Rarity.RARE)));
    /** Recreativas dedicadas a un juego, en el orden de {@link com.miscelanea.arcade.ArcadeJuegos#IDS}. */
    public static final java.util.Map<String, RegistryObject<Item>> RECREATIVAS_JUEGO = new java.util.LinkedHashMap<>();
    static {
        for (var e : ModBlocks.RECREATIVAS_JUEGO.entrySet())
            RECREATIVAS_JUEGO.put(e.getKey(), ITEMS.register("recreativa_" + e.getKey(),
                    () -> new net.minecraft.world.item.BlockItem(e.getValue().get(), new Item.Properties().rarity(Rarity.RARE))));
    }

    public static java.util.Collection<RegistryObject<Item>> recreativasJuego() {
        return RECREATIVAS_JUEGO.values();
    }

    public static Item recreativaJuego(String juego) {
        return RECREATIVAS_JUEGO.get(juego).get();
    }
    public static final RegistryObject<Item> NORIA = ITEMS.register("noria",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.NORIA.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TIOVIVO = ITEMS.register("tiovivo",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.TIOVIVO.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> COCHE_CHOQUE = ITEMS.register("coche_choque",
            () -> new com.miscelanea.feria.CocheChoqueItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> VAGONETA_BRUJA = ITEMS.register("vagoneta_bruja",
            () -> new com.miscelanea.feria.VagonetaBrujaItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SUSTO = ITEMS.register("susto",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.SUSTO.get(), new Item.Properties()));
    public static final RegistryObject<Item> CASETA_TIRO = ITEMS.register("caseta_tiro",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.CASETA_TIRO.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ESCOPETA_CORCHO = ITEMS.register("escopeta_corcho",
            () -> new com.miscelanea.feria.EscopetaCorchoItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PELUCHE = ITEMS.register("peluche",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PELUCHE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ALGODONERA = ITEMS.register("algodonera",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.ALGODONERA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PALOMITERA = ITEMS.register("palomitera",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PALOMITERA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CASTILLO_FUEGOS = ITEMS.register("castillo_fuegos",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.CASTILLO_FUEGOS.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ALGODON = ITEMS.register("algodon",
            () -> new com.miscelanea.feria.AlgodonItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PALOMITAS = ITEMS.register("palomitas",
            () -> new Item(new Item.Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationMod(0.5F).build())));
    public static final RegistryObject<Item> MAQUINA_GANCHO = ITEMS.register("maquina_gancho",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.MAQUINA_GANCHO.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TRAGAPERRAS = ITEMS.register("tragaperras",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.TRAGAPERRAS.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GACHAPON = ITEMS.register("gachapon",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.GACHAPON.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CAPSULA_GACHA = ITEMS.register("capsula_gacha",
            () -> new com.miscelanea.feria.CapsulaGachaItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CHAPA = ITEMS.register("chapa",
            () -> new com.miscelanea.feria.ChapaItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PISTA_BOLOS = ITEMS.register("pista_bolos",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PISTA_BOLOS.get(), new Item.Properties()));
    public static final RegistryObject<Item> BOLERA = ITEMS.register("bolera",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.BOLERA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BOLA_BOLOS = ITEMS.register("bola_bolos",
            () -> new com.miscelanea.bolos.BolaBolosItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DIANA_DARDOS = ITEMS.register("diana_dardos",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.DIANA_DARDOS.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DARDO = ITEMS.register("dardo",
            () -> new com.miscelanea.dardos.DardoItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TORRE = ITEMS.register("torre_bloques",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.TORRE.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOMATE = ITEMS.register("tomate",
            () -> new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationMod(0.4F).build())));
    public static final RegistryObject<Item> SEMILLAS_TOMATE = ITEMS.register("semillas_tomate",
            () -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.TOMATERA.get(), new Item.Properties()));
    public static final RegistryObject<Item> ARROZ = ITEMS.register("arroz",
            () -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.ARROZAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> FOGON = ITEMS.register("fogon",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.FOGON.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAELLA = ITEMS.register("paella",
            () -> new com.miscelanea.cocina.PaellaItem(ModBlocks.PAELLA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TORTILLA = ITEMS.register("tortilla",
            () -> new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationMod(0.8F).build())));
    public static final RegistryObject<Item> GAZPACHO = ITEMS.register("gazpacho",
            () -> new com.miscelanea.cocina.GazpachoItem(new Item.Properties().stacksTo(1).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(5).saturationMod(0.6F).alwaysEat()
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 60, 0), 1F).build())));
    public static final RegistryObject<Item> CHURROS = ITEMS.register("churros",
            () -> new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationMod(0.3F).fast().build())));
    public static final RegistryObject<Item> CHURROS_CHOCOLATE = ITEMS.register("churros_chocolate",
            () -> new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationMod(0.9F)
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 20 * 30, 0), 1F)
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SPEED, 20 * 30, 0), 1F).build())));
    public static final RegistryObject<Item> MESA_CARTAS = ITEMS.register("mesa_cartas",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.MESA_CARTAS.get(), new Item.Properties()));
    public static final RegistryObject<Item> MAQUINA_CANASTA = ITEMS.register("maquina_canasta",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.MAQUINA_CANASTA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MAQUINA_BOXEO = ITEMS.register("maquina_boxeo",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.MAQUINA_BOXEO.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MARTILLO_FUERZA = ITEMS.register("martillo_fuerza",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.MARTILLO_FUERZA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BALON = ITEMS.register("balon",
            () -> new com.miscelanea.feria.BalonItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> AIRHOCKEY = ITEMS.register("air_hockey",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.AIRHOCKEY.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PULPO = ITEMS.register("pulpo",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PULPO.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> PESCA_PATITOS = ITEMS.register("pesca_patitos",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PESCA_PATITOS.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOMBOLA = ITEMS.register("tombola",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.TOMBOLA.get(), new Item.Properties()));
    public static final RegistryObject<Item> CHURRERIA = ITEMS.register("churreria",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.CHURRERIA.get(), new Item.Properties()));
    public static final RegistryObject<Item> TABURETE = ITEMS.register("taburete",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.TABURETE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CANA_PATITOS = ITEMS.register("cana_patitos",
            () -> new com.miscelanea.feria.CanaPatitosItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BOLAS_PETANCA = ITEMS.register("bolas_petanca",
            () -> new com.miscelanea.petanca.PetancaItem(new Item.Properties().stacksTo(3), com.miscelanea.petanca.PetancaItem.BOLA));
    public static final RegistryObject<Item> BOLICHE = ITEMS.register("boliche",
            () -> new com.miscelanea.petanca.PetancaItem(new Item.Properties().stacksTo(1), com.miscelanea.petanca.PetancaItem.BOLICHE));
    public static final RegistryObject<Item> METRO = ITEMS.register("metro",
            () -> new com.miscelanea.petanca.PetancaItem(new Item.Properties().stacksTo(1), com.miscelanea.petanca.PetancaItem.METRO));
    public static final RegistryObject<Item> GRAMOLA = ITEMS.register("gramola",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.GRAMOLA.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> PISTA_BAILE = ITEMS.register("pista_baile",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PISTA_BAILE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BILLAR = ITEMS.register("billar",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.BILLAR.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FUTBOLIN = ITEMS.register("futbolin",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.FUTBOLIN.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    // ---- Montaña rusa
    public static final RegistryObject<Item> VIA_MONTANA = ITEMS.register("via_montana", () -> new net.minecraft.world.item.BlockItem(ModBlocks.VIA_MONTANA.get(), new Item.Properties()));
    public static final RegistryObject<Item> VIA_MONTANA_CADENA = ITEMS.register("via_montana_cadena", () -> new net.minecraft.world.item.BlockItem(ModBlocks.VIA_MONTANA_CADENA.get(), new Item.Properties()));
    public static final RegistryObject<Item> VIA_MONTANA_ESTACION = ITEMS.register("via_montana_estacion", () -> new net.minecraft.world.item.BlockItem(ModBlocks.VIA_MONTANA_ESTACION.get(), new Item.Properties()));
    public static final RegistryObject<Item> VIA_MONTANA_FRENO = ITEMS.register("via_montana_freno", () -> new net.minecraft.world.item.BlockItem(ModBlocks.VIA_MONTANA_FRENO.get(), new Item.Properties()));
    public static final RegistryObject<Item> RIZO_MONTANA = ITEMS.register("rizo_montana", () -> new net.minecraft.world.item.BlockItem(ModBlocks.RIZO.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    // El Purgatorio: bloques que solo se encuentran allí (no se fabrican)
    public static final RegistryObject<Item> SIN_TEXTURA = ITEMS.register("sin_textura", () -> new net.minecraft.world.item.BlockItem(ModBlocks.SIN_TEXTURA.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> MOQUETA_RECREATIVA = ITEMS.register("moqueta_recreativa", () -> new net.minecraft.world.item.BlockItem(ModBlocks.MOQUETA_RECREATIVA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TREN_MONTANA = ITEMS.register("tren_montana", () -> new com.miscelanea.montana.TrenMontanaItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PIZARRA_FERIA = ITEMS.register("pizarra_feria",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PIZARRA_FERIA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TABLON_RECORDS = ITEMS.register("tablon_records",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.TABLON_RECORDS.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CARTEL_RECORDS = ITEMS.register("cartel_records",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.CARTEL_RECORDS.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MESA_COCTEL = ITEMS.register("mesa_coctel",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.MESA_COCTEL.get(), new Item.Properties().rarity(Rarity.RARE)));
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

    // ---- Heridas en la skin: venda (sin receta)
    public static final RegistryObject<Item> VENDA = ITEMS.register("venda",
            () -> new com.miscelanea.tortura.heridas.VendaItem(new Item.Properties().stacksTo(16)));

    private ModItems() {}
}
