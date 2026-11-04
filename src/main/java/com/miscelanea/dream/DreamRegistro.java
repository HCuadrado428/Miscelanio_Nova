package com.miscelanea.dream;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
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
 * Lo de "Dream" (la criatura artificial del servidor): el caldero de mezclas y sus frascos (para todos, en supervivencia),
 * la esencia de Dream con el efecto «Mordisco venenoso» y el anillo de medianoche (los dos, solo de creativo).
 * Registro aparte, como PocionesRegistro: basta con {@link #registrar(IEventBus)} desde el constructor del mod.
 */
public final class DreamRegistro {
    private DreamRegistro() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);

    /** Verde veneno oscuro del mordisco (efecto, esencia, partículas y viñeta). */
    public static final int COLOR_MORDISCO = 0x2E5A1C;
    /** Rojo sangre oscuro del desangrado. */
    public static final int COLOR_DESANGRADO = 0x8A0F12;

    // ---- efecto
    public static final RegistryObject<MobEffect> MORDISCO = EFFECTS.register("mordisco_venenoso", MordiscoEfecto::new);
    /** Lo que deja el veneno del mordisco (como la baba de un dragón de Komodo): la herida no deja de sangrar. */
    public static final RegistryObject<MobEffect> DESANGRADO = EFFECTS.register("desangrado", DesangradoEfecto::new);

    // ---- caldero de mezclas
    public static final RegistryObject<Block> CALDERO = BLOCKS.register("caldero_mezclas",
            () -> new CalderoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F)
                    .sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> 6)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CalderoBlockEntity>> CALDERO_BE = BLOCK_ENTITIES.register("caldero_mezclas",
            () -> BlockEntityType.Builder.of(CalderoBlockEntity::new, CALDERO.get()).build(null));
    public static final RegistryObject<Item> CALDERO_ITEM = ITEMS.register("caldero_mezclas",
            () -> new BlockItem(CALDERO.get(), new Item.Properties()));

    // ---- frascos
    public static final RegistryObject<Item> FRASCO_MEZCLA = ITEMS.register("frasco_mezcla",
            () -> new FrascoMezclaItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FRASCO_MEZCLA_ARROJADIZO = ITEMS.register("frasco_mezcla_arrojadizo",
            () -> new FrascoArrojadizoItem(new Item.Properties().stacksTo(1)));

    // ---- veneno de Dream: solo se consigue extrayéndolo con el mordisco activo (agachado + botella vacía)
    public static final RegistryObject<Item> VENENO = ITEMS.register("veneno_dream",
            () -> new VenenoDreamItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ---- solo de creativo
    public static final RegistryObject<Item> ESENCIA = ITEMS.register("esencia_dream",
            () -> new EsenciaDreamItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ANILLO = ITEMS.register("anillo_medianoche",
            () -> new AnilloMedianocheItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // ---- sonidos (procedurales, gen_dream.py)
    public static final RegistryObject<SoundEvent> MORDISCO_SND = sonido("dream_mordisco");
    public static final RegistryObject<SoundEvent> AGARRE_SND = sonido("dream_agarre");
    public static final RegistryObject<SoundEvent> CALDERO_VERTER = sonido("caldero_verter");
    public static final RegistryObject<SoundEvent> CALDERO_BURBUJAS = sonido("caldero_burbujas");
    public static final RegistryObject<SoundEvent> ANILLO_CARGA = sonido("anillo_carga");
    public static final RegistryObject<SoundEvent> ANILLO_SALTO = sonido("anillo_salto");

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
