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

    public static void registrar(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
