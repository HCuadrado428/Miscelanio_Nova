package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Bloques del mod. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);

    /** Para la pestaña de creativo, en el orden de registro. */
    public static final List<RegistryObject<Item>> ITEMS_BLOQUE = new ArrayList<>();

    /** Bloque "sin textura" que deja el aliento del núcleo; se deshace solo (sin ítem). */
    public static final RegistryObject<Block> BLOQUE_CORRUPTO = BLOCKS.register("bloque_corrupto",
            () -> new com.miscelanea.nucleo.BloqueCorrupto(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(0.3F)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 6).noLootTable()));

    /** Vitrina del museo de bichos y peces. */
    public static final RegistryObject<Block> VITRINA = BLOCKS.register("vitrina",
            () -> new com.miscelanea.bichos.VitrinaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F)
                    .sound(SoundType.GLASS).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.bichos.VitrinaBlockEntity>> VITRINA_BE = BLOCK_ENTITIES.register("vitrina",
            () -> BlockEntityType.Builder.of(com.miscelanea.bichos.VitrinaBlockEntity::new, VITRINA.get()).build(null));

    // ---- Muebles
    public static final RegistryObject<Block> SILLA = BLOCKS.register("silla",
            () -> new com.miscelanea.muebles.SillaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.2F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> SOFA = BLOCKS.register("sofa",
            () -> new com.miscelanea.muebles.SofaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(1.0F).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> MESA = BLOCKS.register("mesa",
            () -> new com.miscelanea.muebles.MesaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> LAMPARA = BLOCKS.register("lampara",
            () -> new com.miscelanea.muebles.LamparaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.LANTERN)
                    .noOcclusion().lightLevel(st -> st.getValue(com.miscelanea.muebles.LamparaBlock.LIT) ? 15 : 0)));

    // ---- Recreativa
    public static final RegistryObject<Block> RECREATIVA = BLOCKS.register("recreativa",
            () -> new com.miscelanea.arcade.RecreativaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 7)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.arcade.RecreativaBlockEntity>> RECREATIVA_BE = BLOCK_ENTITIES.register("recreativa",
            () -> BlockEntityType.Builder.of(com.miscelanea.arcade.RecreativaBlockEntity::new, RECREATIVA.get()).build(null));

    private ModBlocks() {}
}
