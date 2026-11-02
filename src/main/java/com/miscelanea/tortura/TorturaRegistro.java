package com.miscelanea.tortura;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
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
 * Registro propio de la "sala de juegos" (trampa de oso invertida, grilletes y minijuegos QTE). Va aparte de
 * ModItems/ModBlocks/ModSounds para no pisar otras ramas: solo hace falta llamar a {@link #registrar(IEventBus)}.
 * Todo es solo de creativo (sin recetas).
 */
public final class TorturaRegistro {
    private TorturaRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);

    // ---- bloques
    public static final RegistryObject<Block> CAJA_JIGSAW = BLOCKS.register("caja_jigsaw",
            () -> new CajaJigsawBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.2F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CajaJigsawBlockEntity>> CAJA_JIGSAW_BE = BLOCK_ENTITIES.register("caja_jigsaw",
            () -> BlockEntityType.Builder.of(CajaJigsawBlockEntity::new, CAJA_JIGSAW.get()).build(null));

    // ---- objetos
    public static final RegistryObject<Item> TRAMPA_OSO = ITEMS.register("trampa_oso",
            () -> new TrampaOsoItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> LLAVE_TRAMPA = ITEMS.register("llave_trampa",
            () -> new LlaveTrampaItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CAJA_JIGSAW_ITEM = ITEMS.register("caja_jigsaw",
            () -> new BlockItem(CAJA_JIGSAW.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> GRILLETES = ITEMS.register("grilletes",
            () -> new GrilletesItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CADENA = ITEMS.register("cadena",
            () -> new CadenaItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GANZUA = ITEMS.register("ganzua",
            () -> new GanzuaItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    // ---- sonidos
    public static final RegistryObject<SoundEvent> QTE_TIC = sonido("qte_tic");
    public static final RegistryObject<SoundEvent> QTE_EXITO = sonido("qte_exito");
    public static final RegistryObject<SoundEvent> QTE_PERFECTO = sonido("qte_perfecto");
    public static final RegistryObject<SoundEvent> QTE_FALLO = sonido("qte_fallo");
    public static final RegistryObject<SoundEvent> QTE_PULSAR = sonido("qte_pulsar");
    public static final RegistryObject<SoundEvent> GANZUA_ROTA = sonido("ganzua_rota");
    public static final RegistryObject<SoundEvent> GANZUA_GIRAR = sonido("ganzua_girar");
    public static final RegistryObject<SoundEvent> CERRADURA_ABRIR = sonido("cerradura_abrir");
    public static final RegistryObject<SoundEvent> TRAMPA_TIC = sonido("trampa_tic");
    public static final RegistryObject<SoundEvent> TRAMPA_CHASQUIDO = sonido("trampa_chasquido");
    public static final RegistryObject<SoundEvent> TRAMPA_PONER = sonido("trampa_poner");
    public static final RegistryObject<SoundEvent> TRAMPA_QUITAR = sonido("trampa_quitar");
    public static final RegistryObject<SoundEvent> GRILLETES_PONER = sonido("grilletes_poner");
    public static final RegistryObject<SoundEvent> GRILLETES_QUITAR = sonido("grilletes_quitar");
    public static final RegistryObject<SoundEvent> CADENA_TINTINEO = sonido("cadena_tintineo");
    public static final RegistryObject<SoundEvent> CAJA_BUSCAR = sonido("caja_buscar");
    public static final RegistryObject<SoundEvent> LLAVE_ENCONTRADA = sonido("llave_encontrada");

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
    }
}
