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

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
    }
}
