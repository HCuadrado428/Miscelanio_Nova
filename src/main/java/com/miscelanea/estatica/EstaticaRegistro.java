package com.miscelanea.estatica;

import com.miscelanea.Miscelanea;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * «El hombre de estática»: la poción de estática (solo de creativo), el efecto «Estática» con sus poderes, las partículas
 * que gotea y los sonidos. Registro aparte, como GlitchRegistro: basta con {@link #registrar(IEventBus)} desde el
 * constructor del mod. Lo del cliente vive en {@code com.miscelanea.client.estatica}.
 */
public final class EstaticaRegistro {
    private EstaticaRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Miscelanea.MOD_ID);

    /** Gris de la estática (color del efecto). */
    public static final int COLOR = 0xB8B8C0;

    public static final RegistryObject<Item> POCION = ITEMS.register("pocion_estatica",
            () -> new PocionEstaticaItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<MobEffect> EFECTO = EFFECTS.register("estatica", EstaticaEfecto::new);

    /** Gotas de estática que caen de las partes del cuerpo (y de los charcos). */
    public static final RegistryObject<SimpleParticleType> GOTA = PARTICLES.register("estatica_gota", () -> new SimpleParticleType(false));
    /** Píxeles de nieve que saltan de sitio (estelas, chispazos, estallidos). */
    public static final RegistryObject<SimpleParticleType> CHISPA = PARTICLES.register("estatica_chispa", () -> new SimpleParticleType(false));

    public static final RegistryObject<SoundEvent> BUCLE = sonido("estatica_bucle");
    public static final RegistryObject<SoundEvent> PROPIA = sonido("estatica_propia");
    public static final RegistryObject<SoundEvent> CRUJIDO = sonido("estatica_crujido");
    public static final RegistryObject<SoundEvent> ESTALLIDO = sonido("estatica_estallido");
    public static final RegistryObject<SoundEvent> PARPADEO = sonido("estatica_parpadeo");
    public static final RegistryObject<SoundEvent> CHISPA_SONIDO = sonido("estatica_chispa");
    public static final RegistryObject<SoundEvent> PANTALLA = sonido("estatica_pantalla");
    public static final RegistryObject<SoundEvent> APAGON = sonido("estatica_apagon");
    public static final RegistryObject<SoundEvent> TENTACULO = sonido("estatica_tentaculo");
    public static final RegistryObject<SoundEvent> GOLPE = sonido("estatica_golpe");
    public static final RegistryObject<SoundEvent> IMITACION = sonido("estatica_imitacion");

    /** Armas que no puede usar (además de espadas, hachas, tridentes, arcos, ballestas y escudos). */
    public static final TagKey<Item> ARMAS = TagKey.create(Registries.ITEM, new ResourceLocation(Miscelanea.MOD_ID, "armas"));
    /** Pantallas por las que se puede meter (además de las recreativas, que se reconocen por su entidad de bloque). */
    public static final TagKey<Block> PANTALLAS = TagKey.create(Registries.BLOCK, new ResourceLocation(Miscelanea.MOD_ID, "pantallas"));
    /** Luces que apaga al pasar (con la propiedad "lit"): se vuelven a encender solas cuando se va. */
    public static final TagKey<Block> APAGABLES = TagKey.create(Registries.BLOCK, new ResourceLocation(Miscelanea.MOD_ID, "apagables_estatica"));
    /** Luces que solo parpadean (humo y chisporroteo; nunca se tocan): antorchas, faroles... */
    public static final TagKey<Block> PARPADEAN = TagKey.create(Registries.BLOCK, new ResourceLocation(Miscelanea.MOD_ID, "parpadean_estatica"));

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
    }
}
