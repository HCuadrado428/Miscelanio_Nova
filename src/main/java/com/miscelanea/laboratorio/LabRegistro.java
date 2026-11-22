package com.miscelanea.laboratorio;

import com.miscelanea.Miscelanea;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro del laboratorio de experimentos de la zona de torturas (ver docs/LABORATORIO.md). Se llama desde el
 * constructor del mod con {@link #registrar(IEventBus)}.
 */
public final class LabRegistro {
    private LabRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);

    // ---- objetos para medir y anotar
    public static final RegistryObject<Item> CUADERNO = ITEMS.register("cuaderno_laboratorio",
            () -> new CuadernoItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PULSOMETRO = ITEMS.register("pulsometro",
            () -> new PulsometroItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CRONOMETRO = ITEMS.register("cronometro",
            () -> new CronometroItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PORTAPAPELES = ITEMS.register("portapapeles",
            () -> new PortapapelesItem(new Item.Properties().stacksTo(1)));

    // ---- tubos y muestras (las muestras salen de los experimentos)
    public static final RegistryObject<Item> TUBO_ENSAYO = ITEMS.register("tubo_ensayo", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MUESTRA_MIEDO = ITEMS.register("muestra_miedo", () -> new MuestraItem("miedo", new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MUESTRA_MAREO = ITEMS.register("muestra_mareo", () -> new MuestraItem("mareo", new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MUESTRA_CALOR = ITEMS.register("muestra_calor", () -> new MuestraItem("calor", new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MUESTRA_FRIO = ITEMS.register("muestra_frio", () -> new MuestraItem("frio", new Item.Properties().stacksTo(16)));

    // ---- máquinas
    public static final RegistryObject<Block> DESTILADOR = BLOCKS.register("destilador_termico",
            () -> new DestiladorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 3.0F)
                    .sound(SoundType.COPPER).noOcclusion()));
    public static final RegistryObject<Item> DESTILADOR_ITEM = ITEMS.register("destilador_termico",
            () -> new BlockItem(DESTILADOR.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<DestiladorBlockEntity>> DESTILADOR_BE = BLOCK_ENTITIES.register("destilador_termico",
            () -> BlockEntityType.Builder.of(DestiladorBlockEntity::new, DESTILADOR.get()).build(null));

    // ---- estaciones
    public static final RegistryObject<Block> CENTRIFUGA = BLOCKS.register("centrifuga",
            () -> new CentrifugaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F)
                    .sound(SoundType.METAL).noOcclusion().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Item> CENTRIFUGA_ITEM = ITEMS.register("centrifuga",
            () -> new com.miscelanea.tortura.aparatos.AparatoItem(CENTRIFUGA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final RegistryObject<BlockEntityType<CentrifugaBlockEntity>> CENTRIFUGA_BE = BLOCK_ENTITIES.register("centrifuga",
            () -> BlockEntityType.Builder.of(CentrifugaBlockEntity::new, CENTRIFUGA.get()).build(null));

    public static final RegistryObject<Block> CAMARA = BLOCKS.register("camara_temperatura",
            () -> new CamaraBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F)
                    .sound(SoundType.GLASS).noOcclusion().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Item> CAMARA_ITEM = ITEMS.register("camara_temperatura",
            () -> new com.miscelanea.tortura.aparatos.AparatoItem(CAMARA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final RegistryObject<BlockEntityType<CamaraBlockEntity>> CAMARA_BE = BLOCK_ENTITIES.register("camara_temperatura",
            () -> BlockEntityType.Builder.of(CamaraBlockEntity::new, CAMARA.get()).build(null));

    public static final RegistryObject<Block> TANQUE = BLOCKS.register("tanque_privacion",
            () -> new TanqueBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 6.0F)
                    .sound(SoundType.GLASS).noOcclusion().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Item> TANQUE_ITEM = ITEMS.register("tanque_privacion",
            () -> new com.miscelanea.tortura.aparatos.AparatoItem(TANQUE.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final RegistryObject<BlockEntityType<TanqueBlockEntity>> TANQUE_BE = BLOCK_ENTITIES.register("tanque_privacion",
            () -> BlockEntityType.Builder.of(TanqueBlockEntity::new, TANQUE.get()).build(null));

    public static final RegistryObject<Item> QUESO = ITEMS.register("queso_laboratorio",
            () -> new QuesoLabItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Block> LABERINTO = BLOCKS.register("laberinto_rata",
            () -> new LaberintoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0F, 6.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> LABERINTO_ITEM = ITEMS.register("laberinto_rata",
            () -> new BlockItem(LABERINTO.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<BlockEntityType<LaberintoBlockEntity>> LABERINTO_BE = BLOCK_ENTITIES.register("laberinto_rata",
            () -> BlockEntityType.Builder.of(LaberintoBlockEntity::new, LABERINTO.get()).build(null));

    // ---- sueros y mezclas
    public static final RegistryObject<Item> SUERO = ITEMS.register("suero_sin_etiquetar", () -> new SueroItem(new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Block> MESA = BLOCKS.register("mesa_mezclas",
            () -> new MesaMezclasBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> MESA_ITEM = ITEMS.register("mesa_mezclas", () -> new BlockItem(MESA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<MesaMezclasBlockEntity>> MESA_BE = BLOCK_ENTITIES.register("mesa_mezclas",
            () -> BlockEntityType.Builder.of(MesaMezclasBlockEntity::new, MESA.get()).build(null));
    public static final RegistryObject<Block> ANALIZADOR = BLOCKS.register("analizador",
            () -> new AnalizadorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> ANALIZADOR_ITEM = ITEMS.register("analizador", () -> new BlockItem(ANALIZADOR.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ---- más máquinas
    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }
    public static final RegistryObject<Block> INCUBADORA = BLOCKS.register("incubadora", () -> new IncubadoraBlock(metal()));
    public static final RegistryObject<Item> INCUBADORA_ITEM = ITEMS.register("incubadora", () -> new BlockItem(INCUBADORA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<IncubadoraBlockEntity>> INCUBADORA_BE = BLOCK_ENTITIES.register("incubadora",
            () -> BlockEntityType.Builder.of(IncubadoraBlockEntity::new, INCUBADORA.get()).build(null));
    public static final RegistryObject<Block> DISPENSADOR = BLOCKS.register("dispensador_sueros", () -> new DispensadorBlock(metal()));
    public static final RegistryObject<Item> DISPENSADOR_ITEM = ITEMS.register("dispensador_sueros", () -> new BlockItem(DISPENSADOR.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Block> IMPRESORA = BLOCKS.register("impresora_informes", () -> new ImpresoraBlock(metal()));
    public static final RegistryObject<Item> IMPRESORA_ITEM = ITEMS.register("impresora_informes", () -> new BlockItem(IMPRESORA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> INFORME = ITEMS.register("informe_laboratorio", () -> new InformeItem(new Item.Properties().stacksTo(1)));

    // ---- utilidades y seguridad
    public static final RegistryObject<Item> SUERO_RECUP = ITEMS.register("suero_recuperacion", () -> new SueroRecuperacionItem(new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> ESCANER = ITEMS.register("escaner_anomalias", () -> new EscanerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Block> FRIGORIFICO = BLOCKS.register("frigorifico_muestras", () -> new FrigorificoBlock(metal()));
    public static final RegistryObject<Item> FRIGORIFICO_ITEM = ITEMS.register("frigorifico_muestras", () -> new BlockItem(FRIGORIFICO.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<FrigorificoBlockEntity>> FRIGORIFICO_BE = BLOCK_ENTITIES.register("frigorifico_muestras",
            () -> BlockEntityType.Builder.of(FrigorificoBlockEntity::new, FRIGORIFICO.get()).build(null));
    public static final RegistryObject<Block> ALARMA = BLOCKS.register("alarma_laboratorio", () -> new AlarmaBlock(metal().lightLevel(s -> s.getValue(AlarmaBlock.SONANDO) ? 12 : s.getValue(AlarmaBlock.ARMADA) ? 4 : 0)));
    public static final RegistryObject<Item> ALARMA_ITEM = ITEMS.register("alarma_laboratorio", () -> new BlockItem(ALARMA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<AlarmaBlockEntity>> ALARMA_BE = BLOCK_ENTITIES.register("alarma_laboratorio",
            () -> BlockEntityType.Builder.of(AlarmaBlockEntity::new, ALARMA.get()).build(null));
    public static final RegistryObject<Block> PUERTA_CUARENTENA = BLOCKS.register("puerta_cuarentena",
            () -> new PuertaCuarentenaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> PUERTA_CUARENTENA_ITEM = ITEMS.register("puerta_cuarentena",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(PUERTA_CUARENTENA.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Block> ESTABILIZADOR = BLOCKS.register("estabilizador_glitch", () -> new EstabilizadorBlock(metal().lightLevel(s -> 6)));
    public static final RegistryObject<Item> ESTABILIZADOR_ITEM = ITEMS.register("estabilizador_glitch", () -> new BlockItem(ESTABILIZADOR.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<BlockEntityType<EstabilizadorBlockEntity>> ESTABILIZADOR_BE = BLOCK_ENTITIES.register("estabilizador_glitch",
            () -> BlockEntityType.Builder.of(EstabilizadorBlockEntity::new, ESTABILIZADOR.get()).build(null));

    // ---- decoración
    public static final RegistryObject<Block> CRISTAL_OBS = BLOCKS.register("cristal_observacion",
            () -> new net.minecraft.world.level.block.GlassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Item> CRISTAL_OBS_ITEM = ITEMS.register("cristal_observacion", () -> new BlockItem(CRISTAL_OBS.get(), new Item.Properties()));
    public static final RegistryObject<Block> CARTEL_BIO = BLOCKS.register("cartel_biopeligro",
            () -> new DecoracionOrientadaBlock(metal().noCollission(), 2, 2, 15, 14, 14, 16));
    public static final RegistryObject<Item> CARTEL_BIO_ITEM = ITEMS.register("cartel_biopeligro", () -> new BlockItem(CARTEL_BIO.get(), new Item.Properties()));
    public static final RegistryObject<Block> TAQUILLA = BLOCKS.register("taquilla_laboratorio",
            () -> new DecoracionOrientadaBlock(metal(), 1, 0, 2, 15, 16, 14));
    public static final RegistryObject<Item> TAQUILLA_ITEM = ITEMS.register("taquilla_laboratorio", () -> new BlockItem(TAQUILLA.get(), new Item.Properties()));
    public static final RegistryObject<Block> MICROSCOPIO = BLOCKS.register("microscopio",
            () -> new DecoracionBlock(metal(), 3, 0, 3, 13, 14, 13));
    public static final RegistryObject<Item> MICROSCOPIO_ITEM = ITEMS.register("microscopio", () -> new BlockItem(MICROSCOPIO.get(), new Item.Properties()));
    public static final RegistryObject<Block> PROBETAS = BLOCKS.register("probetas",
            () -> new DecoracionBlock(metal().sound(SoundType.GLASS), 1, 0, 1, 15, 13, 15));
    public static final RegistryObject<Item> PROBETAS_ITEM = ITEMS.register("probetas", () -> new BlockItem(PROBETAS.get(), new Item.Properties()));

    // ---- ropa del científico
    public static final RegistryObject<Item> GAFAS = ITEMS.register("gafas_seguridad",
            () -> new net.minecraft.world.item.ArmorItem(LabRopa.CIENTIFICO, net.minecraft.world.item.ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> BATA = ITEMS.register("bata_laboratorio",
            () -> new net.minecraft.world.item.ArmorItem(LabRopa.CIENTIFICO, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> PANTALON_LAB = ITEMS.register("pantalon_laboratorio",
            () -> new net.minecraft.world.item.ArmorItem(LabRopa.CIENTIFICO, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static void registrar(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
