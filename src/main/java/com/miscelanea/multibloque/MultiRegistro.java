package com.miscelanea.multibloque;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
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

/** Registro del relleno de los bloques de varios huecos (no tiene objeto: se pone y se quita solo). */
public final class MultiRegistro {
    private MultiRegistro() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Miscelanea.MOD_ID);

    public static final RegistryObject<Block> PARTE = BLOCKS.register("parte_multibloque", () -> new ParteBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion().noLootTable().dynamicShape().pushReaction(PushReaction.BLOCK)
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)));
    public static final RegistryObject<BlockEntityType<ParteBlockEntity>> PARTE_BE = BLOCK_ENTITIES.register("parte_multibloque",
            () -> BlockEntityType.Builder.of(ParteBlockEntity::new, PARTE.get()).build(null));

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
