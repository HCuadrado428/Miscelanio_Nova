package com.miscelanea.tortura.aparatos;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
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
 * Registro de la rueda de Catalina (aparte de {@link AparatosRegistro} para no pisar a otras ramas; lo llama
 * {@link com.miscelanea.bromas.BromasRegistro#registrar}). Solo de creativo.
 */
public final class RuedaRegistro {
    private RuedaRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);

    /** Tipo de daño (data/miscelanea/damage_type/rueda_catalina.json). */
    public static final ResourceKey<DamageType> DANO_RUEDA = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(Miscelanea.MOD_ID, "rueda_catalina"));

    public static final RegistryObject<Block> RUEDA = BLOCKS.register("rueda_catalina", () -> new RuedaBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RuedaBlockEntity>> RUEDA_BE = BLOCK_ENTITIES.register("rueda_catalina",
            () -> BlockEntityType.Builder.of(RuedaBlockEntity::new, RUEDA.get()).build(null));

    public static final RegistryObject<Item> RUEDA_ITEM = ITEMS.register("rueda_catalina",
            () -> new AparatoItem(RUEDA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
