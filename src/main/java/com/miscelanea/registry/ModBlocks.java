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

    private ModBlocks() {}
}
