package com.miscelanea.purgatorio.eventos;

import com.miscelanea.Miscelanea;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Los objetos purgados del Purgatorio (2.0.0): solo salen allí (en los rincones raros o se les caen a los habitantes al
 * pegarles) y no se fabrican. Registro aparte para no tocar el de todo el mod.
 */
public final class ExtrasRegistro {
    private ExtrasRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);

    /** Brújula que apunta a la puerta de SALIDA más cercana (fuera del Purgatorio da vueltas). */
    public static final RegistryObject<Item> BRUJULA = ITEMS.register("brujula_salida",
            () -> new BrujulaSalidaItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    /** Reloj que va hacia atrás: al usarlo vuelves a donde estabas hace 10 s. */
    public static final RegistryObject<Item> RELOJ = ITEMS.register("reloj_reves",
            () -> new RelojRevesItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    /** Tique de turno de la Sala de Espera: lo canjeas y te llaman a una salida. */
    public static final RegistryObject<Item> TIQUE = ITEMS.register("tique_turno",
            () -> new TiqueTurnoItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    /** Llave de la habitación 404 del hotel: cuelga el "No molestar" y los habitantes dejan de oírte un rato. */
    public static final RegistryObject<Item> LLAVE = ITEMS.register("llave_habitacion",
            () -> new LlaveHabitacionItem(new Item.Properties().stacksTo(1).durability(5).rarity(Rarity.RARE)));
    /** Ficha infinita de la Recreativa Infinita: vale en cualquier recreativa y no se gasta. */
    public static final RegistryObject<Item> FICHA_INFINITA = ITEMS.register("ficha_infinita",
            () -> new FichaInfinitaItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    /** El bloque MissingNo.: le pasa algo raro al sexto objeto de tu barra. */
    public static final RegistryObject<Block> MISSINGNO = BLOCKS.register("missingno",
            () -> new MissingnoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.0F, 6.0F)
                    .sound(SoundType.AMETHYST).emissiveRendering((s, l, p) -> true).lightLevel(s -> 4)));
    public static final RegistryObject<Item> MISSINGNO_ITEM = ITEMS.register("missingno",
            () -> new BlockItem(MISSINGNO.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        modBus.addListener(ExtrasRegistro::pestana);
        RedExtras.registrar();
    }

    /** En la pestaña Varios, junto a los bloques del Purgatorio. */
    private static void pestana(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != com.miscelanea.registry.ModTabs.VARIOS.getKey()) return;
        e.accept(BRUJULA);
        e.accept(RELOJ);
        e.accept(TIQUE);
        e.accept(LLAVE);
        e.accept(FICHA_INFINITA);
        e.accept(MISSINGNO_ITEM);
    }
}
