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
    public static final RegistryObject<Item> MUESTRA_FRIO = ITEMS.register("muestra_frio", () -> new MuestraItem("frio", new Item.Properties().stacksTo(16)));

    // ---- máquinas
    public static final RegistryObject<Block> DESTILADOR = BLOCKS.register("destilador_termico",
            () -> new DestiladorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 3.0F)
                    .sound(SoundType.COPPER).noOcclusion()));
    public static final RegistryObject<Item> DESTILADOR_ITEM = ITEMS.register("destilador_termico",
            () -> new BlockItem(DESTILADOR.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<DestiladorBlockEntity>> DESTILADOR_BE = BLOCK_ENTITIES.register("destilador_termico",
            () -> BlockEntityType.Builder.of(DestiladorBlockEntity::new, DESTILADOR.get()).build(null));

    public static void registrar(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
