package com.miscelanea.camaras;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

/** Registro del sistema de cámaras: cámara de vigilancia, televisión y su canal de red. */
public final class CamarasRegistro {
    private CamarasRegistro() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Miscelanea.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }

    /** Objeto de bloque con tres líneas de ayuda ({@code block.miscelanea.<id>.ayuda}, {@code .ayuda2}, {@code .ayuda3}). */
    public static final class ConAyuda extends BlockItem {
        private final String id;

        public ConAyuda(Block b, String id) {
            super(b, new Item.Properties().rarity(Rarity.UNCOMMON));
            this.id = id;
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
            for (String sufijo : new String[]{".ayuda", ".ayuda2", ".ayuda3"})
                t.add(Component.translatable("block.miscelanea." + id + sufijo).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    public static final RegistryObject<Block> VIGILANCIA = BLOCKS.register("camara_vigilancia", () -> new VigilanciaBlock(metal()));
    public static final RegistryObject<Item> VIGILANCIA_ITEM = ITEMS.register("camara_vigilancia", () -> new ConAyuda(VIGILANCIA.get(), "camara_vigilancia"));
    public static final RegistryObject<BlockEntityType<VigilanciaBlockEntity>> VIGILANCIA_BE = BLOCK_ENTITIES.register("camara_vigilancia",
            () -> BlockEntityType.Builder.of(VigilanciaBlockEntity::new, VIGILANCIA.get()).build(null));

    public static final RegistryObject<Block> TELE = BLOCKS.register("television", () -> new TeleBlock(metal().lightLevel(s -> 6)));
    public static final RegistryObject<Item> TELE_ITEM = ITEMS.register("television", () -> new ConAyuda(TELE.get(), "television"));
    public static final RegistryObject<BlockEntityType<TeleBlockEntity>> TELE_BE = BLOCK_ENTITIES.register("television",
            () -> BlockEntityType.Builder.of(TeleBlockEntity::new, TELE.get()).build(null));

    public static final RegistryObject<Block> MONITOR = BLOCKS.register("monitor_laboratorio", () -> new MonitorBlock(metal().lightLevel(s -> 7)));
    public static final RegistryObject<Item> MONITOR_ITEM = ITEMS.register("monitor_laboratorio", () -> new ConAyuda(MONITOR.get(), "monitor_laboratorio"));
    public static final RegistryObject<BlockEntityType<MonitorBlockEntity>> MONITOR_BE = BLOCK_ENTITIES.register("monitor_laboratorio",
            () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, MONITOR.get()).build(null));

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(CamarasRed::registrar));
    }
}
