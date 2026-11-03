package com.miscelanea.tortura.saw;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de los juegos de Jigsaw (Saw): la caja de agua con su tanque, el pozo de agujas, el alambre de espino con
 * las tenazas y la puerta de salida con reloj. Va aparte del resto de la sala de juegos para no pisar a otras ramas:
 * basta con llamar a {@link #registrar(IEventBus)} desde el constructor del mod. Todo es solo de creativo (sin recetas).
 */
public final class SawRegistro {
    private SawRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);

    // ---- tipos de daño (data/miscelanea/damage_type/*.json)
    public static final ResourceKey<DamageType> DANO_CAJA = dano("caja_agua");
    public static final ResourceKey<DamageType> DANO_CUCHILLAS = dano("caja_agua_cuchillas");
    public static final ResourceKey<DamageType> DANO_POZO = dano("pozo_agujas");
    public static final ResourceKey<DamageType> DANO_ALAMBRE = dano("alambre_espino");

    private static ResourceKey<DamageType> dano(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, n));
    }

    // ---- bloques
    public static final RegistryObject<Block> TANQUE = BLOCKS.register("tanque_agua", () -> new TanqueAguaBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Block> POZO = BLOCKS.register("pozo_agujas", () -> new PozoAgujasBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.8F, 1.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion()
                    .speedFactor(0.45F).jumpFactor(0.6F).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Block> ALAMBRE = BLOCKS.register("alambre_espino", () -> new AlambreEspinoBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(20.0F, 6.0F).sound(SoundType.CHAIN).noOcclusion()
                    .noCollission().pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> SALIDA = BLOCKS.register("salida", () -> new SalidaBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.BLOCK).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SalidaBlockEntity>> SALIDA_BE = BLOCK_ENTITIES.register("salida",
            () -> BlockEntityType.Builder.of(SalidaBlockEntity::new, SALIDA.get()).build(null));

    // ---- objetos
    public static final RegistryObject<Item> CAJA_AGUA = ITEMS.register("caja_agua",
            () -> new CajaAguaItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TANQUE_ITEM = ITEMS.register("tanque_agua",
            () -> new SawBlockItem(TANQUE.get(), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 2));
    public static final RegistryObject<Item> POZO_ITEM = ITEMS.register("pozo_agujas",
            () -> new SawBlockItem(POZO.get(), new Item.Properties().rarity(Rarity.EPIC), 3));
    public static final RegistryObject<Item> ALAMBRE_ITEM = ITEMS.register("alambre_espino",
            () -> new SawBlockItem(ALAMBRE.get(), new Item.Properties().stacksTo(64).rarity(Rarity.RARE), 2));
    public static final RegistryObject<Item> SALIDA_ITEM = ITEMS.register("salida",
            () -> new SalidaItem(SALIDA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TENAZAS = ITEMS.register("tenazas",
            () -> new TenazasItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ---- sonidos (scratchpad/gen_tortura_saw.py)
    public static final RegistryObject<SoundEvent> JUEGO = sonido("saw_juego");
    public static final RegistryObject<SoundEvent> CAJA_LLENAR = sonido("saw_caja_llenar");
    public static final RegistryObject<SoundEvent> CAJA_BURBUJAS = sonido("saw_caja_burbujas");
    public static final RegistryObject<SoundEvent> CAJA_CUCHILLAS = sonido("saw_caja_cuchillas");
    public static final RegistryObject<SoundEvent> CAJA_VACIAR = sonido("saw_caja_vaciar");
    public static final RegistryObject<SoundEvent> CAJA_ABRIR = sonido("saw_caja_abrir");
    public static final RegistryObject<SoundEvent> TANQUE_PALANCA = sonido("saw_tanque_palanca");
    public static final RegistryObject<SoundEvent> POZO_BUSCAR = sonido("saw_pozo_buscar");
    public static final RegistryObject<SoundEvent> POZO_PINCHAZO = sonido("saw_pozo_pinchazo");
    public static final RegistryObject<SoundEvent> POZO_LLAVE = sonido("saw_pozo_llave");
    public static final RegistryObject<SoundEvent> ALAMBRE_RASGAR = sonido("saw_alambre_rasgar");
    public static final RegistryObject<SoundEvent> ALAMBRE_CORTAR = sonido("saw_alambre_cortar");
    public static final RegistryObject<SoundEvent> SALIDA_TIC = sonido("saw_salida_tic");
    public static final RegistryObject<SoundEvent> SALIDA_APAGON = sonido("saw_salida_apagon");
    public static final RegistryObject<SoundEvent> SALIDA_ABRIR = sonido("saw_salida_abrir");
    public static final RegistryObject<SoundEvent> SALIDA_CERRAR = sonido("saw_salida_cerrar");

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
