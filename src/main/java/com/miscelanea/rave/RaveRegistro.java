package com.miscelanea.rave;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro de la rave (discoteca y concierto). Hito 1: neón (tubos, paneles y barras luminosas). */
public final class RaveRegistro {
    private RaveRegistro() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Miscelanea.MOD_ID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Miscelanea.MOD_ID);

    private static BlockBehaviour.Properties neon(int luz) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> luz);
    }

    public static final RegistryObject<Block> TUBO = BLOCKS.register("tubo_neon", () -> new NeonBlock(neon(12), 6.5, 0, 6.5, 9.5, 16, 9.5));
    public static final RegistryObject<Item> TUBO_ITEM = ITEMS.register("tubo_neon", () -> new NeonItem(TUBO.get(), new Item.Properties()));
    public static final RegistryObject<Block> PANEL = BLOCKS.register("panel_neon", () -> new NeonBlock(neon(10), 0, 0, 0, 16, 1, 16));
    public static final RegistryObject<Item> PANEL_ITEM = ITEMS.register("panel_neon", () -> new NeonItem(PANEL.get(), new Item.Properties()));

    public static final RegistryObject<Item> BARRA = ITEMS.register("barra_luminosa", () -> new BarraLuminosaItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Block> BARRA_SUELO = BLOCKS.register("barra_luminosa_suelo",
            () -> new BarraLuminosaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).instabreak().noCollission().noOcclusion()
                    .sound(SoundType.GLASS).lightLevel(s -> 9).noLootTable()));
    public static final RegistryObject<EntityType<BarraLanzada>> BARRA_LANZADA = ENTITIES.register("barra_lanzada",
            () -> EntityType.Builder.<BarraLanzada>of(BarraLanzada::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10)
                    .build("barra_lanzada"));

    // ---- hito 2: luces
    private static BlockBehaviour.Properties luz(int nivel) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion()
                .lightLevel(s -> s.getValue(LucesRave.ENCENDIDA) ? nivel : 0);
    }

    public static final RegistryObject<Block> BOLA = BLOCKS.register("bola_discoteca", () -> new BolaBlock(luz(8)));
    public static final RegistryObject<Item> BOLA_ITEM = ITEMS.register("bola_discoteca", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(BOLA.get(), "bola_discoteca"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<BolaBlockEntity>> BOLA_BE = BLOCK_ENTITIES.register("bola_discoteca",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(BolaBlockEntity::new, BOLA.get()).build(null));
    public static final RegistryObject<Block> FOCO = BLOCKS.register("foco_movil", () -> new FocoBlock(luz(10)));
    public static final RegistryObject<Item> FOCO_ITEM = ITEMS.register("foco_movil", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(FOCO.get(), "foco_movil"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<FocoBlockEntity>> FOCO_BE = BLOCK_ENTITIES.register("foco_movil",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(FocoBlockEntity::new, FOCO.get()).build(null));
    public static final RegistryObject<Block> ESTROBO = BLOCKS.register("estroboscopio", () -> new EstroboBlock(luz(6)));
    public static final RegistryObject<Item> ESTROBO_ITEM = ITEMS.register("estroboscopio", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(ESTROBO.get(), "estroboscopio"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<EstroboBlockEntity>> ESTROBO_BE = BLOCK_ENTITIES.register("estroboscopio",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(EstroboBlockEntity::new, ESTROBO.get()).build(null));

    // ---- hito 3: mesa de DJ y altavoces
    public static final RegistryObject<Block> MESA = BLOCKS.register("mesa_dj", () -> new MesaDjBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 5)));
    public static final RegistryObject<Item> MESA_ITEM = ITEMS.register("mesa_dj", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(MESA.get(), "mesa_dj"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<MesaDjBlockEntity>> MESA_BE = BLOCK_ENTITIES.register("mesa_dj",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(MesaDjBlockEntity::new, MESA.get()).build(null));
    public static final RegistryObject<Block> ALTAVOZ = BLOCKS.register("altavoz_concierto", () -> new AltavozBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 6.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> ALTAVOZ_ITEM = ITEMS.register("altavoz_concierto", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(ALTAVOZ.get(), "altavoz_concierto"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<AltavozBlockEntity>> ALTAVOZ_BE = BLOCK_ENTITIES.register("altavoz_concierto",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(AltavozBlockEntity::new, ALTAVOZ.get()).build(null));

    // ---- hito 4: humo, chispas y confeti
    public static final RegistryObject<net.minecraft.core.particles.ParticleType<HumoOpciones>> HUMO_PARTICULA = PARTICLES.register("humo_rave",
            () -> new net.minecraft.core.particles.ParticleType<HumoOpciones>(false, HumoOpciones.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<HumoOpciones> codec() {
                    return HumoOpciones.CODEC;
                }
            });
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> CHISPA_PARTICULA = PARTICLES.register("chispa_fria",
            () -> new net.minecraft.core.particles.SimpleParticleType(false));
    public static final RegistryObject<net.minecraft.core.particles.ParticleType<ConfetiOpciones>> CONFETI_PARTICULA = PARTICLES.register("confeti_rave",
            () -> new net.minecraft.core.particles.ParticleType<ConfetiOpciones>(false, ConfetiOpciones.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<ConfetiOpciones> codec() {
                    return ConfetiOpciones.CODEC;
                }
            });
    public static final RegistryObject<Block> HUMO = BLOCKS.register("maquina_humo", () -> new HumoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion(), false));
    public static final RegistryObject<Item> HUMO_ITEM = ITEMS.register("maquina_humo", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(HUMO.get(), "maquina_humo"));
    public static final RegistryObject<Block> CHISPAS = BLOCKS.register("chispas_frias", () -> new HumoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(LucesRave.ENCENDIDA) ? 9 : 0), true));
    public static final RegistryObject<Item> CHISPAS_ITEM = ITEMS.register("chispas_frias", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(CHISPAS.get(), "chispas_frias"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<EfectoBlockEntity>> EFECTO_BE = BLOCK_ENTITIES.register("efecto_rave",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(EfectoBlockEntity::new, HUMO.get(), CHISPAS.get()).build(null));
    public static final RegistryObject<Block> CONFETI = BLOCKS.register("canon_confeti", () -> new ConfetiBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(1.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> CONFETI_ITEM = ITEMS.register("canon_confeti", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(CONFETI.get(), "canon_confeti"));

    // ---- hito 5: escenario
    public static final RegistryObject<Block> TRUSS = BLOCKS.register("andamio_truss", () -> new TrussBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(2.0F, 6.0F).sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> s.getValue(TrussBlock.LUCES) ? 7 : 0)));
    public static final RegistryObject<Item> TRUSS_ITEM = ITEMS.register("andamio_truss", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(TRUSS.get(), "andamio_truss"));
    public static final RegistryObject<Block> TARIMA = BLOCKS.register("tarima_escenario", () -> new net.minecraft.world.level.block.SlabBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> TARIMA_ITEM = ITEMS.register("tarima_escenario", () -> new net.minecraft.world.item.BlockItem(TARIMA.get(), new Item.Properties()));
    public static final RegistryObject<Block> SUELO_LED = BLOCKS.register("suelo_led", () -> new SueloLedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 6.0F).sound(SoundType.GLASS).lightLevel(s -> 6)));
    public static final RegistryObject<Item> SUELO_LED_ITEM = ITEMS.register("suelo_led", () -> new com.miscelanea.camaras.CamarasRegistro.ConAyuda(SUELO_LED.get(), "suelo_led"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SueloLedBlockEntity>> SUELO_LED_BE = BLOCK_ENTITIES.register("suelo_led",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SueloLedBlockEntity::new, SUELO_LED.get()).build(null));

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        PARTICLES.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(RaveRed::registrar));
    }
}
