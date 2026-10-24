package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.nucleo.BloqueCorrupto;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);

    /** Bloque "sin textura" que deja el aliento del núcleo; se deshace solo. */
    public static final RegistryObject<Block> BLOQUE_CORRUPTO = BLOCKS.register("bloque_corrupto",
            () -> new BloqueCorrupto(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(0.3F)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 6).noLootTable()));

    private ModBlocks() {}
}
