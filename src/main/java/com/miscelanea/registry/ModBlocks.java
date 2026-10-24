package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Bloques de la cárcel. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);

    /** Para la pestaña de creativo, en el orden de registro. */
    public static final List<RegistryObject<Item>> ITEMS_BLOQUE = new ArrayList<>();

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F)
                .sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final RegistryObject<Block> REJA = registrar("reja_simbolo",
            () -> new RejaBlock(metal().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> MONITOR = registrar("monitor_simbolo",
            () -> new MonitorBlock(metal().strength(2.0F).noOcclusion().lightLevel(s -> 7)));
    public static final RegistryObject<Block> CADAVER = registrar("cadaver",
            () -> new CadaverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.0F).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> TERMINAL = registrar("terminal_ejecucion",
            () -> new TerminalBlock(metal().noOcclusion().lightLevel(s -> 4)));
    public static final RegistryObject<Block> PALANCA = registrar("palanca_descarga",
            () -> new PalancaBlock(metal().strength(2.0F).noOcclusion().noCollission()));
    public static final RegistryObject<Block> TAQUILLA = registrar("taquilla",
            () -> new TaquillaBlock(metal().strength(3.0F).noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> CRISTALES = registrar("cristales_rotos",
            () -> new CristalesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.3F)
                    .sound(SoundType.GLASS).noOcclusion().noCollission()));
    public static final RegistryObject<Block> LATAS = registrar("alarma_latas",
            () -> new LatasBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.2F)
                    .sound(SoundType.CHAIN).noOcclusion().noCollission()));
    public static final RegistryObject<Block> REJILLA_ELECTRICA = registrar("rejilla_electrica",
            () -> new RejillaElectricaBlock(metal().noOcclusion()
                    .lightLevel(s -> s.getValue(RejillaElectricaBlock.ACTIVA) ? 6 : 0)));
    public static final RegistryObject<Block> REJILLA_GAS = registrar("rejilla_gas",
            () -> new GasBlock(metal().noOcclusion()));

    public static final RegistryObject<BlockEntityType<CadaverBlockEntity>> CADAVER_BE = BLOCK_ENTITIES.register("cadaver",
            () -> BlockEntityType.Builder.of(CadaverBlockEntity::new, CADAVER.get()).build(null));

    private static RegistryObject<Block> registrar(String nombre, Supplier<Block> bloque) {
        RegistryObject<Block> b = BLOCKS.register(nombre, bloque);
        ITEMS_BLOQUE.add(ModItems.ITEMS.register(nombre, () -> new BlockItem(b.get(), new Item.Properties())));
        return b;
    }

    private ModBlocks() {}
}
