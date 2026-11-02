package com.miscelanea.tortura.aparatos;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
 * Registro de los aparatos de la sala de juegos (doncella de hierro, potro, silla eléctrica, hierro de marcar, correas,
 * péndulo y silla de inmersión).
 * Va aparte de {@link com.miscelanea.tortura.TorturaRegistro} para no pisar a otras ramas: basta con llamar a
 * {@link #registrar(IEventBus)} desde el constructor del mod. Todo es solo de creativo (sin recetas).
 */
public final class AparatosRegistro {
    private AparatosRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Miscelanea.MOD_ID);

    // ---- tipos de daño (data/miscelanea/damage_type/*.json)
    public static final ResourceKey<DamageType> DANO_DONCELLA = dano("doncella");
    public static final ResourceKey<DamageType> DANO_POTRO = dano("potro");
    public static final ResourceKey<DamageType> DANO_SILLA = dano("silla_electrica");
    public static final ResourceKey<DamageType> DANO_PENDULO = dano("pendulo");
    public static final ResourceKey<DamageType> DANO_INMERSION = dano("inmersion");

    private static ResourceKey<DamageType> dano(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, n));
    }

    // ---- entidades
    /** Asiento invisible al que va "montada" la víctima: la sujeta en su sitio en el servidor y en el cliente. */
    public static final RegistryObject<EntityType<AsientoAparato>> ASIENTO = ENTITIES.register("asiento_aparato",
            () -> EntityType.Builder.<AsientoAparato>of(AsientoAparato::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(10).updateInterval(10).noSummon().build("asiento_aparato"));

    // ---- bloques
    private static BlockBehaviour.Properties hierro() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.ANVIL).noOcclusion()
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    private static BlockBehaviour.Properties madera() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    public static final RegistryObject<Block> DONCELLA = BLOCKS.register("doncella_hierro", () -> new DoncellaBlock(hierro()));
    public static final RegistryObject<Block> POTRO = BLOCKS.register("potro", () -> new PotroBlock(madera()));
    public static final RegistryObject<Block> SILLA = BLOCKS.register("silla_electrica", () -> new SillaElectricaBlock(madera()));
    public static final RegistryObject<Block> PENDULO = BLOCKS.register("pendulo", () -> new PenduloBlock(madera()));
    public static final RegistryObject<Block> INMERSION = BLOCKS.register("silla_inmersion", () -> new InmersionBlock(madera()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<DoncellaBlockEntity>> DONCELLA_BE = BLOCK_ENTITIES.register("doncella_hierro",
            () -> BlockEntityType.Builder.of(DoncellaBlockEntity::new, DONCELLA.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PotroBlockEntity>> POTRO_BE = BLOCK_ENTITIES.register("potro",
            () -> BlockEntityType.Builder.of(PotroBlockEntity::new, POTRO.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SillaElectricaBlockEntity>> SILLA_BE = BLOCK_ENTITIES.register("silla_electrica",
            () -> BlockEntityType.Builder.of(SillaElectricaBlockEntity::new, SILLA.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PenduloBlockEntity>> PENDULO_BE = BLOCK_ENTITIES.register("pendulo",
            () -> BlockEntityType.Builder.of(PenduloBlockEntity::new, PENDULO.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<InmersionBlockEntity>> INMERSION_BE = BLOCK_ENTITIES.register("silla_inmersion",
            () -> BlockEntityType.Builder.of(InmersionBlockEntity::new, INMERSION.get()).build(null));

    // ---- objetos
    public static final RegistryObject<Item> DONCELLA_ITEM = ITEMS.register("doncella_hierro",
            () -> new AparatoItem(DONCELLA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final RegistryObject<Item> POTRO_ITEM = ITEMS.register("potro",
            () -> new AparatoItem(POTRO.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final RegistryObject<Item> SILLA_ITEM = ITEMS.register("silla_electrica",
            () -> new AparatoItem(SILLA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final RegistryObject<Item> CORREAS = ITEMS.register("correas",
            () -> new CorreasItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> HIERRO_MARCAR = ITEMS.register("hierro_marcar",
            () -> new HierroMarcarItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> PENDULO_ITEM = ITEMS.register("pendulo",
            () -> new AparatoItem(PENDULO.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final RegistryObject<Item> INMERSION_ITEM = ITEMS.register("silla_inmersion",
            () -> new AparatoItem(INMERSION.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));

    // ---- sonidos
    public static final RegistryObject<SoundEvent> DONCELLA_CHIRRIDO = sonido("doncella_chirrido");
    public static final RegistryObject<SoundEvent> DONCELLA_CIERRE = sonido("doncella_cierre");
    public static final RegistryObject<SoundEvent> DONCELLA_PINCHOS = sonido("doncella_pinchos");
    public static final RegistryObject<SoundEvent> POTRO_CARRACA = sonido("potro_carraca");
    public static final RegistryObject<SoundEvent> POTRO_CRUJIDO = sonido("potro_crujido");
    public static final RegistryObject<SoundEvent> POTRO_CONVERSION = sonido("potro_conversion");
    public static final RegistryObject<SoundEvent> SILLA_DESCARGA = sonido("silla_descarga");
    public static final RegistryObject<SoundEvent> SILLA_PALANCA = sonido("silla_palanca");
    public static final RegistryObject<SoundEvent> SILLA_DIAL = sonido("silla_dial");
    public static final RegistryObject<SoundEvent> HIERRO_CHISPORROTEO = sonido("hierro_chisporroteo");
    public static final RegistryObject<SoundEvent> HIERRO_BRASA = sonido("hierro_brasa");
    public static final RegistryObject<SoundEvent> PENDULO_SILBIDO = sonido("pendulo_silbido");
    public static final RegistryObject<SoundEvent> PENDULO_CARRACA = sonido("pendulo_carraca");
    public static final RegistryObject<SoundEvent> PENDULO_TICTAC = sonido("pendulo_tictac");
    public static final RegistryObject<SoundEvent> PENDULO_CORREA = sonido("pendulo_correa");
    public static final RegistryObject<SoundEvent> PENDULO_CORTE = sonido("pendulo_corte");
    public static final RegistryObject<SoundEvent> PENDULO_PALANCA = sonido("pendulo_palanca");
    public static final RegistryObject<SoundEvent> INMERSION_CHAPUZON = sonido("inmersion_chapuzon");
    public static final RegistryObject<SoundEvent> INMERSION_BURBUJAS = sonido("inmersion_burbujas");
    public static final RegistryObject<SoundEvent> INMERSION_TOS = sonido("inmersion_tos");
    public static final RegistryObject<SoundEvent> INMERSION_CRUJIDO = sonido("inmersion_crujido");
    public static final RegistryObject<SoundEvent> INMERSION_GOTEO = sonido("inmersion_goteo");

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        ENTITIES.register(modBus);
    }
}
