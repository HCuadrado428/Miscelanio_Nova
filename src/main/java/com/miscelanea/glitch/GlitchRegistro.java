package com.miscelanea.glitch;

import com.miscelanea.Miscelanea;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
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

import javax.annotation.Nullable;

/**
 * «El glitch»: la muestra de glitch (un frasco con un trozo de error vivo que, si se suelta o se rompe, corrompe y borra
 * una esfera de bloques) y el frasco de muestras para recogerla. Las dos cosas son solo de creativo (para el rol de
 * Drista). Registro aparte, como DreamRegistro: basta con {@link #registrar(IEventBus)} desde el constructor del mod.
 */
public final class GlitchRegistro {
    private GlitchRegistro() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Miscelanea.MOD_ID);

    /** Morado del glitch (efecto, salpicadura del frasco). */
    public static final int COLOR = 0xB000FF;

    // ---- bloque temporal: lo que queda de un bloque mientras el glitch se lo come (guarda el original)
    public static final RegistryObject<Block> GLITCH_TEMPORAL = BLOCKS.register("glitch_temporal",
            () -> new GlitchTemporalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA)
                    .strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.AMETHYST)
                    .emissiveRendering((s, g, p) -> true).pushReaction(PushReaction.BLOCK).isValidSpawn((s, g, p, t) -> false)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<GlitchTemporalBlockEntity>> GLITCH_TEMPORAL_BE = BLOCK_ENTITIES.register("glitch_temporal",
            () -> BlockEntityType.Builder.of(GlitchTemporalBlockEntity::new, GLITCH_TEMPORAL.get()).build(null));

    // ---- objetos (solo de creativo)
    public static final RegistryObject<Item> MUESTRA = ITEMS.register("muestra_glitch",
            () -> new MuestraGlitchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> FRASCO = ITEMS.register("frasco_muestras",
            () -> new FrascoMuestrasItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    /** Solo para dibujar la muestra en vuelo (la botella de poción arrojadiza); no va en el creativo. */
    public static final RegistryObject<Item> MUESTRA_LANZADA_ITEM = ITEMS.register("muestra_glitch_lanzada",
            () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public boolean isFoil(ItemStack st) {
                    return true;
                }
            });

    // ---- entidad: la muestra lanzada
    public static final RegistryObject<EntityType<MuestraLanzadaEntity>> MUESTRA_LANZADA = ENTITIES.register("muestra_glitch_lanzada",
            () -> EntityType.Builder.<MuestraLanzadaEntity>of(MuestraLanzadaEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(2).build("muestra_glitch_lanzada"));

    // ---- efecto: «Glitch» (náuseas de píxeles; en el cliente, la pantalla se rompe un poco)
    public static final RegistryObject<MobEffect> EFECTO = EFFECTS.register("glitch", GlitchEfecto::new);

    // ---- partícula: píxeles sueltos que saltan de sitio
    public static final RegistryObject<SimpleParticleType> PARTICULA = PARTICLES.register("glitch", () -> new SimpleParticleType(false));

    // ---- sonidos (procedurales)
    public static final RegistryObject<SoundEvent> ESTATICA = sonido("glitch_estatica");
    public static final RegistryObject<SoundEvent> SISEO = sonido("glitch_siseo");
    public static final RegistryObject<SoundEvent> CRUJIDO = sonido("glitch_crujido");
    public static final RegistryObject<SoundEvent> ESTALLIDO = sonido("glitch_estallido");
    public static final RegistryObject<SoundEvent> LLENAR = sonido("frasco_llenar");

    // ---- tipo de daño (data/miscelanea/damage_type/glitch.json)
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "glitch"));

    public static DamageSource dano(Level level, @Nullable Entity causante) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DANO), causante);
    }

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
    }
}
