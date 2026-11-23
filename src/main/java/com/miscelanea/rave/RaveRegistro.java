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

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
    }
}
